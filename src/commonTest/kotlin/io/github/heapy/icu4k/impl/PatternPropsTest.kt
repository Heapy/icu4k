package io.github.heapy.icu4k.impl

import kotlin.test.*

class PatternPropsTest {
    @Test fun distinguishesPatternWhitespaceAndSpaceCharacters() {
        assertTrue(PatternProps.isWhiteSpace(0x200e))
        assertFalse(PatternProps.isWhiteSpace(0xa0))
        assertTrue(PatternProps.isSyntax(0x2190))
        assertFalse(PatternProps.isSyntax(0x1f600))
        assertEquals("word", PatternProps.trimWhiteSpace("\u200e word\u2028"))
        assertEquals("word\u00a0", PatternProps.trimSpaceChar("\u00a0word\u00a0"))
        assertFailsWith<IndexOutOfBoundsException> { PatternProps.trimSpaceChar("\t ") }
        assertEquals(2, PatternProps.skipWhiteSpace("\t word", 0))
        assertEquals(4, PatternProps.skipIdentifier("word!", 0))
        assertTrue(PatternProps.isIdentifier("hello世界"))
        assertFalse(PatternProps.isIdentifier(""))
        assertFalse(PatternProps.isIdentifier("a-b"))
    }
}
