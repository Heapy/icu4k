package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.RuleCharacterIterator
import kotlin.test.*

class UnicodeSetPropertyPatternTest {
    @Test fun propertyFormsPositionsAndRebuiltText() {
        for (pattern in listOf("[:gc=Lu:]", "\\p{Lu}", "\\p { gc = Lu }")) {
            val set = UnicodeSet()
            val pos = ParsePosition(2)
            assertSame(set, set.applyPropertyPattern("xx${pattern}tail", pos, null))
            assertTrue(set.contains(65)); assertFalse(set.contains(97))
            assertEquals(pattern.length + 2, pos.index)
        }
        val set = UnicodeSet()
        set.applyPropertyPattern("[:^Lu:]", ParsePosition(0), null)
        assertFalse(set.contains(65)); assertTrue(set.contains(97))
        set.applyPropertyPattern("\\N{LATIN CAPITAL LETTER A}", ParsePosition(0), null)
        assertEquals(UnicodeSet(65, 65), set)
        val pos = ParsePosition(0)
        val chars = RuleCharacterIterator("\\p{Lu}tail", null, pos)
        assertTrue(UnicodeSet.resemblesPropertyPattern(chars, RuleCharacterIterator.PARSE_ESCAPES))
        assertEquals(0, pos.index)
        val rebuilt = StringBuilder("!")
        set.applyPropertyPattern(chars, rebuilt, null)
        assertEquals("!\\p{Lu}", rebuilt.toString())
        assertEquals('t'.code, chars.next(0))
        val invalid = ParsePosition(0)
        assertNull(set.applyPropertyPattern("\\p Lu", invalid, null))
        assertEquals(0, invalid.index)
    }
}
