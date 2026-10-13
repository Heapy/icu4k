package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.RuleCharacterIterator
import kotlin.test.*

class UnicodeSetExpressionTest {
    @Test fun recursiveExpressionsAndStringRangesUseOriginalGrammar() {
        fun parse(pattern: String, options: Int = 0): UnicodeSet {
            val result = UnicodeSet(); val pos = ParsePosition(0)
            result.applyPattern(RuleCharacterIterator(pattern, null, pos), null, StringBuilder(), options, 0)
            assertEquals(pattern.length, pos.index)
            return result
        }
        val set = parse("[[a-z]-[aeiou]{ab}-{cd}]")
        assertTrue(set.contains('b'.code)); assertFalse(set.contains('a'.code))
        assertTrue(set.contains("ab")); assertTrue(set.contains("cd")); assertFalse(set.contains("ce"))
        assertTrue(parse("[k]", UnicodeSet.CASE_INSENSITIVE).contains(0x212a))
        assertFalse(parse("[^a{ab}]").hasStrings())
        assertTrue(parse("[a$]").contains(0xffff))
        assertFailsWith<IllegalArgumentException> { parse("[a-a]") }
    }
}
