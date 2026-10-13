package io.github.heapy.icu4k.lang

import kotlin.test.*

class CharSequencesTest {
    @Test fun surrogateBoundariesAndCodePoints() {
        assertEquals(0, CharSequences.matchAfter("😀", "😁", 0, 0))
        assertEquals(2, CharSequences.matchAfter("😀a", "😀b", 0, 0))
        assertFalse(CharSequences.onCharacterBoundary("😀", 1))
        assertContentEquals(intArrayOf(65, 0x1f600), CharSequences.codePoints("A😀"))
        assertEquals(1, CharSequences.indexOf("A😀", 0x1f600))
        assertEquals(0x1f600, CharSequences.getSingleCodePoint("😀"))
        assertEquals(Int.MAX_VALUE, CharSequences.getSingleCodePoint("ab"))
        assertTrue(CharSequences.equals(0x1f600, "😀"))
        assertFalse(CharSequences.equals(0x1f600, null))
        assertTrue(CharSequences.equalsChars(StringBuilder("abc"), "abc"))
    }
}
