package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetIteratorTest {
    @Test fun rangesStringsResetAndSkipping() {
        val iterator = UnicodeSetIterator(UnicodeSet(65, 67).add(0x1f600).add("ab").add(""))
        assertTrue(iterator.nextRange())
        assertEquals(65, iterator.codepoint)
        assertEquals(67, iterator.codepointEnd)
        assertTrue(iterator.nextRange())
        assertEquals("😀", iterator.getString())
        assertTrue(iterator.nextRange())
        assertEquals(-1, iterator.codepoint)
        assertEquals("", iterator.getString())
        assertTrue(iterator.nextRange())
        assertEquals("ab", iterator.string)
        assertFalse(iterator.nextRange())
        iterator.reset()
        assertSame(iterator, iterator.skipToStrings())
        assertTrue(iterator.next())
        assertEquals("", iterator.getString())
        iterator.reset(UnicodeSet())
        assertFalse(iterator.next())
        assertFalse(UnicodeSetIterator().nextRange())
    }
}
