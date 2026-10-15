// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.Norm2AllModes
import io.github.heapy.icu4k.impl.Trie2_32
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUException
import kotlin.test.*

class CollationDataTest {
    @Test fun singleElementsIndirectTagsAndFallbackTables() {
        val simple = 0x12340505
        val data = CollationData(null)
        data.trie = constantCollationTrie(simple)
        assertEquals(Collation.ceFromSimpleCE32(simple), data.getSingleCE(65))
        data.trie = constantCollationTrie(Collation.makeCE32FromTagIndexAndLength(5, 1, 1))
        data.ce32s = intArrayOf(simple, Collation.makeCE32FromTagAndIndex(10, 2), 0x234567c1)
        assertEquals(Collation.ceFromCE32(0x234567c1), data.getSingleCE(65))
        val child = CollationData(null).also {
            it.trie = constantCollationTrie(Collation.FALLBACK_CE32)
            it.ce32s = intArrayOf(0, 0)
            it.base = data
        }
        assertEquals(data.getSingleCE(65), child.getSingleCE(65))
        data.trie = constantCollationTrie(0xcb)
        assertEquals(Collation.ceFromSimpleCE32(simple), data.getSingleCE(0))
        data.ces = longArrayOf(0x1234567805000500L)
        data.trie = constantCollationTrie(Collation.makeCE32FromTagIndexAndLength(6, 0, 1))
        assertEquals(data.ces!![0], data.getSingleCE(65))
        data.trie = constantCollationTrie(Collation.UNASSIGNED_CE32)
        assertEquals(Collation.unassignedCEFromCodePoint(0x10ffff), data.getSingleCE(0x10ffff))
        data.trie = constantCollationTrie(0xc4)
        assertEquals("there is not exactly one collation element for U+0041 (CE32 0x000000c4)", assertFailsWith<UnsupportedOperationException> { data.getSingleCE(65) }.message)
        data.trie = constantCollationTrie(0x20c0)
        assertFailsWith<AssertionError> { data.getSingleCE(65) }
        assertEquals(Collation.UNASSIGNED_CE32, data.getIndirectCE32(0xcd))
        data.contexts = "\uffff\u0001abc"
        assertEquals(0xffff0001.toInt(), data.getCE32FromContexts(0))
        assertFailsWith<IndexOutOfBoundsException> { data.getCE32FromContexts(-1) }
        assertFailsWith<IndexOutOfBoundsException> { data.getCE32FromContexts(4) }
    }
    @Test fun numericSafetyCompressionAndFcd() {
        val data = CollationData(Norm2AllModes.getNFCInstance().impl)
        data.unsafeBackwardSet = UnicodeSet().add(0x660)
        assertTrue(data.isUnsafeBackward(0x660, true))
        assertFalse(data.isUnsafeBackward(0x661, false))
        assertFailsWith<NullPointerException> { data.isUnsafeBackward(0x661, true) }
        assertTrue(data.isDigit(0x30))
        assertFalse(data.isDigit(0x40))
        data.trie = constantCollationTrie(0xca)
        assertTrue(data.isDigit(0x660))
        data.compressibleBytes = BooleanArray(256).also { it[0xfe] = true }
        assertTrue(data.isCompressiblePrimary(0xfe000000L))
        assertFalse(data.isCompressiblePrimary(0x12000000L))
        assertFailsWith<IndexOutOfBoundsException> { data.isCompressibleLeadByte(256) }
        assertEquals(0xe6e6, data.getFCD16(0x301))
    }
    @Test fun scriptGroupsAliasesAndSourceReorderVectors() {
        val data = scriptCollationData()
        assertEquals(0x20000000L, data.getFirstPrimaryForGroup(25))
        assertEquals(0x2fffffffL, data.getLastPrimaryForGroup(25))
        assertEquals(7, data.getGroupForPrimary(0x30001234L))
        assertEquals(-1, data.getGroupForPrimary(0xff000000L))
        assertContentEquals(intArrayOf(7, 14), data.getEquivalentScripts(14))
        assertContentEquals(intArrayOf(4096), data.getEquivalentScripts(4096))
        assertSame(data.getEquivalentScripts(-2), CollationData(null).getEquivalentScripts(-2))
        val vectors = listOf(
            intArrayOf() to intArrayOf(), intArrayOf(25) to intArrayOf(),
            intArrayOf(14) to intArrayOf(536870912, 805306546, -33488940),
            intArrayOf(14, 25) to intArrayOf(536870912, 805306546, -33488940),
            intArrayOf(103, 25) to intArrayOf(536870912, 805306575, -16711724),
            intArrayOf(25, 103, 14) to intArrayOf(805306368, -33554431, -16711886),
            intArrayOf(4096, 14, 103, 25) to intArrayOf(536870912, 805306575, -16711724)
        )
        val ranges = UVector32()
        for ((reorder, expected) in vectors) {
            ranges.addElement(999)
            data.makeReorderRanges(reorder, ranges)
            assertContentEquals(expected, ranges.getBuffer().copyOf(ranges.size()))
        }
        for (bad in listOf(intArrayOf(25, 25), intArrayOf(14, 7), intArrayOf(103, 103), intArrayOf(-1))) {
            ranges.addElement(999)
            assertFailsWith<IllegalArgumentException> { data.makeReorderRanges(bad, ranges) }
            assertTrue(ranges.isEmpty())
        }
        ranges.addElement(999)
        assertFailsWith<NullPointerException> { data.makeReorderRanges(null, ranges) }
        assertTrue(ranges.isEmpty())
        CollationData(null).makeReorderRanges(intArrayOf(), ranges)
        assertEquals("setReorderCodes(): reordering too many partial-primary-lead-byte scripts", assertFailsWith<ICUException> { partialCollationData().makeReorderRanges(intArrayOf(25, 8, 14), ranges) }.message)

    }
}

/** Synthetic serialized immutable trie, not a production replacement for Trie2Writable. */
internal fun constantCollationTrie(value: Int): Trie2_32 {
    val indexLength = 2112
    val dataLength = 260
    val buffer = BinaryBuffer(ByteArray(16 + indexLength * 2 + dataLength * 4))
    buffer.writeInt(0x54726932); buffer.writeShort(1); buffer.writeShort(indexLength)
    buffer.writeShort(dataLength / 4); buffer.writeShort(0xffff); buffer.writeShort(192); buffer.writeShort(0x10000 shr 11)
    repeat(indexLength) { buffer.writeShort(192 shr 2) }
    repeat(dataLength) { buffer.writeInt(value) }
    return Trie2_32.createFromSerialized(buffer.bytes)
}
internal fun scriptCollationData(): CollationData = CollationData(null).also {
    it.numScripts = 26
    it.scriptsIndex = CharArray(42).also { index -> index[25] = 3.toChar(); index[14] = 4.toChar(); index[7] = 4.toChar(); index[8] = 5.toChar(); index[26] = 1.toChar(); index[40] = 2.toChar() }
    it.scriptStarts = intArrayOf(0, 0x300, 0x400, 0x2000, 0x3000, 0xfe00, 0xff00).map { c -> c.toChar() }.toCharArray()
}

internal fun partialCollationData(): CollationData = CollationData(null).also {
    it.numScripts = 26
    it.scriptsIndex = CharArray(42).also { index -> index[25] = 2.toChar(); index[14] = 3.toChar(); index[8] = 4.toChar(); index[26] = 1.toChar() }
    it.scriptStarts = intArrayOf(0, 0x300, 0x400, 0x501, 0xfe00, 0xff00).map { c -> c.toChar() }.toCharArray()
}
