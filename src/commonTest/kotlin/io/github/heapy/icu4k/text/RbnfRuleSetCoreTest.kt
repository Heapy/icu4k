package io.github.heapy.icu4k.text

import kotlin.test.*

class RbnfRuleSetCoreTest {
    private class Rule(var base: Long, val label: String, val consumed: Int = 0) : RbnfRule {
        var rollback = false
        var mask = 0
        var depth = 0
        override fun getBaseValue() = base
        override fun setBaseValue(value: Long) { base = value }
        override fun getDecimalPoint() = '.'
        override fun shouldRollBack(number: Long) = rollback
        override fun doFormat(number: Long, output: StringBuilder?, position: Int, recursionCount: Int) {
            depth = recursionCount; output!!.insert(position, label)
        }
        override fun doFormat(number: Double, output: StringBuilder?, position: Int, recursionCount: Int) {
            depth = recursionCount; output!!.insert(position, label)
        }
        override fun doParse(text: String?, position: ParsePosition?, fractionRule: Boolean, upperBound: Double, executedMask: Int, recursionCount: Int): Number {
            mask = executedMask; depth = recursionCount
            position!!.index = consumed
            return base
        }
        override fun setDecimalFormatSymbols(symbols: DecimalFormatSymbols?) {}
        override fun toString() = label
    }
    private class Owner(val pending: List<Rule>) : RbnfRuleSetOwner {
        override fun getDecimalFormatSymbols() = DecimalFormatSymbols()
        override fun getDefaultNaNRule() = Rule(-6, "NaN")
        override fun getDefaultInfinityRule() = Rule(-5, "Infinity")
        override fun makeRules(description: String, ruleSet: RbnfRuleSetCore, predecessor: RbnfRule?, rules: MutableList<RbnfRule>) { rules.addAll(pending) }
    }
    private fun set(vararg rules: Rule, fraction: Boolean = false): RbnfRuleSetCore {
        val result = RbnfRuleSetCore(Owner(rules.toList()), arrayOf("%test: fixture"), 0)
        if (fraction) result.makeIntoFractionRuleSet()
        result.parseRules("fixture")
        return result
    }
    @Test fun normalSelectionRollbackAndRecursion() {
        val zero = Rule(0, "zero")
        val ten = Rule(10, "ten")
        val core = set(zero, ten)
        fun format(n: Long, depth: Int = 0) = StringBuilder("[]").also { core.format(n, it, 1, depth) }.toString()
        assertEquals("[zero]", format(-9))
        assertEquals("[ten]", format(10))
        ten.rollback = true
        assertEquals("[zero]", format(11, 63))
        assertEquals(64, zero.depth)
        assertFailsWith<IllegalStateException> { format(11, 64) }
        assertEquals("[ten]", format(10)) // Exact base bypasses rollback.
    }
    @Test fun specialRulesAndLongDoubleDispatch() {
        val zero = Rule(0, "zero")
        val core = set(zero)
        val default = Rule(-4, "default")
        core.setNonNumericalRule(default)
        assertSame(default, core.findRule(2.0))
        val out = StringBuilder()
        core.format(2L, out, 0, 0)
        assertEquals("zero", out.toString())
        assertEquals(-6L, core.findRule(Double.NaN)!!.getBaseValue())
        assertEquals(-5L, core.findRule(Double.POSITIVE_INFINITY)!!.getBaseValue())
        val negative = Rule(-1, "negative")
        core.setNonNumericalRule(negative)
        assertSame(negative, core.findRule(Double.NEGATIVE_INFINITY))
    }
    @Test fun fractionsChooseNearestAndDistinguishPlural() {
        val half = Rule(2, "half")
        val halves = Rule(2, "halves")
        val third = Rule(3, "third")
        val core = set(half, halves, third, fraction = true)
        assertSame(half, core.findRule(0.5))
        assertSame(halves, core.findRule(1.0))
        assertSame(third, core.findRule(1.0 / 3))
        assertEquals(30L, RbnfRuleSetCore.lcm(6, 15))
    }
    @Test fun parsingKeepsLongestFirstTieAndAccumulatesMask() {
        val low = Rule(0, "low", 3)
        val high = Rule(10, "high", 3)
        val core = set(low, high)
        val negative = Rule(-1, "negative", 2)
        val nan = Rule(-6, "nan", 2)
        core.setNonNumericalRule(negative); core.setNonNumericalRule(nan)
        val position = ParsePosition(0).also { it.errorIndex = 7 }
        assertEquals(10L, core.parse("abcd", position, 100.0, 0, 0))
        assertEquals(3, position.index)
        assertEquals(7, position.errorIndex)
        assertEquals(1, negative.mask)
        assertEquals(33, nan.mask)
        assertEquals(33, high.mask)
        assertEquals(0L, core.parse("", null, 100.0, 0, 0))
        assertFailsWith<IllegalStateException> { core.parse("", null, 100.0, 0, 64) }
    }
    @Test fun namesAndNumericBoundaries() {
        val descriptions = arrayOf("%%hidden@noparse:   fixture")
        val core = RbnfRuleSetCore(Owner(emptyList()), descriptions, 0)
        assertEquals("%%hidden", core.getName())
        assertFalse(core.isPublic()); assertFalse(core.isParseable())
        assertEquals("fixture", descriptions[0])
        assertFailsWith<IndexOutOfBoundsException> { RbnfRuleSetCore(Owner(emptyList()), arrayOf("%x:"), 0) }
        assertEquals(0L, RbnfMath.roundToLong(-0.5))
        assertEquals(-1L, RbnfMath.roundToLong(-1.5))
        assertEquals(1L, RbnfMath.roundToLong(0.5))
        assertEquals(0L, RbnfMath.roundToLong(Double.NaN))
        assertEquals(Long.MAX_VALUE, RbnfMath.roundToLong(Double.POSITIVE_INFINITY))
        assertEquals(Long.MIN_VALUE, RbnfMath.divide(Long.MIN_VALUE, -1))
        assertEquals(0L, RbnfMath.remainder(Long.MIN_VALUE, -1))
    }
}
