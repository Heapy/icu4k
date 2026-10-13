package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeFilterTest {
    @Test fun baseMatcherHandlesCodePointsAndIncrementalBoundary() {
        val filter: UnicodeFilter = UnicodeSet().add(0x1f600).add('A'.code)
        val text = ReplaceableString("😀A")
        val offset = intArrayOf(0)
        assertEquals(UnicodeMatcher.U_MATCH, filter.matches(text, offset, 3, false))
        assertEquals(2, offset[0])
        assertEquals(UnicodeMatcher.U_MATCH, filter.matches(text, offset, -1, false))
        assertEquals(0, offset[0])
        assertEquals(UnicodeMatcher.U_MATCH, filter.matches(text, offset, -1, false))
        assertEquals(-1, offset[0])
        // UnicodeSet adds ETHER anchoring; the base filter has a partial boundary.
        val base = object : UnicodeFilter() {
            override fun contains(c: Int): Boolean = c == 'A'.code
            override fun toPattern(escapeUnprintable: Boolean): String = "[A]"
            override fun matchesIndexValue(v: Int): Boolean = v == 65
            override fun addMatchSetTo(toUnionTo: UnicodeSet) { toUnionTo.add(65) }
        }
        offset[0] = 0
        assertEquals(UnicodeMatcher.U_PARTIAL_MATCH, base.matches(text, offset, 0, true))
        assertEquals(UnicodeMatcher.U_MISMATCH, base.matches(text, offset, 0, false))
    }
}
