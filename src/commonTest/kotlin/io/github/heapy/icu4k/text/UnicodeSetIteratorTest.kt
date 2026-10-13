package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetIteratorTest {
    @Test fun rangesThenStringsAndMixedMethods() {
        val set = UnicodeSet(65, 67).add(0x1f600).add("xy").add("ab").add("")
        val iter = UnicodeSetIterator(set)
        assertTrue(iter.next()); assertEquals(65, iter.codepoint)
        assertTrue(iter.nextRange()); assertEquals(66, iter.codepoint); assertEquals(67, iter.codepointEnd)
        assertTrue(iter.next()); assertEquals("😀", iter.getString())
        assertTrue(iter.nextRange()); assertEquals(UnicodeSetIterator.IS_STRING, iter.codepoint); assertEquals("", iter.string)
        assertTrue(iter.next()); assertEquals("ab", iter.getString())
        assertTrue(iter.next()); assertEquals("xy", iter.string)
        assertFalse(iter.next()); assertFalse(iter.nextRange())
        iter.reset(); iter.skipToStrings()
        assertTrue(iter.next()); assertEquals("", iter.string)
        assertEquals(listOf("A", "B", "C", "😀", "", "ab", "xy"), set.toList())
    }
    @Test fun stringViewAndResetToEmpty() {
        val set = UnicodeSet().add("ab")
        val view = set.strings()
        set.add("xy")
        assertEquals(listOf("ab", "xy"), view.toList())
        val iter = UnicodeSetIterator(set)
        iter.reset(UnicodeSet())
        assertFalse(iter.next())
        set.set(UnicodeSet().add("new"))
        assertEquals(listOf("ab", "xy"), view.toList())
    }
}
