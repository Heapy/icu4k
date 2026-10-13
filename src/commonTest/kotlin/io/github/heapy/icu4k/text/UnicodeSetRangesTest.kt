package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetRangesTest {
    @Test fun entriesAreReusedAndStringsExcluded() {
        val set = UnicodeSet(65, 66).add(90).add("ab")
        val iterator = set.ranges().iterator()
        val first = iterator.next()
        assertEquals("A-B", first.toString())
        assertSame(first, iterator.next())
        assertEquals("Z", first.toString())
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
        assertEquals("ABZ", set.stripFrom("xAByZ", false))
        assertEquals("xy", set.stripFrom("xAByZ", true))
        assertEquals(1, set.findIn("xA", 0, false))
        assertEquals(1, set.findLastIn("xA", 2, false))
        assertEquals(UnicodeSet(65, 67), UnicodeSet().add(65).add(67).addBridges(UnicodeSet().add(66)))
    }
}
