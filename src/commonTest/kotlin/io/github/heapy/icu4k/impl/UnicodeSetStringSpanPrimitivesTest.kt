package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.OutputInt
import kotlin.test.*

class UnicodeSetStringSpanPrimitivesTest {
    @Test fun offsetRingKeepsMinimumCountAndWraps() {
        val offsets = UnicodeSetStringSpan.OffsetList()
        offsets.setMaxLength(20)
        offsets.addOffsetAndCount(3, 7); offsets.addOffsetAndCount(3, 5); offsets.addOffsetAndCount(3, 9)
        offsets.addOffset(20)
        assertTrue(offsets.hasCountAtOffset(3, 5)); assertFalse(offsets.hasCountAtOffset(3, 4))
        val count = OutputInt()
        assertEquals(3, offsets.popMinimum(count)); assertEquals(5, count.value)
        assertTrue(offsets.containsOffset(17))
        offsets.shift(10)
        assertEquals(7, offsets.popMinimum(count)); assertEquals(1, count.value)
        assertTrue(offsets.isEmpty())
        offsets.addOffset(20)
        assertEquals(20, offsets.popMinimum(null))
    }
    @Test fun matchesRequireCodePointBoundaries() {
        val text = charArrayOf('a', 0xd800.toChar(), 0xdc00.toChar(), 'b').concatToString()
        assertFalse(UnicodeSetStringSpan.matches16CPB(text, 1, 4, text.substring(1, 2), 1))
        assertFalse(UnicodeSetStringSpan.matches16CPB(text, 2, 4, text.substring(2, 3), 1))
        assertTrue(UnicodeSetStringSpan.matches16CPB(text, 1, 4, text.substring(1, 3), 2))
        val set = UnicodeSet(0x10000, 0x10000)
        assertEquals(2, UnicodeSetStringSpan.spanOne(set, text, 1, 3))
        assertEquals(2, UnicodeSetStringSpan.spanOneBack(set, text, 3))
        assertEquals(-1, UnicodeSetStringSpan.spanOne(set, text, 1, 1))
    }
}
