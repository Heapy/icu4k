// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class RbnfRuleCoreTest {
    private class Owner : RbnfRuleOwner, RbnfRuleSetOwner {
        val events = ArrayList<String>()
        var scanner: RbnfLenientScanner? = null
        var failureToken: String? = null
        override fun getDecimalFormatSymbols() = DecimalFormatSymbols(ULocale.ENGLISH)
        override fun getDefaultNaNRule() = RbnfRuleCore(this, "NaN: NaN")
        override fun getDefaultInfinityRule() = RbnfRuleCore(this, "Inf: infinity")
        override fun lenientParseEnabled() = false
        override fun getLenientScanner(): RbnfLenientScanner? { events.add("scanner"); return scanner }
        override fun makeRules(description: String, ruleSet: RbnfRuleSetCore, predecessor: RbnfRule?, rules: MutableList<RbnfRule>) {
            RbnfRuleCore.makeRules(description, ruleSet, predecessor as RbnfRuleCore?, this, rules)
        }
        override fun createPluralFormat(type: PluralRules.PluralType, pattern: String): PluralFormatCore =
            PluralFormatCore(PluralRules.forLocale(ULocale.ENGLISH, type), DecimalFormat("0", getDecimalFormatSymbols())).also { it.applyPattern(pattern) }
        override fun makeSubstitution(position: Int, rule: RbnfRuleCore, predecessor: RbnfRuleCore?, ruleSet: RbnfRuleSetCore, token: String): RbnfSubstitution {
            events.add("make:$position:$token:${rule.getBaseValue()}")
            if (token == failureToken) throw IllegalArgumentException("factory failure")
            return object : RbnfSubstitution {
                override fun getPos() = position
                override fun setDivisor(radix: Int, exponent: Short) { events.add("divisor:$token:$radix:$exponent") }
                override fun isModulusSubstitution() = token.startsWith('>')
                override fun doSubstitution(number: Long, output: StringBuilder?, position: Int, recursionCount: Int) {
                    events.add("format:$token:$recursionCount"); output!!.insert(position + getPos(), token)
                }
                override fun doSubstitution(number: Double, output: StringBuilder?, position: Int, recursionCount: Int) {
                    events.add("double:$token:$recursionCount"); output!!.insert(position + getPos(), token)
                }
                override fun doParse(text: String?, position: ParsePosition?, baseValue: Double, upperBound: Double, lenient: Boolean, executedMask: Int, recursionCount: Int): Number? {
                    events.add("parse:$token:$text:${baseValue.toRawBits()}:$executedMask:$recursionCount")
                    val number = text!!.toDoubleOrNull() ?: return 0L
                    position!!.index = text.length
                    return baseValue + number
                }
                override fun setDecimalFormatSymbols(symbols: DecimalFormatSymbols?) { events.add("symbols:$token") }
                override fun toString() = token
            }
        }
        fun ruleSet(fraction: Boolean = false) = RbnfRuleSetCore(this, arrayOf("%test: fixture"), 0).also {
            if (fraction) it.makeIntoFractionRuleSet()
        }
        fun rules(description: String, fraction: Boolean = false): List<RbnfRule> = ArrayList<RbnfRule>().also {
            RbnfRuleCore.makeRules(description, ruleSet(fraction), null, this, it)
        }
    }
    @Test fun descriptorsPowersAndOptionalAlternatives() {
        val owner = Owner()
        assertEquals(listOf("100: hundred;", "101: hundred plus;"), owner.rules("100: hundred[ plus]").map { it.toString() })
        assertEquals(listOf("100: single;", "101: plural;"), owner.rules("100: [plural|single]").map { it.toString() })
        assertEquals(listOf(100L, 100L), owner.rules("100: hundred[ plus]", true).map { it.getBaseValue() })
        assertEquals("1024/2>>: binary;", RbnfRuleCore(owner, "1,024/2>>: binary").toString())
        assertEquals(256L, RbnfRuleCore(owner, "1024/2>>: binary").getDivisor())
        assertEquals("0: ' leading;", RbnfRuleCore(owner, "' leading").toString())
        assertEquals(-2L, RbnfRuleCore(owner, "x,x: fraction").getBaseValue())
        assertEquals(',', RbnfRuleCore(owner, "x,x: fraction").getDecimalPoint())
        assertFailsWith<IllegalArgumentException> { RbnfRuleCore(owner, "10/0: bad") }
        assertFailsWith<IllegalArgumentException> { RbnfRuleCore(owner, "10>>: bad") }
        assertFailsWith<IndexOutOfBoundsException> { RbnfRuleCore(owner, ": bad") }
        assertFailsWith<IndexOutOfBoundsException> { owner.rules("100: [a]b|c") }
        assertEquals(Long.MIN_VALUE, RbnfRuleCore.power(2, 63))
        assertEquals(0L, RbnfRuleCore.power(2, 64))
        assertFailsWith<IllegalArgumentException> { RbnfRuleCore.power(-1, 1) }
        assertFailsWith<IllegalArgumentException> { RbnfRuleCore.power(2, -1) }
    }
    @Test fun substitutionsAreExtractedLeftToRightAndAppliedRightToLeft() {
        val owner = Owner()
        val rule = owner.rules("101: a<<b>>c").single() as RbnfRuleCore
        assertEquals(listOf("make:1:<<:101", "make:2:>>:101"), owner.events)
        owner.events.clear()
        val out = StringBuilder("[]")
        rule.doFormat(110L, out, 1, 9)
        assertEquals("[a<<b>>c]", out.toString())
        assertEquals(listOf("format:>>:9", "format:<<:9"), owner.events)
        assertTrue(rule.shouldRollBack(200))
        assertFalse(rule.shouldRollBack(201))
        assertFalse(rule.hasModulusSubstitution()) // Concrete type and virtual query differ.
        owner.events.clear()
        rule.setBaseValue(1000)
        assertEquals(listOf("divisor:<<:10:3", "divisor:>>:10:3"), owner.events)
        rule.setBaseValue(0)
        assertEquals(2, owner.events.size) // Zero resets exponent without notifying substitutions.
        owner.events.clear()
        rule.setDecimalFormatSymbols(null)
        assertEquals(listOf("symbols:<<", "symbols:>>"), owner.events)
    }
    @Test fun parseDelimitersFractionReciprocalAndPositions() {
        val owner = Owner()
        val rule = owner.rules("100: first << middle >> last").single()
        val pp = ParsePosition(0).also { it.errorIndex = 19 }
        assertEquals(103L, rule.doParse("first 1 middle 2 last trailing", pp, false, 200.0, 5, 7))
        assertEquals(21, pp.index)
        assertEquals(19, pp.errorIndex)
        assertTrue(owner.events.contains("parse:<<:1:${100.0.toRawBits()}:5:7"))
        assertTrue(owner.events.contains("parse:>>:2:${101.0.toRawBits()}:5:7"))
        val literal = owner.rules("2: half").single()
        pp.index = 9
        assertEquals(0.5, literal.doParse("half!", pp, true, 10.0, 0, 0))
        assertEquals(4, pp.index)
        pp.index = 9
        assertEquals(0L, literal.doParse("mismatch", pp, false, 10.0, 0, 0))
        assertEquals(9, pp.index) // Prefix failure leaves caller position unchanged.
        assertEquals(0L, literal.doParse("mismatch", null, false, 10.0, 0, 0))
    }
    @Test fun pluralInsertionAdjustsLaterSubstitutionOffsets() {
        val owner = Owner()
        val rule = owner.rules("100: << $(cardinal,one{unit}other{units})$ >>").single()
        val out = StringBuilder("[]")
        rule.doFormat(200L, out, 1, 0)
        assertEquals("[<< units >>]", out.toString())
        val singular = StringBuilder()
        rule.doFormat(0.01, singular, 0, 0)
        assertEquals("<< unit >>", singular.toString())
    }
    @Test fun lenientScannerUsesExactMatchesFirstAndRetainsConsumedLength() {
        val owner = Owner()
        var calls = 0
        owner.scanner = object : RbnfLenientScanner {
            override fun allIgnorable(s: String?) = false
            override fun prefixLength(str: String?, prefix: String?): Int { ++calls; return if (str!!.startsWith("HALF")) 4 else 0 }
            override fun findText(str: String?, key: String?, startingAt: Int): IntArray? = error("not expected")
        }
        val rule = owner.rules("2: half").single()
        val pp = ParsePosition(0)
        assertEquals(2L, rule.doParse("half", pp, false, 10.0, 0, 0))
        assertEquals(0, calls)
        assertEquals(2L, rule.doParse("HALF!", pp, false, 10.0, 0, 0))
        assertEquals(1, calls)
        assertEquals(4, pp.index)
    }
}
