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
    @Test fun absoluteSlicesShareStorageAndPreserveReadOnlyStateAndBounds() {
        val storage = byteArrayOf(10, 11, 12, 13, 14, 15, 16)
        val source = BinaryBuffer(storage, BinaryBuffer.ByteOrder.LITTLE_ENDIAN, 1, 5)
        source.limit = 4; source.position = 3
        for (buffer in listOf(source, source.asReadOnlyBuffer())) {
            val slice = buffer.slice(1, 2)
            assertEquals(buffer.isReadOnly, slice.isReadOnly)
            assertEquals(BinaryBuffer.ByteOrder.LITTLE_ENDIAN, slice.order)
            assertEquals(0, slice.position); assertEquals(2, slice.capacity); assertEquals(2, slice.limit)
            assertEquals(3, buffer.position); assertEquals(4, buffer.limit)
            assertEquals(12, slice.getByte(0).toInt())
            storage[2] = 99
            assertEquals(99, slice.getByte(0).toInt())
            storage[2] = 12
            assertEquals(0, buffer.slice(4).remaining)
            assertFailsWith<IndexOutOfBoundsException> { buffer.slice(-1) }
            assertFailsWith<IndexOutOfBoundsException> { buffer.slice(5, 0) }
            assertFailsWith<IndexOutOfBoundsException> { buffer.slice(1, 4) }
            assertFailsWith<IndexOutOfBoundsException> { buffer.slice(0, Int.MAX_VALUE) }
            assertFailsWith<IndexOutOfBoundsException> { buffer.slice(Int.MAX_VALUE, Int.MAX_VALUE) }
            assertFailsWith<IndexOutOfBoundsException> { buffer.slice(0, -1) }
            if (slice.isReadOnly) assertFailsWith<UnsupportedOperationException> { slice.writeByte(0) }
            else { slice.writeByte(21); assertEquals(21, buffer.getByte(1).toInt()); storage[2] = 12 }
        }
    }

}
