package io.github.heapy.icu4k.impl.locale

import kotlin.test.*

class LocalePrimitivesTest {
    @Test fun asciiCasingAndKeysDoNotApplyUnicodeCaseFolding() {
        assertEquals("1AbC", AsciiUtil.toTitleString("1AbC"))
        assertEquals("Latn", AsciiUtil.toTitleString("LATN"))
        assertFalse(AsciiUtil.caseIgnoreMatch("İ", "i"))
        assertEquals(-25, AsciiUtil.caseIgnoreCompare("A", "z"))
        assertEquals(4, AsciiUtil.caseIgnoreCompare("abcde", "a"))
        assertEquals(AsciiUtil.CaseInsensitiveKey("en-US"), AsciiUtil.CaseInsensitiveKey("EN-us"))
        assertTrue(AsciiUtil.isAlphaString("")); assertTrue(AsciiUtil.isNumericString(""))
        val unchanged = "é123"
        assertSame(unchanged, AsciiUtil.toLowerString(unchanged))
        assertSame(unchanged, AsciiUtil.toUpperString(unchanged))
    }
    @Test fun tokenizationPreservesEmptySubtagsAndResetState() {
        val tokens = StringTokenIterator("en--US-", "-")
        val actual = mutableListOf<String?>()
        while (!tokens.isDone()) { actual.add(tokens.current()); tokens.next() }
        assertEquals(listOf<String?>("en", "", "US", ""), actual)
        assertEquals(7, tokens.currentStart()); assertEquals(7, tokens.currentEnd())
        assertEquals("en", tokens.first()); assertFalse(tokens.isDone())
        val status = ParseStatus().also { it._parseLength = 4; it._errorIndex = 3; it._errorMsg = "bad" }
        assertTrue(status.isError()); status.reset()
        assertFalse(status.isError()); assertEquals(0, status.getParseLength()); assertNull(status.getErrorMessage())
        assertEquals(3, LocaleSyntaxException("bad", 3).getErrorIndex())
    }
}
