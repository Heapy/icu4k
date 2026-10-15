// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.math.BigDecimal as IcuDecimal
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.BigInteger
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class RuleBasedNumberFormatTest {
    @Test fun predefinedFormattingParsingMetadataAndContext() {
        val f = RuleBasedNumberFormat(ULocale("en_US"), RuleBasedNumberFormat.SPELLOUT)
        assertEquals(ULocale.ENGLISH, f.getLocale(ULocale.ACTUAL_LOCALE))
        assertEquals("one thousand two hundred thirty-four", f.format(1234L))
        f.setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
        assertEquals("Twenty-one", f.format(21L, "%spellout-numbering"))
        val out = StringBuilder("prefix ")
        val field = FormatFieldPosition(0).also { it.setBeginIndex(4); it.setEndIndex(7) }
        assertSame(out, f.format(21L, out, field))
        assertEquals("prefix twenty-one", out.toString())
        assertEquals(4, field.getBeginIndex()); assertEquals(7, field.getEndIndex())
        assertEquals("Twenty-one", f.format(21L, StringBuilder(), null).toString())
        val pp = ParsePosition(2)
        assertEquals(0L, f.parse("xxTwenty-one!", pp)) // Uppercase is not accepted without a scanner.
        assertEquals(2, pp.index)
    }
    @Test fun decimalAndIntegerRoutesAreVirtualAndPreserveScale() {
        class Probe : RuleBasedNumberFormat("0: value;", ULocale.ENGLISH) {
            val events = ArrayList<String>()
            override fun format(number: Long, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder { events.add("long"); return output!!.append("long") }
            override fun format(number: Double, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder { events.add("double"); return output!!.append("double") }
            override fun format(number: IcuDecimal?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder { events.add("icu"); return super.format(number, output, position) }
        }
        val f = Probe()
        assertEquals("long", f.format(BigInteger("21")))
        assertEquals(listOf("icu", "long"), f.events)
        f.events.clear()
        assertEquals("double", f.format(BigDecimal("21.0")))
        assertEquals(listOf("icu", "double"), f.events)
        f.events.clear()
        assertEquals("long", f.format(IcuDecimal("21")))
        assertEquals(listOf("icu", "long"), f.events)
        f.events.clear()
        assertEquals("9,223,372,036,854,775,808", f.format(IcuDecimal("9223372036854775808")))
        assertEquals(listOf("icu"), f.events)
        assertFailsWith<NullPointerException> { f.format(null as BigInteger?) }
    }
    @Test fun copyingSharesRulesAndRetainsIndependentParentSettings() {
        val f = RuleBasedNumberFormat(RbnfTestEngine.ENGLISH, ULocale.ENGLISH)
        f.setMaximumFractionDigits(2)
        f.setRoundingMode(4)
        f.setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
        val clone = f.clone()
        assertTrue(f == clone)
        assertNotSame(f, clone)
        assertSame(f.runtime!!.getDefaultRuleSet(), clone.runtime!!.getDefaultRuleSet())
        clone.setMaximumFractionDigits(1); clone.setRoundingMode(3)
        assertEquals(2, f.getMaximumFractionDigits()); assertEquals(4, f.getRoundingMode())
        assertEquals("One point two four", f.format(1.235))
        assertEquals("One point two", clone.format(1.235))
        assertTrue(f == clone) // Source RBNF equality ignores parent configuration.
        clone.setLenientParseMode(true)
        assertFalse(f == clone)
    }
    @Test fun virtualScannerGetterAndNullOutputOrder() {
        class Probe : RuleBasedNumberFormat("%main: 0: one;", ULocale.ENGLISH) {
            var reads = 0
            override fun getLenientScannerProvider(): RbnfLenientScannerProvider? {
                ++reads
                return object : RbnfLenientScannerProvider {
                    override fun get(locale: ULocale?, extras: String?): RbnfLenientScanner = object : RbnfLenientScanner {
                        override fun allIgnorable(s: String?) = s!!.isEmpty()
                        override fun prefixLength(str: String?, prefix: String?) = if (str!!.startsWith(prefix!!, true)) prefix.length else 0
                        override fun findText(str: String?, key: String?, startingAt: Int) = intArrayOf(str!!.indexOf(key!!, startingAt, true), key.length)
                    }
                }
            }
        }
        val f = Probe()
        val pp = ParsePosition(0)
        assertEquals(0L, f.parse("ONE", pp)); assertEquals(0, pp.index); assertEquals(0, f.reads)
        f.setLenientParseMode(true)
        assertEquals(0L, f.parse("ONE", pp)); assertEquals(3, pp.index); assertEquals(1, f.reads)
        val broken = object : RuleBasedNumberFormat("0: literal;", ULocale.ENGLISH) { override fun getRoundingMode(): Int = error("query") }
        assertFailsWith<NullPointerException> { broken.format(1.5, null as StringBuilder?, null) }
        assertFailsWith<IllegalStateException> { broken.format(1.5, StringBuilder(), null) }
    }
    @Test fun localizationConstructorsAndParentParseFlags() {
        val f = RuleBasedNumberFormat("%a: 0: one; %b: 0: two;", arrayOf(arrayOf("%a", "%b"), arrayOf("en", "One", "Two")), ULocale.ENGLISH)
        assertNull(f.getLocale(ULocale.ACTUAL_LOCALE))
        assertEquals("%a", f.getDefaultRuleSetName())
        assertContentEquals(arrayOf("One", "Two"), f.getRuleSetDisplayNames(ULocale.ENGLISH))
        val numeric = RuleBasedNumberFormat("0: =0.0=;", ULocale.ENGLISH)
        numeric.setParseIntegerOnly(true)
        assertEquals(1.5, numeric.parse("1.5"))
        assertFailsWith<IllegalArgumentException> { f.format(1L, "%%private") }
    }
    @Test fun virtualNameAndLenientGettersReachCommonEnginePaths() {
        val names = arrayOf("%injected")
        val named = object : RuleBasedNumberFormat("0: one;", ULocale.ENGLISH) { override fun getRuleSetNames() = names }
        assertContentEquals(arrayOf("injected"), named.getRuleSetDisplayNames(null as ULocale?))
        assertContentEquals(arrayOf("injected"), names)
        val lenient = object : RuleBasedNumberFormat("%main: 0: zero; 1: one; 100: << hundred >>;", ULocale.ENGLISH) {
            override fun lenientParseEnabled() = true
        }
        val pp = ParsePosition(0)
        assertEquals(201L, lenient.parse("2 hundred one", pp))
        assertEquals("2 hundred one".length, pp.index)
        assertNull(lenient.getLenientScannerProvider()) // Discovery still consults the private mode, not the virtual query.
    }
}
