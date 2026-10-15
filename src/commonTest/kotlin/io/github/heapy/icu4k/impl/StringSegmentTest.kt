package io.github.heapy.icu4k.impl

import kotlin.test.*

class StringSegmentTest {
    @Test fun windowAndPrefixSemantics() {
        val window = StringSegment("XX😀ABC", true)
        window.adjustOffset(2); window.setLength(1)
        assertEquals(0xd83d, window.getCodePoint())
        assertEquals(0x1f600, window.codePointAt(0))
        assertEquals(0xde00, window[1].code)
        window.resetLength()
        assertEquals(5, window.length)
        window.adjustOffsetByCodePoint()
        assertEquals("ABC", window.asString())
        assertTrue(window.startsWith("abc"))
        assertEquals(2, window.getCommonPrefixLength("abZ"))
        assertEquals(0, window.getCaseSensitivePrefixLength("abc"))
        assertFalse(window.contentEquals("abc")); assertTrue(window.contentEquals("ABC"))
        assertFalse(window.startsWith(null as CharSequence?))
        assertEquals("XX😀[ABC]", window.toString())
    }
}
