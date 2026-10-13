package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import kotlin.test.*

class Trie2Test {
    private fun propertiesTrie(): Trie2_16 {
        val bytes = ICUBinary.getRequiredData("uprops.icu")
        ICUBinary.readHeader(bytes, 0x5550726f, null)
        bytes.skip(16 * 4)
        return Trie2_16.createFromSerialized(bytes)
    }

    @Test fun bothWidthsPreserveAlternateLeadValuesAndSignedData() {
        fun fixture(bits: Int): Trie2 {
            val indexLength = 2112
            val dataLength = 260
            val dataOffset = if (bits == 16) indexLength else 0
            val buffer = BinaryBuffer(ByteArray(16 + indexLength * 2 + dataLength * (bits / 8)))
            buffer.writeInt(0x54726932)
            buffer.writeShort(if (bits == 16) 0 else 1)
            buffer.writeShort(indexLength)
            buffer.writeShort(dataLength / 4)
            buffer.writeShort(0xffff)
            buffer.writeShort(dataOffset + 192)
            buffer.writeShort(0x10000 shr 11)
            repeat(indexLength) {
                val block = when {
                    it < 4 -> it * 32
                    it in (0xd800 shr 5)..(0xdbff shr 5) -> 224
                    else -> 192
                }
                buffer.writeShort((dataOffset + block) shr 2)
            }
            repeat(dataLength) {
                val value = when {
                    it == 65 -> 13
                    it in 128..191 -> if (bits == 16) 65535 else Int.MIN_VALUE
                    it in 224..255 -> 17
                    else -> 7
                }
                if (bits == 16) buffer.writeShort(value) else buffer.writeInt(value)
            }
            return Trie2.createFromSerialized(buffer.bytes)
        }
        for (bits in listOf(16, 32)) {
            val trie = fixture(bits)
            assertEquals(13, trie['A'.code])
            assertEquals(7, trie[0xd800])
            assertEquals(17, trie.getFromU16SingleLead(0xd800.toChar()))
            assertEquals(7, trie[0x10000])
            assertEquals(if (bits == 16) 65535 else Int.MIN_VALUE, trie[-1])
            assertEquals(trie, Trie2.createFromSerialized(trie.serialize()))
            val ranges = trie.iteratorForLeadSurrogate(0xdbff.toChar())
            val range = ranges.next()
            assertEquals(0x10fc00, range.startCodePoint)
            assertEquals(0x10ffff, range.endCodePoint)
            assertFalse(ranges.hasNext())
        }
    }

    @Test fun looksUpActualUnicodeCategories() {
        val trie = propertiesTrie()
        assertEquals(1, trie['A'.code] and 31)
        assertEquals(2, trie['a'.code] and 31)
        assertEquals(9, trie['0'.code] and 31)
        assertEquals(6, trie[0x0301] and 31)
        assertEquals(18, trie[0xd800] and 31)
        assertEquals(27, trie[0x1f600] and 31)
        assertEquals(trie[-1], trie[0x110000])
        val serialized = trie.serialize()
        assertEquals(trie.getSerializedLength(), serialized.size)
        val restored = Trie2.createFromSerialized(serialized)
        assertEquals(trie, restored)
        assertEquals(trie.hashCode(), restored.hashCode())
        val little = trie.serialize(BinaryBuffer.ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, Trie2.getVersion(BinaryBuffer(little), true))
        assertEquals(0, Trie2.getVersion(BinaryBuffer(little), false))
        assertEquals(trie, Trie2.createFromSerialized(little))
    }

    @Test fun rangeAndStringIteratorsReuseResults() {
        val trie = propertiesTrie()
        val ranges = trie.iterator(Trie2.ValueMapper { it and 31 })
        val first = ranges.next()
        assertEquals(0, first.startCodePoint)
        val second = ranges.next()
        assertSame(first, second)
        val leads = trie.iteratorForLeadSurrogate(0xd800.toChar())
        val lead = leads.next()
        assertEquals(0x10000, lead.startCodePoint)
        assertTrue(lead.endCodePoint <= 0x103ff)
        assertFalse(lead.leadSurrogate)
        val text = charArrayOf('A', 0xd83d.toChar(), 0xde00.toChar(), 0xd800.toChar()).concatToString()
        val strings = trie.charSequenceIterator(text, 0)
        val result = strings.next()
        assertEquals(0, result.index)
        assertEquals('A'.code, result.codePoint)
        assertSame(result, strings.next())
        assertEquals(1, result.index)
        assertEquals(0x1f600, result.codePoint)
        strings.next()
        assertFalse(strings.hasNext())
        assertEquals(0xd800, strings.previous().codePoint)
        assertEquals(0x1f600, strings.previous().codePoint)
        strings.set(2)
        assertEquals(0xde00, strings.next().codePoint)
    }

    @Test fun restoresOrderAndProtectsDecodedData() {
        val trie = propertiesTrie()
        val serialized = trie.serialize(BinaryBuffer.ByteOrder.LITTLE_ENDIAN)
        val buffer = BinaryBuffer(serialized)
        assertEquals(2, Trie2.getVersion(buffer, true))
        assertEquals(0, buffer.position)
        val restored = Trie2.createFromSerialized(buffer)
        assertEquals(BinaryBuffer.ByteOrder.BIG_ENDIAN, buffer.order)
        assertEquals(serialized.size, buffer.position)
        serialized.fill(0)
        assertEquals(trie['A'.code], restored['A'.code])
        assertFailsWith<ICUUncheckedIOException> { Trie2.createFromSerialized(ByteArray(15)) }
        assertFailsWith<IllegalArgumentException> { Trie2.createFromSerialized(ByteArray(16)) }
        assertFailsWith<IllegalArgumentException> { trie.iteratorForLeadSurrogate('A') }
    }
}
