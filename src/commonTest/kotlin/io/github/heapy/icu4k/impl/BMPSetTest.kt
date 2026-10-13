package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet.SpanCondition
import io.github.heapy.icu4k.util.OutputInt
import kotlin.test.*

class BMPSetTest {
    @Test fun membershipAndSpansCountCodePoints() {
        val list = intArrayOf(65, 91, 0x1f600, 0x1f601, 0x110000)
        val set = BMPSet(list, list.size)
        assertTrue(set.contains(65)); assertFalse(set.contains(97)); assertTrue(set.contains(0x1f600))
        val text = "AB😀!"
        val count = OutputInt()
        assertEquals(4, set.span(text, 0, SpanCondition.CONTAINED, count))
        assertEquals(3, count.value)
        assertEquals(5, set.span(text, 4, SpanCondition.NOT_CONTAINED, count))
        assertEquals(1, count.value)
        assertEquals(0, set.spanBack(text, 4, SpanCondition.CONTAINED))
        assertEquals(4, set.spanBack(text, 5, SpanCondition.NOT_CONTAINED))
        assertEquals("1", count.toString())
        assertTrue(BMPSet(set, list.copyOf(), list.size).contains(0x1f600))
    }
    @Test fun mixedBlocksAndUnpairedSurrogates() {
        val list = intArrayOf(0x7ff, 0x841, 0xd800, 0xd801, 0xffff, 0x10000, 0x110000)
        val set = BMPSet(list, list.size)
        assertTrue(set.contains(0x7ff)); assertTrue(set.contains(0x840)); assertFalse(set.contains(0x841))
        val text = charArrayOf(0xd800.toChar(), 0xffff.toChar(), 'x').concatToString()
        val count = OutputInt(99)
        assertEquals(2, set.span(text, 0, SpanCondition.SIMPLE, count)); assertEquals(2, count.value)
        // Preserve the original backward machine on this malformed UTF-16 sequence.
        assertEquals(2, set.spanBack(text, 2, SpanCondition.CONTAINED))
    }
}
