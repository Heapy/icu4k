// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// Port of ICU4J 78.3 NFRuleSet.
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.PatternProps
import kotlin.math.floor

/** Full rule-set selection/formatting/parsing policy, independent of concrete rule construction. */
internal class RbnfRuleSetCore(val owner: RbnfRuleSetOwner, descriptions: Array<String>, index: Int) {
    private val name: String
    private var rawRules: Array<RbnfRule>? = null
    private val rules: Array<RbnfRule> get() = rawRules!!
    internal val nonNumericalRules = arrayOfNulls<RbnfRule>(6)
    internal var fractionRules: MutableList<RbnfRule>? = null
        private set
    private var fractionRuleSet = false
    private val parseable: Boolean

    init {
        if (index !in descriptions.indices) throw IndexOutOfBoundsException()
        val initial: String? = descriptions[index]
        var description = initial!!
        if (description.isEmpty()) throw IllegalArgumentException("Empty rule set description")
        if (description[0] == '%') {
            var pos = description.indexOf(':')
            if (pos == -1) throw IllegalArgumentException("Rule set name doesn't end in colon")
            if (pos < 2) throw IllegalArgumentException("Rule set name is '%'")
            var ruleName = description.substring(0, pos)
            parseable = !ruleName.endsWith("@noparse")
            if (!parseable) ruleName = ruleName.substring(0, ruleName.length - 8)
            name = ruleName
            // Preserve the source pre-increment: a terminal colon/whitespace fails on charAt.
            while (pos < description.length) {
                ++pos
                if (pos >= description.length) throw IndexOutOfBoundsException()
                if (!PatternProps.isWhiteSpace(description[pos].code)) break
            }
            description = description.substring(pos)
            descriptions[index] = description
        } else {
            name = "%default"
            parseable = true
        }
        if (description.isEmpty()) throw IllegalArgumentException("Empty rule set description")
    }

    fun parseRules(description: String?) {
        val tempRules = ArrayList<RbnfRule>()
        var predecessor: RbnfRule? = null
        var oldP = 0
        val length = description!!.length
        do {
            var p = description.indexOf(';', oldP)
            if (p < 0) p = length
            owner.makeRules(description.substring(oldP, p), this, predecessor, tempRules)
            if (tempRules.isNotEmpty()) predecessor = tempRules.last()
            oldP = p + 1
        } while (oldP < length)
        var defaultBaseValue = 0L
        for (rule in tempRules) {
            val baseValue = rule.getBaseValue()
            if (baseValue == 0L) rule.setBaseValue(defaultBaseValue) else {
                if (baseValue < defaultBaseValue) throw IllegalArgumentException("Rules are not in order, base: $baseValue < $defaultBaseValue")
                defaultBaseValue = baseValue
            }
            if (!fractionRuleSet) ++defaultBaseValue
        }
        rawRules = tempRules.toTypedArray()
    }
    fun setNonNumericalRule(rule: RbnfRule) {
        when (rule.getBaseValue()) {
            -1L -> nonNumericalRules[NEGATIVE_RULE_INDEX] = rule
            -2L -> setBestFractionRule(IMPROPER_FRACTION_RULE_INDEX, rule, true)
            -3L -> setBestFractionRule(PROPER_FRACTION_RULE_INDEX, rule, true)
            -4L -> setBestFractionRule(DEFAULT_RULE_INDEX, rule, true)
            -5L -> nonNumericalRules[INFINITY_RULE_INDEX] = rule
            -6L -> nonNumericalRules[NAN_RULE_INDEX] = rule
        }
    }
    private fun setBestFractionRule(index: Int, newRule: RbnfRule, rememberRule: Boolean) {
        if (rememberRule) {
            if (fractionRules == null) fractionRules = ArrayList()
            fractionRules!!.add(newRule)
        }
        if (nonNumericalRules[index] == null) nonNumericalRules[index] = newRule else {
            val symbols = owner.getDecimalFormatSymbols()
            if (symbols.getDecimalSeparator() == newRule.getDecimalPoint()) nonNumericalRules[index] = newRule
        }
    }
    fun makeIntoFractionRuleSet() { fractionRuleSet = true }
    fun isFractionSet(): Boolean = fractionRuleSet
    fun getName(): String = name
    fun isPublic(): Boolean = !name.startsWith("%%")
    fun isParseable(): Boolean = parseable

