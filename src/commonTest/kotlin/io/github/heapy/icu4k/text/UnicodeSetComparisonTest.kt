package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetComparisonTest {
    @Test fun lengthAndLexicographicOrdersAreDistinct() {
        val a = UnicodeSet.fromAll("ab")
        val b = UnicodeSet.fromAll("z")
        assertTrue(a.compareTo(b) > 0)
        assertTrue(a.compareTo(b, UnicodeSet.ComparisonStyle.LEXICOGRAPHIC) < 0)
        assertTrue(a.compareTo(b, UnicodeSet.ComparisonStyle.LONGER_FIRST) < 0)
        assertEquals(0, a.compareTo(UnicodeSet(a)))
        assertEquals(0, UnicodeSet.compare("😀", 0x1f600))
        assertTrue(UnicodeSet.compare("😀x", 0x1f600) > 0)
        assertTrue(UnicodeSet.compare(listOf("a"), listOf("a", "b")) < 0)
        assertFailsWith<IllegalArgumentException> { UnicodeSet.compare("a", -1) }
    }
}
