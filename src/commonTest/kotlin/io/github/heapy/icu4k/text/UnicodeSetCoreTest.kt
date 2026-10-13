package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetCoreTest {
    @Test fun mergesRangesAndSeparatesStringsFromCodePoints() {
        val set = UnicodeSet().add(1).add(3).add(2).add(0).add(0xffff, 0x10000)
        assertEquals(2, set.getRangeCount())
        assertEquals(0, set.getRangeStart(0)); assertEquals(3, set.getRangeEnd(0))
        assertTrue(set.contains(0, 3)); assertFalse(set.contains(0, 4))
        set.add("ab").add("").add("😀")
        assertTrue(set.contains("ab")); assertTrue(set.contains("")); assertTrue(set.contains(0x1f600))
        assertEquals(9, set.size())
        val copy = UnicodeSet(set)
        set.clear()
        assertTrue(set.isEmpty()); assertTrue(copy.contains("ab"))
        copy.addAll(copy).compact()
        assertEquals(9, copy.size())
    }
    @Test fun reachesUnicodeMaximumAndChecksBounds() {
        val set = UnicodeSet().add(0x10ffff)
        assertTrue(set.contains(0x10ffff))
        set.add(0, 0x10fffe)
        assertEquals(1, set.getRangeCount()); assertEquals(0x110000, set.size())
        assertFailsWith<IllegalArgumentException> { set.add(-1) }
        assertFailsWith<IllegalArgumentException> { set.contains(0x110000) }
        assertFailsWith<IllegalArgumentException> { UnicodeSet(1, 2, 2, 3) }
        assertEquals(UnicodeSet(set), set)
    }
}
