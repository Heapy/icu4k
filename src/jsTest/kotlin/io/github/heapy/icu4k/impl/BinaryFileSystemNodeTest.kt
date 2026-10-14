package io.github.heapy.icu4k.impl

import kotlin.test.*

class BinaryFileSystemNodeTest {
    @Test fun descriptorViewsObserveWritesAndSurviveUnlink() {
        val fs: dynamic = js("require('node:fs')")
        val os: dynamic = js("require('node:os')")
        val path: dynamic = js("require('node:path')")
        val directory = fs.mkdtempSync(path.join(os.tmpdir(), "icu4k-map-")) as String
        val file = path.join(directory, "data.bin") as String
        try {
            fs.writeFileSync(file, js("new Uint8Array([1, 2, 3, 4])"))
            val host = binaryFileSystem()!!
            val buffer = assertNotNull(host.mapFile(file))
            val slice = buffer.slice(1, 2)
            val fileHandler = assertNotNull(URLHandler.forFile(file))
            val names = mutableListOf<String>()
            assertNotNull(URLHandler.forFile(directory)).guide(URLHandler.URLVisitor(names::add), false, false)
            assertEquals(listOf("/data.bin"), names)
            assertNull(URLHandler.forFile("$directory/missing"))
            assertTrue(slice.isReadOnly)
            assertFailsWith<UnsupportedOperationException> { slice.writeByte(0) }
            val descriptor = fs.openSync(file, "r+")
            try { fs.writeSync(descriptor, js("new Uint8Array([55])"), 0, 1, 1) }
            finally { fs.closeSync(descriptor) }
            assertEquals(55, slice.readUnsignedByte())
            assertEquals(listOf("data.bin"), host.listFiles(directory)!!.map { it.name })
            assertEquals(1, BinaryDataFiles(directory, host).getData("data.bin")!!.readUnsignedByte())
            fs.unlinkSync(file)
            assertEquals(3, slice.readUnsignedByte())
            names.clear()
            fileHandler.guide(URLHandler.URLVisitor(names::add), true, false)
            assertEquals(listOf("data.bin"), names)
            assertNull(host.listFiles(path.join(directory, "missing") as String))
        } finally { fs.rmSync(directory, js("({ recursive: true, force: true })")) }
    }
}
