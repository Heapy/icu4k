package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetPatternApiTest {
    @Test fun publicParsingRetainsPatternsUntilOriginalInvalidationPoints() {
        val pattern = "[[a-c]{ab}]"
        val set = UnicodeSet(pattern)
        assertEquals(pattern, set.toPattern(false))
        set.add('b'.code).add("ab").compact()
        assertEquals(pattern, set.toPattern(false))
        assertEquals(pattern, set.cloneAsThawed().freeze().toPattern(false))
        set.add('d'.code)
        assertEquals("[a-d{ab}]", set.toPattern(false))
        val pos = ParsePosition(2)
        val parsed = UnicodeSet("xx[a-z]rest", pos, null)
        assertTrue(parsed.contains('m'.code)); assertEquals(7, pos.index)
        assertTrue(UnicodeSet("[k]", UnicodeSet.CASE_INSENSITIVE).contains(0x212a))
        assertFailsWith<IllegalArgumentException> { UnicodeSet("[a]rest") }
        assertFailsWith<UnsupportedOperationException> { UnicodeSet().freeze().applyPattern("[]") }
    }
}