    override fun equals(other: Any?): Boolean {
        if (other !is RbnfRuleSetCore) return false
        if (name != other.name || rules.size != other.rules.size || fractionRuleSet != other.fractionRuleSet) return false
        for (i in nonNumericalRules.indices) if (nonNumericalRules[i] != other.nonNumericalRules[i]) return false
        for (i in rules.indices) if (ruleAt(i) != other.ruleAt(i)) return false
        return true
    }
    override fun hashCode(): Int { assertRbnfRuleSetHash(); return 42 }
    override fun toString(): String = buildString {
        append(name).append(":\n")
        for (rule in rules) append(rule.toString()).append('\n')
        for (rule in nonNumericalRules) if (rule != null) {
            if (rule.getBaseValue() == -2L || rule.getBaseValue() == -3L || rule.getBaseValue() == -4L) {
                for (fractionRule in fractionRules!!) if (fractionRule.getBaseValue() == rule.getBaseValue()) append(fractionRule.toString()).append('\n')
            } else append(rule.toString()).append('\n')
        }
    }

    fun format(number: Long, output: StringBuilder?, position: Int, recursionCount: Int) {
        checkRecursion(recursionCount)
        val rule = findNormalRule(number)
        rule!!.doFormat(number, output, position, recursionCount + 1)
    }
    fun format(number: Double, output: StringBuilder?, position: Int, recursionCount: Int) {
        checkRecursion(recursionCount)
        val rule = findRule(number)
        rule!!.doFormat(number, output, position, recursionCount + 1)
    }
    fun findRule(input: Double): RbnfRule? {
        var number = input
        if (fractionRuleSet) return findFractionRuleSetRule(number)
        if (number.isNaN()) return nonNumericalRules[NAN_RULE_INDEX] ?: owner.getDefaultNaNRule()
        if (number < 0) {
            if (nonNumericalRules[NEGATIVE_RULE_INDEX] != null) return nonNumericalRules[NEGATIVE_RULE_INDEX]
            number = -number
        }
        if (number.isInfinite()) return nonNumericalRules[INFINITY_RULE_INDEX] ?: owner.getDefaultInfinityRule()
        if (number != floor(number)) {
            if (number < 1 && nonNumericalRules[PROPER_FRACTION_RULE_INDEX] != null) return nonNumericalRules[PROPER_FRACTION_RULE_INDEX]
            if (nonNumericalRules[IMPROPER_FRACTION_RULE_INDEX] != null) return nonNumericalRules[IMPROPER_FRACTION_RULE_INDEX]
        }
        return nonNumericalRules[DEFAULT_RULE_INDEX] ?: findNormalRule(RbnfMath.roundToLong(number))
    }
    private fun findNormalRule(input: Long): RbnfRule? {
        var number = input
        if (fractionRuleSet) return findFractionRuleSetRule(number.toDouble())
        if (number < 0L) {
            if (nonNumericalRules[NEGATIVE_RULE_INDEX] != null) return nonNumericalRules[NEGATIVE_RULE_INDEX]
            number = -number
        }
        var lo = 0
        var hi = rules.size
        if (hi > 0) {
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                val base = ruleAt(mid).getBaseValue()
                if (base == number) return ruleAt(mid)
                if (base > number) hi = mid else lo = mid + 1
            }
            if (hi == 0) throw IllegalStateException("The rule set $name cannot format the value $number")
            var result = ruleAt(hi - 1)
            if (result.shouldRollBack(number)) {
                if (hi == 1) throw IllegalStateException("The rule set $name cannot roll back from the rule '$result'")
                result = ruleAt(hi - 2)
            }
            return result
        }
        return nonNumericalRules[DEFAULT_RULE_INDEX]
    }
    private fun findFractionRuleSetRule(number: Double): RbnfRule {
        var leastCommonMultiple = ruleAt(0).getBaseValue()
        for (i in 1..<rules.size) leastCommonMultiple = lcm(leastCommonMultiple, ruleAt(i).getBaseValue())
        val numerator = RbnfMath.roundToLong(number * leastCommonMultiple)
        var difference = Long.MAX_VALUE
        var winner = 0
        for (i in rules.indices) {
            var tempDifference = RbnfMath.remainder(numerator * ruleAt(i).getBaseValue(), leastCommonMultiple)
            if (leastCommonMultiple - tempDifference < tempDifference) tempDifference = leastCommonMultiple - tempDifference
            if (tempDifference < difference) {
                difference = tempDifference
                winner = i
                if (difference == 0L) break
            }
        }
        if (winner + 1 < rules.size && ruleAt(winner + 1).getBaseValue() == ruleAt(winner).getBaseValue()) {
            if (RbnfMath.roundToLong(number * ruleAt(winner).getBaseValue()) < 1L ||
                RbnfMath.roundToLong(number * ruleAt(winner).getBaseValue()) >= 2L) ++winner
        }
        return ruleAt(winner)
    }
    private fun ruleAt(index: Int): RbnfRule {
        val current = rules
        if (index !in current.indices) throw IndexOutOfBoundsException()
        return current[index]
    }
    private fun checkRecursion(count: Int) {
        if (count >= RECURSION_LIMIT) throw IllegalStateException("Recursion limit exceeded when applying ruleSet $name")
    }
    fun parse(text: String?, position: ParsePosition?, upperBound: Double, initialMask: Int, recursionCount: Int): Number? {
        val highWaterMark = ParsePosition(0)
        var result: Number? = ZERO
        checkRecursion(recursionCount)
        if (text!!.isEmpty()) return result
        var mask = initialMask
        for (i in nonNumericalRules.indices) {
            val rule = nonNumericalRules[i]
            if (rule != null && ((mask shr i) and 1) == 0) {
                mask = mask or (1 shl i)
                val tempResult = rule.doParse(text, position, false, upperBound, mask, recursionCount + 1)
                if (position!!.index > highWaterMark.index) {
                    result = tempResult
                    highWaterMark.index = position.index
                }
                position.index = 0
            }
        }
        var i = rules.size - 1
        while (i >= 0 && highWaterMark.index < text.length) {
            if (!fractionRuleSet && ruleAt(i).getBaseValue() >= upperBound) { --i; continue }
            val tempResult = ruleAt(i).doParse(text, position, fractionRuleSet, upperBound, mask, recursionCount + 1)
            if (position!!.index > highWaterMark.index) {
                result = tempResult
                highWaterMark.index = position.index
            }
            position.index = 0
            --i
        }
        position!!.index = highWaterMark.index
        return result
    }
    fun setDecimalFormatSymbols(newSymbols: DecimalFormatSymbols?) {
        for (rule in rules) rule.setDecimalFormatSymbols(newSymbols)
        if (fractionRules != null) {
            for (i in IMPROPER_FRACTION_RULE_INDEX..DEFAULT_RULE_INDEX) if (nonNumericalRules[i] != null) {
                for (rule in fractionRules!!) {
                    if (nonNumericalRules[i]!!.getBaseValue() == rule.getBaseValue()) setBestFractionRule(i, rule, false)
                }
            }
        }
        for (rule in nonNumericalRules) if (rule != null) rule.setDecimalFormatSymbols(newSymbols)
    }
    companion object {
        const val NEGATIVE_RULE_INDEX = 0
        const val IMPROPER_FRACTION_RULE_INDEX = 1
        const val PROPER_FRACTION_RULE_INDEX = 2
        const val DEFAULT_RULE_INDEX = 3
        const val INFINITY_RULE_INDEX = 4
        const val NAN_RULE_INDEX = 5
        private const val RECURSION_LIMIT = 64
        internal val ZERO: Number = 0L
        internal fun lcm(x: Long, y: Long): Long {
            var x1 = x
            var y1 = y
            var p2 = 0
            while ((x1 and 1L) == 0L && (y1 and 1L) == 0L) { ++p2; x1 = x1 shr 1; y1 = y1 shr 1 }
            var t = if ((x1 and 1L) == 1L) -y1 else x1
            while (t != 0L) {
                while ((t and 1L) == 0L) t = t shr 1
                if (t > 0L) x1 = t else y1 = -t
                t = x1 - y1
            }
            val gcd = x1 shl p2
            return RbnfMath.divide(x, gcd) * y
        }
    }
}
internal expect fun assertRbnfRuleSetHash()
