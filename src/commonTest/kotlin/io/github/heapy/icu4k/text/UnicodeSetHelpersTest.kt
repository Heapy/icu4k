package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetHelpersTest {
    @Test fun patternRecognitionAndSymbolDefaults() {
        assertTrue(UnicodeSet.resemblesPattern("[a]", 0))
        assertTrue(UnicodeSet.resemblesPattern("\\p{L}", 0))
        assertTrue(UnicodeSet.resemblesPattern("\\N{x}", 0))
        assertFalse(UnicodeSet.resemblesPattern("\\p", 0))
        val symbols = object : UnicodeSet.XSymbolTable() { }
        val result = UnicodeSet(65, 67)
        assertFalse(symbols.applyPropertyAlias("x", "y", result))
        assertEquals(UnicodeSet(65, 67), result)
        assertNull(symbols.lookup("x"))
        assertNull(symbols.lookupMatcher(65))
        val position = ParsePosition(2)
        assertNull(symbols.parseReference("abc", position, 3))
        assertEquals(2, position.index)
        assertEquals(0x1f600, UnicodeSet.getSingleCodePoint("😀"))
        val target = arrayOf("", "", "tail")
        assertSame(target, UnicodeSet.addAllTo(listOf("a", "b"), target))
        assertContentEquals(arrayOf("a", "b", "tail"), target)
        assertTrue(UnicodeSet.compare(listOf("z"), listOf("a", "b"), UnicodeSet.ComparisonStyle.SHORTER_FIRST) < 0)
    }
}
