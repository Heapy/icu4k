package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import kotlin.test.*

class RuleCharacterIteratorTest {
    @Test fun variableExpansionSnapshotsAndEscapes() {
        val symbols = object : SymbolTable {
            override fun lookup(s: String): CharArray? = if (s == "x") " A\\u0042".toCharArray() else null
            override fun lookupMatcher(ch: Int): UnicodeMatcher? = null
            override fun parseReference(text: String, pos: ParsePosition, limit: Int): String? {
                if (pos.index >= limit || text[pos.index] !in 'a'..'z') return null
                return text[pos.index++].toString()
            }
        }
        val position = ParsePosition(0)
        val iterator = RuleCharacterIterator("\$x!", symbols, position)
        assertEquals('A'.code, iterator.next(7))
        assertTrue(iterator.inVariable())
        assertEquals(2, position.index)
        val saved = iterator.getPos(null)
        assertEquals('B'.code, iterator.next(7))
        assertTrue(iterator.isEscaped())
        assertFalse(iterator.inVariable())
        iterator.setPos(saved)
        assertEquals('B'.code, iterator.next(7))
        assertEquals('!'.code, iterator.next(7))
        assertTrue(iterator.atEnd())
        assertEquals(-1, iterator.next(7))
        assertFailsWith<IllegalArgumentException> { RuleCharacterIterator("\$y", symbols, ParsePosition(0)).next(7) }
    }
    @Test fun skippingAndJumpingUseCodeUnits() {
        val iterator = RuleCharacterIterator(" \t😀!", null, ParsePosition(0))
        iterator.skipIgnored(4)
        assertEquals(2, iterator.getCurrentBufferPos())
        iterator.jumpahead(2)
        assertEquals('!'.code, iterator.next(0))
        assertFailsWith<IllegalArgumentException> { iterator.jumpahead(1) }
    }
}
