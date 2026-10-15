// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class MessagePatternTest {
    @Test fun nestedPatternsAndOffsets() {
        val p = MessagePattern("Hi {name}: {0,plural,offset:1 =0{none} one{'#' #} other{{0,number} things}}")
        assertTrue(p.hasNamedArguments())
        assertTrue(p.hasNumberedArguments())
        assertEquals(1, (0..<p.countParts()).count { p.getPartType(it) == MessagePattern.Part.Type.REPLACE_NUMBER })
        val plural = (0..<p.countParts()).first { p.getPartType(it) == MessagePattern.Part.Type.ARG_START && p.getPart(it).getArgType() == MessagePattern.ArgType.PLURAL }
        assertEquals(MessagePattern.Part.Type.ARG_NUMBER, p.getPartType(plural + 1))
        assertEquals(1.0, p.getPluralOffset(plural + 2))
        assertEquals(MessagePattern.Part.Type.ARG_LIMIT, p.getPartType(p.getLimitPartIndex(plural)))
        assertEquals("name", p.getSubstring(p.getPart(2)))
    }
    @Test fun quotesCloneAndFreeze() {
        val p = MessagePattern("can't '{x}' end'")
        assertEquals("can''t '{x}' end''", p.autoQuoteApostropheDeep())
        val c = p.cloneAsThawed()
        assertEquals(p, c)
        assertSame(p.getPart(0), c.getPart(0))
        p.freeze()
        assertSame(p, p.clone())
        assertFailsWith<UnsupportedOperationException> { p.clear() }
        assertFailsWith<UnsupportedOperationException> { p.parse("x") }
        c.clearPatternAndSetApostropheMode(MessagePattern.ApostropheMode.DOUBLE_REQUIRED)
        c.parse("can't {0}")
        assertFalse(c.hasNumberedArguments())
        assertNull(MessagePattern().getPatternString())
    }
    @Test fun choiceAndNumericEdges() {
        val p = MessagePattern().parseChoiceStyle("-∞#negative|0#zero|1.5<positive")
        val values = (0..<p.countParts()).map { p.getNumericValue(p.getPart(it)) }.filter { it != MessagePattern.NO_NUMERIC_VALUE }
        assertEquals(listOf(Double.NEGATIVE_INFINITY, 0.0, 1.5), values)
        assertEquals(-2, MessagePattern.validateArgumentName("01"))
        assertEquals(-1, MessagePattern.validateArgumentName("01a"))
        assertEquals(-2, MessagePattern.validateArgumentName("2147483640"))
        val reduced = StringBuilder()
        MessagePattern.appendReducedApostrophes("x'a''b'c", 0, 8, reduced)
        assertEquals("xa'bc", reduced.toString())
        assertFailsWith<IllegalArgumentException> { p.parsePluralStyle("one{x}") }
        // ICU treats a prefix of 'other' as fulfilling the required fallback selector.
        p.parsePluralStyle("o{x}")
        assertEquals("o", p.getSubstring(p.getPart(0)))
    }
}
