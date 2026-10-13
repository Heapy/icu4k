package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetExtraTest {
    @Test fun representationsAndMatchAtCompatibility() {
        assertEquals("[A-C]", UnicodeSet(65, 67).toString())
        assertEquals("(?:[A]|ab)", UnicodeSet().add(65).add("ab").getRegexEquivalent())
        assertEquals(1, UnicodeSet(65, 67).matchesAt("ABC", 0))
        assertEquals(2, UnicodeSet.from("ab").matchesAt("ab", 0))
        assertEquals(-1, UnicodeSet.from("ab").matchesAt("abc", 0))
        assertFailsWith<IndexOutOfBoundsException> { UnicodeSet.from("").matchesAt("abc", 0) }
    }
}
