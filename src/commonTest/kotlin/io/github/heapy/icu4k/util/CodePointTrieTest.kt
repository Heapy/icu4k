package io.github.heapy.icu4k.util

import kotlin.test.*

class CodePointTrieTest {
    // Small valid Tri3 image: two distinct ASCII blocks and one shared null block.
    private fun fixture(type: CodePointTrie.Type, width: CodePointTrie.ValueWidth, order: BinaryBuffer.ByteOrder): ByteArray {
        val indexLength = if (type == CodePointTrie.Type.FAST) 1024 else 64
        val dataLength = 194
        val widthBytes = when (width) { CodePointTrie.ValueWidth.BITS_8 -> 1; CodePointTrie.ValueWidth.BITS_16 -> 2; CodePointTrie.ValueWidth.BITS_32 -> 4 }
        val buffer = BinaryBuffer(ByteArray(16 + 2 * indexLength + dataLength * widthBytes), order)
        buffer.writeInt(0x54726933)
        buffer.writeShort((type.ordinal shl 6) or width.ordinal)
        buffer.writeShort(indexLength)
        buffer.writeShort(dataLength)
        buffer.writeShort(0x7fff)
        buffer.writeShort(128)
        buffer.writeShort(if (type == CodePointTrie.Type.FAST) 128 else 8)
        repeat(indexLength) { buffer.writeShort(if (it == 0) 0 else if (it == 1) 64 else 128) }
        repeat(dataLength) {
            val value = when (it) { 65 -> 9; 193 -> 255; else -> 7 }
            when (width) {
                CodePointTrie.ValueWidth.BITS_8 -> buffer.writeByte(value)
                CodePointTrie.ValueWidth.BITS_16 -> buffer.writeShort(value)
                CodePointTrie.ValueWidth.BITS_32 -> buffer.writeInt(value)
            }
        }
        return buffer.bytes
    }

    @Test fun decodesAllTypesWidthsAndByteOrders() {
        for (type in CodePointTrie.Type.entries) for (width in CodePointTrie.ValueWidth.entries) for (order in BinaryBuffer.ByteOrder.entries) {
            val encoded = fixture(type, width, order)
            val buffer = BinaryBuffer(encoded, if (order == BinaryBuffer.ByteOrder.BIG_ENDIAN) BinaryBuffer.ByteOrder.LITTLE_ENDIAN else BinaryBuffer.ByteOrder.BIG_ENDIAN)
            val originalOrder = buffer.order
            val trie = CodePointTrie.fromBinary(type, width, buffer)
            assertEquals(encoded.size, buffer.position)
            assertEquals(originalOrder, buffer.order)
            assertEquals(type, trie.getType())
            assertEquals(width, trie.getValueWidth())
            assertEquals(9, trie[65])
            assertEquals(9, trie.asciiGet(65))
            for (cp in listOf(0, 64, 66, 127, 128, 0xd800, 0xffff, 0x10000, 0x10ffff)) assertEquals(7, trie[cp])
            assertEquals(255, trie[-1])
            assertEquals(255, trie[0x110000])
            assertContentEquals(fixture(type, width, BinaryBuffer.ByteOrder.BIG_ENDIAN), trie.toBinary())
            assertContentEquals(encoded, trie.toBinary(order))
            val range = CodePointMap.Range()
            assertTrue(trie.getRange(0, null, range))
            assertEquals(64, range.getEnd())
            assertTrue(trie.getRange(65, null, range))
            assertEquals(65, range.getEnd())
            assertTrue(trie.getRange(66, null, range))
            assertEquals(0x10ffff, range.getEnd())
            assertTrue(trie.getRange(0, CodePointMap.ValueFilter { it and 1 }, range))
            assertEquals(0x10ffff, range.getEnd())
            val text = charArrayOf(0xd800.toChar(), 'A', 0xdc00.toChar(), 0xd800.toChar(), 0xdc00.toChar()).concatToString()
            val iterator = trie.stringIterator(text, 0)
            val expected = listOf(255, 9, 255, 7)
            for (value in expected) { assertTrue(iterator.next()); assertEquals(value, iterator.getValue()) }
            assertFalse(iterator.next())
            for (value in expected.reversed()) { assertTrue(iterator.previous()); assertEquals(value, iterator.getValue()) }
            assertFalse(iterator.previous())
        }
    }

    @Test fun validatesHeadersAndRestoresByteOrderAfterErrors() {
        val valid = fixture(CodePointTrie.Type.FAST, CodePointTrie.ValueWidth.BITS_16, BinaryBuffer.ByteOrder.LITTLE_ENDIAN)
        for (length in listOf(0, 1, 15, 16, valid.size - 1)) {
            val truncated = BinaryBuffer(valid.copyOf(length))
            assertFailsWith<ICUUncheckedIOException> { CodePointTrie.fromBinary(null, null, truncated) }
            assertEquals(BinaryBuffer.ByteOrder.BIG_ENDIAN, truncated.order)
        }
        assertFailsWith<ICUUncheckedIOException> { CodePointTrie.fromBinary(CodePointTrie.Type.SMALL, null, valid) }
        assertFailsWith<ICUUncheckedIOException> { CodePointTrie.fromBinary(null, CodePointTrie.ValueWidth.BITS_32, valid) }
        for (mask in listOf(0x38, 0x80, 0x07)) {
            val bad = valid.copyOf()
            bad[4] = (bad[4].toInt() or mask).toByte()
            assertFailsWith<ICUUncheckedIOException> { CodePointTrie.fromBinary(null, null, bad) }
        }
        val original = CodePointTrie.fromBinary(null, null, valid)
        valid.fill(0)
        assertEquals(9, original[65])
    }
}
