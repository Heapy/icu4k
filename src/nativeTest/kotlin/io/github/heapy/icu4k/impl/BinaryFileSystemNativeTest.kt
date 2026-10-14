@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.heapy.icu4k.impl

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.test.*

class BinaryFileSystemNativeTest {
    @Test fun mmapViewsObserveWritesAndSurviveUnlink() {
        val template = "/tmp/icu4k-map-XXXXXX".encodeToByteArray() + byteArrayOf(0)
        val directory = template.usePinned { mkdtemp(it.addressOf(0))!!.toKString() }
        val path = "$directory/data.bin"
        try {
            val output = fopen(path, "wb")!!
            try { byteArrayOf(1, 2, 3, 4).usePinned { assertEquals(4uL, fwrite(it.addressOf(0), 1u, 4u, output)) } }
            finally { fclose(output) }
            val fs = binaryFileSystem()!!
            val buffer = assertNotNull(fs.mapFile(path))
            val slice = buffer.slice(1, 2)
            assertTrue(slice.isReadOnly)
            assertFailsWith<UnsupportedOperationException> { slice.writeByte(0) }
            val file = fopen(path, "r+b")!!
            try { assertEquals(0, fseek(file, 1, SEEK_SET)); assertEquals(55, fputc(55, file)) }
            finally { fclose(file) }
            assertEquals(55, slice.readUnsignedByte())
            assertEquals(listOf("data.bin"), fs.listFiles(directory)!!.map { it.name })
            assertEquals(1, BinaryDataFiles(directory, fs).getData("data.bin")!!.readUnsignedByte())
            assertEquals(0, unlink(path))
            assertEquals(3, slice.readUnsignedByte())
            assertNull(fs.listFiles("$directory/missing"))
        } finally { unlink(path); rmdir(directory) }
    }
}
