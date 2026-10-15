// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

class FCDUTF16CollationIteratorTest {
    @Test fun normalizedSegmentsMapToRawBoundariesInBothDirections() {
        val it = FCDUTF16CollationIterator(iteratorFixtureData(), false, "a\u0315\u0300b", 0)
        val forward = listOf(0x61 to 1, 0x300 to 3, 0x315 to 3, 0x62 to 4, -1 to 4)
        for ((cp, offset) in forward) { assertEquals(cp, it.nextCodePoint()); assertEquals(offset, it.getOffset()) }
        val backward = listOf(0x62 to 3, 0x315 to 3, 0x300 to 1, 0x61 to 0, -1 to 0)
        for ((cp, offset) in backward) { assertEquals(cp, it.previousCodePoint()); assertEquals(offset, it.getOffset()) }
    }
    @Test fun turningInsideNormalizedSegmentKeepsItsCursor() {
        val it = FCDUTF16CollationIterator(iteratorFixtureData(), false, "a\u0315\u0300b", 0)
        assertEquals(0x61, it.nextCodePoint()); assertEquals(0x300, it.nextCodePoint())
        assertEquals(0x300, it.previousCodePoint()); assertEquals(1, it.getOffset())
        assertEquals(0x300, it.nextCodePoint()); assertEquals(3, it.getOffset())
        assertEquals(0x315, it.nextCodePoint()); assertEquals(0x315, it.previousCodePoint())
        it.resetToOffset(0)
        assertEquals(0x61, it.nextCodePoint())
        it.setText(true, "\u0f73", 0)
        assertEquals(0xf71, it.nextCodePoint()); assertEquals(1, it.getOffset())
        assertEquals(0xf72, it.nextCodePoint()); assertEquals(-1, it.nextCodePoint())
        assertEquals(0xf72, it.previousCodePoint()); assertEquals(0xf71, it.previousCodePoint()); assertEquals(0, it.getOffset())
    }
    @Test fun fcdTextStaysComposedAndSurrogatesArePreserved() {
        val it = FCDUTF16CollationIterator(iteratorFixtureData(), false, iteratorMalformedText("é😀", 'x'), 0)
        val expected = listOf(0xe9, 0x1f600, 0xd800, 0x78, 0xdc00)
        for (cp in expected) assertEquals(cp, it.nextCodePoint())
        assertEquals(-1, it.nextCodePoint())
        for (cp in expected.reversed()) assertEquals(cp, it.previousCodePoint())
        assertEquals(-1, it.previousCodePoint())
    }
    @Test fun collationNormalizesOnlyTheFailingSegments() {
        val d = iteratorFixtureData()
        for (numeric in listOf(false, true)) {
            val fcd = FCDUTF16CollationIterator(d, numeric, "a\u0315\u0300b123", 0)
            val nfd = UTF16CollationIterator(d, numeric, "a\u0300\u0315b123", 0)
            while (true) { val ce = nfd.nextCE(); assertEquals(ce, fcd.nextCE()); if (ce == Collation.NO_CE) break }
            val fo = UVector32(); val no = UVector32()
            while (true) { val ce = nfd.previousCE(no); assertEquals(ce, fcd.previousCE(fo)); if (ce == Collation.NO_CE) break }
        }
    }
    @Test fun partialInitializationAndInvalidOffsetsRetainSourceFailures() {
        val it = FCDUTF16CollationIterator(iteratorFixtureData())
        assertEquals(-1, it.nextCodePoint()); assertEquals(-1, it.previousCodePoint())
        assertFalse(it.equals(null)); assertFalse(it.equals("text"))
        assertFailsWith<NullPointerException> { it.resetToOffset(0) }
        it.setText(false, "a", -1)
        assertFailsWith<IndexOutOfBoundsException> { it.nextCodePoint() }
        assertEquals(0, it.getOffset())
    }
}
