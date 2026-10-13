package io.github.heapy.icu4k.util

import kotlin.test.*

class BinaryBufferTest {
    @Test fun readsBothByteOrdersAndChecksCapacity() {
        val bytes = byteArrayOf(0x80.toByte(), 0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xab.toByte(), 0xcd.toByte())
        val buffer = BinaryBuffer(bytes)
        assertEquals(0x800123456789abcdUL.toLong(), buffer.readLong())
        assertEquals(8, buffer.position)
        assertFailsWith<ICUUncheckedIOException> { buffer.readInt() }
        assertEquals(8, buffer.position)
        buffer.position = 0
        buffer.order = BinaryBuffer.ByteOrder.LITTLE_ENDIAN
        assertEquals(0xcdab896745230180UL.toLong(), buffer.readLong())
        buffer.position = 0
        assertEquals(0x0180, buffer.readUnsignedShort())
        assertEquals(0x4523, buffer.readUnsignedShort())
        buffer.limit = 3
        assertEquals(3, buffer.position)
        assertFailsWith<IndexOutOfBoundsException> { buffer.position = 4 }
        assertFailsWith<ICUUncheckedIOException> { buffer.readChars(Int.MAX_VALUE) }
    }
}
