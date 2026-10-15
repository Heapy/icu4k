// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import kotlin.test.*

class PluralConditionTest {
    @Test fun precedenceOperandsAndVisibleFractions() {
        val rule = PluralConditionParser.parse("i % 10 = 1 and v = 0 or n within 2..3")!!
        fun selected(n: Double, v: Int) = rule.evaluate(FixedDecimalCore(n, v)::getPluralOperand)
        assertTrue(selected(21.0, 0))
        assertFalse(selected(21.0, 1))
        assertTrue(selected(2.5, 1))
        assertFalse(selected(4.0, 0))
        assertEquals("i % 10 = 1 and v = 0 or n within 2..3", rule.toString())
        val j = PluralConditionParser.parse("j = 1")!!
        assertTrue(j.evaluate(FixedDecimalCore(1.0, 0)::getPluralOperand))
        assertFalse(j.evaluate(FixedDecimalCore(1.0, 1)::getPluralOperand))
    }
    @Test fun unicodeBoundsAndLongOverflow() {
        assertEquals("n = 1..3", PluralConditionParser.parse("n = ١..٣").toString())
        assertEquals(Long.MIN_VALUE, DecimalNumberParsing.long("-9223372036854775808"))
        assertEquals(Long.MAX_VALUE, DecimalNumberParsing.long("+9223372036854775807"))
        for (s in listOf("9223372036854775808", "-9223372036854775809", "", "+")) assertFailsWith<NumberFormatException> { DecimalNumberParsing.long(s) }
    }
    @Test fun sourceSplitAndDiagnostics() {
        assertNull(PluralConditionParser.parse("or"))
        assertEquals("n = 1", PluralConditionParser.parse("n=1 or").toString())
        assertEquals("n = 1 and n = 2", PluralConditionParser.parse("n=1andn=2").toString())
        val e = assertFailsWith<PluralConditionParseException> { PluralConditionParser.parse("n is not 1,2") }
        assertEquals("unexpected token 'is not <range>' in 'n is not 1,2'", e.message)
        assertEquals(-1, e.errorOffset)
    }
}
