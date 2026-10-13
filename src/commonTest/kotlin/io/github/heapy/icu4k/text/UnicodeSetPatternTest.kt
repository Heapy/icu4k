package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetPatternTest {
    @Test fun generatesEscapesRangesStringsAndComplements() {
        assertEquals("[a-c]", UnicodeSet(97, 99).toPattern(false))
        assertEquals("[ab]", UnicodeSet(97, 98).toPattern(false))
        assertEquals("[^a]", UnicodeSet().add(97).complement().toPattern(false))
        assertEquals("[\\uDBFF-\\uDC00]", UnicodeSet(0xdbff, 0xdc00).toPattern(false))
        assertEquals("[{a\\-b}]", UnicodeSet().add("a-b").toPattern(false))
        assertEquals("[\\U0001F600]", UnicodeSet().add(0x1f600).toPattern(true))
        val matcher: UnicodeMatcher = UnicodeSet().add("abc")
        assertEquals("[{abc}]", matcher.toPattern(false))
    }
}
