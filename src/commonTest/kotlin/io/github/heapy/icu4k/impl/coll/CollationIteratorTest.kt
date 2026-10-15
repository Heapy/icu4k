// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.Norm2AllModes
import io.github.heapy.icu4k.impl.Trie2_32
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.CharsTrieBuilder
import io.github.heapy.icu4k.util.StringTrieBuilder
import kotlin.test.*

class CollationIteratorTest {
    @Test fun numericBoundariesLeadingZerosAndSegments() {
        val data = iteratorFixtureData()
        fun ces(s: String): List<Long> = iteratorForward(UTF16CollationIterator(data, true, s, 0))
        for ((digits, primary) in listOf("0" to 0x12020000L, "73" to 0x124b0000L, "74" to 0x124c0200L, "10233" to 0x1273ff00L, "10234" to 0x12740202L, "1042489" to 0x1283ffffL))
            assertEquals(listOf(Collation.makeCE(primary)), ces(digits), digits)
        assertEquals(ces("74"), ces("00074"))
        assertEquals(ces("0"), ces("0".repeat(600)))
        assertEquals(listOf(Collation.makeCE(0x12840d13), Collation.makeCE(0x123bbe00)), ces("1042490"))
        assertEquals(listOf(Collation.makeCE(0x12ff1e00), Collation.makeCE(0x12020000)), ces("1" + "0".repeat(254)))
    }
    @Test fun expansionsContractionsPrefixesHangulAndOffsets() {
        val data = iteratorFixtureData()
        val match = Collation.ceFromCE32(iteratorSimple('z'.code))
        assertEquals(listOf(match), iteratorForward(UTF16CollationIterator(data, false, "ab", 0)))
        assertEquals(listOf(Collation.ceFromCE32(iteratorSimple('q'.code))), iteratorForward(UTF16CollationIterator(data, false, "xp", 1)))
        val skipped = iteratorForward(UTF16CollationIterator(data, false, "a\u0323\u0301", 0))
        assertEquals(listOf(Collation.ceFromCE32(0x350405c1), Collation.unassignedCEFromCodePoint(0x323)), skipped)
        for (text in listOf("abc", "f", "g", "a\u0323\u0301", "xp", "12345678901234567890", "각", "😀\ud800A\udc00")) {
            val forward = iteratorForward(UTF16CollationIterator(data, true, text, 0))
            val backwards = UTF16CollationIterator(data, true, text, text.length)
            val offsets = UVector32()
            val reverse = mutableListOf<Long>()
            while (true) { val ce = backwards.previousCE(offsets); if (ce == Collation.NO_CE) break; reverse.add(ce) }
            assertEquals(forward.reversed(), reverse, text)
            assertEquals(0, backwards.getOffset())
        }
        val e = UTF16CollationIterator(data, false, "g", 1)
        val offsets = UVector32()
        assertEquals(data.ces!![1], e.previousCE(offsets))
        assertContentEquals(intArrayOf(0, 1, 1), offsets.getBuffer().copyOf(offsets.size()))
    }
    @Test fun nestedDiscontiguousContractionsReplaceSkippedMarks() {
        val data = iteratorNestedFixtureData()
        val text = "a\u0323\u0324\u0301\u0345"
        val expected = listOf(Collation.ceFromCE32(0x390405c1), Collation.ceFromCE32(0x380406c1), Collation.unassignedCEFromCodePoint(0x324))
        assertEquals(expected, iteratorForward(UTF16CollationIterator(data, false, text, 0)))
        val reverse = UTF16CollationIterator(data, false, text, text.length)
        val offsets = UVector32()
        val actual = buildList { while (true) { val ce = reverse.previousCE(offsets); if (ce == Collation.NO_CE) break; add(ce) } }
        assertEquals(expected.reversed(), actual)
        val beyond = "a\u0323\u0345X"
        assertEquals(listOf(Collation.ceFromCE32(0x390405c1), Collation.unassignedCEFromCodePoint(0x323), Collation.ceFromCE32(iteratorSimple('X'.code))), iteratorForward(UTF16CollationIterator(data, false, beyond, 0)))
    }
    @Test fun partialInitializationHistoryOwnershipAndUtf16Positions() {
        val data = iteratorFixtureData()
        val partial = UTF16CollationIterator(data)
        assertEquals(-1, partial.nextCodePoint())
        assertFailsWith<NullPointerException> { partial.getCEsLength() }
        assertFailsWith<NullPointerException> { partial.equals(partial) }
        partial.setText(false, "A", 0)
        val first = partial.nextCE()
        val backing = partial.getCEs()
        partial.setText(false, "", 0)
        assertSame(backing, partial.getCEs())
        assertEquals(first, partial.getCE(0)) // Capacity, not logical length.
        assertEquals(Collation.NO_CE, partial.nextCE())
        assertEquals(Collation.NO_CE, partial.nextCE())
        assertEquals(2, partial.getCEsLength())
        partial.setCurrentCE(123)
        assertEquals(123L, partial.getCE(1))
        partial.clearCEsIfNoneRemaining()
        assertEquals(0, partial.getCEsLength())
        partial.setText(false, "😀\ud800A\udc00", 0)
        assertEquals(0x1f600, partial.nextCodePoint()); assertEquals(2, partial.getOffset())
        assertEquals(0xd800, partial.nextCodePoint()); assertEquals('A'.code, partial.nextCodePoint())
        assertEquals(0xdc00, partial.nextCodePoint()); assertEquals(-1, partial.nextCodePoint())
        assertEquals(0xdc00, partial.previousCodePoint()); assertEquals('A'.code, partial.previousCodePoint())
        assertEquals(0xd800, partial.previousCodePoint()); assertEquals(0x1f600, partial.previousCodePoint())
        partial.resetToOffset(-1)
        assertFailsWith<IndexOutOfBoundsException> { partial.nextCodePoint() }
        assertEquals(0, partial.getOffset())
    }
    @Test fun partialProtectedAppendPreservesTagAndHookOrder() {
        val it = object : UTF16CollationIterator(iteratorFixtureData()) {
            var calls = 0
            fun appendRaw(ce32: Int) { appendCEsFromCE32(null, 65, ce32, true) }
            fun appendOffset(d: CollationData) { appendCEsFromCE32(d, 65, Collation.makeCE32FromTagAndIndex(14, 0), true) }
            override fun getCE32FromBuilderData(ce32: Int): Int { ++calls; return iteratorSimple(65) }
        }
        assertEquals("internal program error: should be unreachable", assertFailsWith<io.github.heapy.icu4k.util.ICUException> { it.appendRaw(Collation.FALLBACK_CE32) }.message)
        assertEquals(0, it.calls)
        assertFailsWith<NullPointerException> { it.appendRaw(0xc7) }
        assertEquals(1, it.calls)
        val emptyOffsets = iteratorFixtureData().apply { ces = longArrayOf() }
        assertFailsWith<IndexOutOfBoundsException> { it.appendOffset(emptyOffsets) }
        assertFailsWith<NullPointerException> { it.getCEsLength() }
    }
    @Test fun bufferGrowthAndClearRetainExpandedStorage() {
        val it = UTF16CollationIterator(iteratorFixtureData(), false, "g".repeat(2000), 0)
        assertEquals(4001, it.fetchCEs())
        assertEquals(5120, it.getCEs().size)
        val backing = it.getCEs()
        it.setText(false, "g", 0)
        assertSame(backing, it.getCEs())
        it.nextCE(); it.clearCEsIfNoneRemaining()
        assertEquals(2, it.getCEsLength())
        it.nextCE(); it.clearCEsIfNoneRemaining()
        assertEquals(0, it.getCEsLength())
    }
}
internal fun iteratorForward(it: UTF16CollationIterator): List<Long> = buildList {
    while (true) { val ce = it.nextCE(); if (ce == Collation.NO_CE) break; add(ce) }
}
internal fun iteratorSimple(c: Int): Int = ((0x2000 + c) shl 16) or 0x0505
internal fun iteratorFixtureData(): CollationData {
    val data = CollationData(Norm2AllModes.getNFCInstance().impl)
    val map = (0..127).associateWith(::iteratorSimple).toMutableMap()
    data.ce32s = IntArray(20) { iteratorSimple(it) }
    data.ces = longArrayOf(0x1234567805000500, 0x2345678906000700, (0x34040200L shl 32) or ('o'.code.toLong() shl 8) or 0x82)
    for (digit in 0..9) map['0'.code + digit] = Collation.makeCE32FromTagIndexAndLength(Collation.DIGIT_TAG, digit, digit)
    map[0] = 0xcb
    map['e'.code] = 0x231234c4
    data.ce32s!![10] = iteratorSimple('x'.code); data.ce32s!![11] = 0x234567c1; data.ce32s!![12] = 0x123456c2
    data.ce32s!![13] = iteratorSimple('v'.code); data.ce32s!![14] = iteratorSimple('w'.code)
    map['f'.code] = Collation.makeCE32FromTagIndexAndLength(5, 10, 3)
    map['g'.code] = Collation.makeCE32FromTagIndexAndLength(6, 0, 2)
    map['o'.code] = Collation.makeCE32FromTagAndIndex(14, 2)
    val suffixes = CharsTrieBuilder().add("b", iteratorSimple('z'.code)).add("bc", Collation.makeCE32FromTagIndexAndLength(5, 13, 2)).add("\u0301", 0x350405c1).buildCharSequence(StringTrieBuilder.Option.SMALL).toString()
    val contexts = StringBuilder().append((iteratorSimple('a'.code) ushr 16).toChar()).append(iteratorSimple('a'.code).toChar()).append(suffixes)
    val prefixOffset = contexts.length
    val prefixes = CharsTrieBuilder().add("x", iteratorSimple('q'.code)).add("xy", 0x370405c1).buildCharSequence(StringTrieBuilder.Option.SMALL)
    contexts.append((iteratorSimple('p'.code) ushr 16).toChar()).append(iteratorSimple('p'.code).toChar()).append(prefixes)
    data.contexts = contexts.toString()
    map['a'.code] = Collation.makeCE32FromTagAndIndex(9, 0) or Collation.CONTRACT_TRAILING_CCC
    map['p'.code] = Collation.makeCE32FromTagAndIndex(8, prefixOffset)
    for (cp in 0xac00..0xac1f) map[cp] = 0xcc or Collation.HANGUL_NO_SPECIAL_JAMO
    data.jamoCE32s = IntArray(67) { iteratorSimple(it) }
    data.trie = iteratorFixtureTrie(map)
    data.unsafeBackwardSet = UnicodeSet().add('b'.code).add('c'.code).add(0x300, 0x36f)
    data.compressibleBytes = BooleanArray(256)
    return data
}
/** Test-only serialization, retaining separate code-point and single-lead-surrogate blocks. */
internal fun iteratorFixtureTrie(values: Map<Int, Int>, leadType: Int = 0xcd, supplementary: Int = Collation.UNASSIGNED_CE32): Trie2_32 {
    val index = IntArray(2112) { 192 shr 2 }
    val data = MutableList(256) { Collation.UNASSIGNED_CE32 }
    for (i in 224..255) data[i] = leadType
    for (i in (0xd800 shr 5)..(0xdbff shr 5)) index[i] = 224 shr 2
    val blocks = mutableMapOf<Int, Int>()
    for ((cp, ce32) in values) {
        val key = if (cp in 0xd800..0xdbff) 2048 + ((cp - 0xd800) shr 5) else cp shr 5
        val offset = blocks.getOrPut(key) { val at = data.size; repeat(32) { data.add(Collation.UNASSIGNED_CE32) }; index[key] = at shr 2; at }
        data[offset + (cp and 31)] = ce32
    }
    repeat(4) { data.add(supplementary) }
    val bytes = BinaryBuffer(ByteArray(16 + index.size * 2 + data.size * 4))
    bytes.writeInt(0x54726932); bytes.writeShort(1); bytes.writeShort(index.size); bytes.writeShort(data.size / 4)
    bytes.writeShort(0xffff); bytes.writeShort(192); bytes.writeShort(0x10000 shr 11)
    for (i in index) bytes.writeShort(i)
    for (i in data) bytes.writeInt(i)
    return Trie2_32.createFromSerialized(bytes.bytes)
}

internal fun iteratorNestedFixtureData(): CollationData {
    val data = iteratorFixtureData()
    val values = (0..127).associateWith { data.getCE32(it) }.toMutableMap()
    for (c in 0xac00..0xac1f) values[c] = data.getCE32(c)
    val contexts = StringBuilder(data.contexts!!)
    fun record(default: Int, suffix: String, result: Int): Int {
        val index = contexts.length
        contexts.append((default ushr 16).toChar()).append(default.toChar())
        contexts.append(CharsTrieBuilder().add(suffix, result).buildCharSequence(StringTrieBuilder.Option.SMALL))
        return Collation.makeCE32FromTagAndIndex(9, index) or Collation.CONTRACT_NEXT_CCC or Collation.CONTRACT_TRAILING_CCC
    }
    values['a'.code] = record(iteratorSimple('a'.code), "\u0345", 0x390405c1)
    values[0x323] = record(Collation.UNASSIGNED_CE32, "\u0301", 0x380406c1)
    data.contexts = contexts.toString()
    data.trie = iteratorFixtureTrie(values)
    return data
}
