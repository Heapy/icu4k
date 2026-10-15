// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ParseException
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class PluralRulesTest {
    @Test fun publicLocaleAndParsedRules() {
        val english = PluralRules.forLocale(ULocale("en_US"))
        assertEquals("one", english.select(1.0))
        assertEquals("other", english.select(1.0, 1, 0))
        assertEquals("two", PluralRules.forLocale(ULocale("en"), PluralRules.PluralType.ORDINAL).select(2.0))
        assertSame(english, PluralRules.forLocale(ULocale("en")))
        val parsed = PluralRules.parseDescription("one:i=1 and v=0")
        assertEquals(english.select(1.0), parsed.select(1.0))
        assertEquals(listOf("one", "other"), parsed.getKeywords().toList())
        assertEquals(listOf(1.0), parsed.getSamples("one")!!.toList())
        assertEquals(1.0, parsed.getUniqueKeywordValue("one"))
        assertSame(PluralRules.DEFAULT, PluralRules.parseDescription("  "))
        assertNull(PluralRules.createRules("invalid"))
        val e = assertFailsWith<ParseException> { PluralRules.parseDescription("one") }
        assertEquals(0, e.getErrorOffset())
        assertEquals("missing ':' in rule description 'one'", e.message)
        assertFailsWith<UnsupportedOperationException> { (parsed.getKeywords() as MutableSet<String>).clear() }
    }
    @Test fun customOperandsAndFlagShortCircuit() {
        val calls = mutableListOf<String>()
        var infinite = false
        val custom = object : PluralRules.IFixedDecimal {
            override fun isInfinite(): Boolean { calls.add("infinite"); return infinite }
            override fun isNaN(): Boolean { calls.add("nan"); return false }
            override fun isHasIntegerValue(): Boolean = error("Not consulted by constraint evaluation")
            override fun getPluralOperand(operand: PluralRules.Operand?): Double { calls.add(operand!!.name); return 1.0 }
        }
        val rules = PluralRules.parseDescription("one:n=1")
        assertEquals("one", rules.select(custom))
        assertEquals(listOf("infinite", "nan", "n"), calls)
        calls.clear(); infinite = true
        assertEquals("other", rules.select(custom))
        assertEquals(listOf("infinite"), calls)
        val subclass = object : PluralRules.FixedDecimal(2.0) {
            override fun getPluralOperand(operand: PluralRules.Operand?): Double = 1.0
        }
        assertEquals("one", rules.select(subclass))
    }
    @Test fun fixedDecimalAndFactories() {
        val fixed = PluralRules.FixedDecimal.createWithExponent(1.2, 2, 3)
        assertEquals(1200.0, fixed.getPluralOperand(PluralRules.Operand.n))
        assertEquals(3.0, fixed.getPluralOperand(PluralRules.Operand.c))
        assertEquals("1.20e3", fixed.toString())
        assertEquals(2, PluralRules.FixedDecimal.decimals(1.25))
        assertEquals(PluralRules.Operand.v, PluralRules.FixedDecimal.getOperand("v"))
        assertTrue(PluralRules.getAvailableULocales().size > 200)
        assertFalse(PluralRules.Factory.getDefaultFactory().hasOverride(null))
        assertSame(PluralRules.DEFAULT, PluralRules.Factory.getDefaultFactory().forLocale(ULocale("zz_ZZ")))
    }
}
