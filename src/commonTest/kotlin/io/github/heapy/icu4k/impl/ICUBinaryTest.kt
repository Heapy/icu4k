package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import kotlin.test.*

class ICUBinaryTest {
    @Test fun readsHeadersInBothOrdersAndAuthenticatesVersions() {
        val header = ICUBinary.writeHeader(0x4c61796f, 0x01020304, 0x11000000)
        for (little in listOf(false, true)) {
            val bytes = header.copyOf()
            if (little) {
                bytes[0] = 32; bytes[1] = 0
                bytes[4] = 20; bytes[5] = 0
                bytes[8] = 0
            }
            // A slice must authenticate its own header, not bytes before it.
            val prefixed = ByteArray(7) + bytes + byteArrayOf(1, 2)
            val buffer = BinaryBuffer(prefixed, arrayOffset = 7, capacity = bytes.size)
            val version = ICUBinary.readHeaderAndDataVersion(buffer, 0x4c61796f, ICUBinary.Authenticate {
                it.contentEquals(byteArrayOf(1, 2, 3, 4))
            })
            assertEquals("17.0.0.0", version.toString())
            assertEquals(32, buffer.position)
            assertEquals(if (little) BinaryBuffer.ByteOrder.LITTLE_ENDIAN else BinaryBuffer.ByteOrder.BIG_ENDIAN, buffer.order)
        }
        assertFailsWith<ICUUncheckedIOException> { ICUBinary.readHeader(BinaryBuffer(header), 0, null) }
        assertFailsWith<ICUUncheckedIOException> { ICUBinary.readHeader(BinaryBuffer(header), 0x4c61796f, ICUBinary.Authenticate { false }) }
        val bad = header.copyOf().also { it[2] = 0 }
        assertFailsWith<ICUUncheckedIOException> { ICUBinary.readHeader(BinaryBuffer(bad), 0x4c61796f, null) }
    }

    @Test fun slicesShareStorageButNotCursors() {
        val buffer = BinaryBuffer(ByteArray(8))
        buffer.position = 2
        buffer.limit = 6
        buffer.order = BinaryBuffer.ByteOrder.LITTLE_ENDIAN
        val slice = ICUBinary.sliceWithOrder(buffer)
        assertEquals(4, slice.remaining)
        slice.writeInt(0x12345678)
        assertEquals(2, buffer.position)
        assertEquals(0x12345678, buffer.readInt())
        assertFailsWith<ICUUncheckedIOException> { slice.readByte() }
    }
}
