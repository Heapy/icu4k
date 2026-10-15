// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// Port of ICU4J 78.3 NFSubstitution and its concrete substitutions.
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.math.BigDecimal
import kotlin.math.abs
import kotlin.math.floor

/** Services used by substitutions during the rule graph's two-phase construction. */
internal interface RbnfSubstitutionOwner : RbnfRuleOwner, RbnfRuleSetOwner {
    fun findRuleSet(name: String): RbnfRuleSetCore
    fun getDefaultRuleSet(): RbnfRuleSetCore?
    fun getDecimalFormat(): DecimalFormat
    fun getRoundingMode(): Int
}

internal abstract class RbnfSubstitutionCore(private val pos: Int, container: RbnfRuleSetCore?, input: String?) : RbnfSubstitution {
    internal val ruleSet: RbnfRuleSetCore?
    internal val numberFormat: DecimalFormat?
    init {
        var description = input!!
        val length = description.length
        if (length >= 2 && description[0] == description[length - 1]) description = description.subSlice(1, length - 1)
        else if (length != 0) throw IllegalArgumentException("Illegal substitution syntax")
        when {
            description.isEmpty() -> { ruleSet = container; numberFormat = null }
            description[0] == '%' -> { ruleSet = owner(container).findRuleSet(description); numberFormat = null }
            description[0] == '#' || description[0] == '0' -> {
                ruleSet = null
                numberFormat = owner(container).getDecimalFormat().clone()
                numberFormat.applyPattern(description)
            }
            description[0] == '>' -> { ruleSet = container; numberFormat = null }
            else -> throw IllegalArgumentException("Illegal substitution syntax")
        }
    }
    override fun getPos() = pos
    override fun setDivisor(radix: Int, exponent: Short) {}
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (this::class == other::class) {
            other as RbnfSubstitutionCore
            return pos == other.pos && (ruleSet != null || other.ruleSet == null) &&
                (if (numberFormat == null) other.numberFormat == null else numberFormat == other.numberFormat)
        }
        return false
    }
    override fun hashCode(): Int { assertRbnfSubstitutionHash(); return 42 }
    override fun toString(): String = if (ruleSet != null) "${tokenChar()}${ruleSet.getName()}${tokenChar()}"
        else "${tokenChar()}${numberFormat!!.toPattern()}${tokenChar()}"
    override fun doSubstitution(number: Long, output: StringBuilder?, position: Int, recursionCount: Int) {
        if (ruleSet != null) ruleSet.format(transformNumber(number), output, position + pos, recursionCount)
        else {
            val formatted = if (number <= MAX_INT64_IN_DOUBLE) {
                val transformed = transformNumber(number.toDouble())
                numberFormat!!.format(transformed)
            } else {
                val transformed = transformNumber(number)
                numberFormat!!.format(transformed)
            }
            output!!.insert(position + pos, formatted)
        }
    }
    override fun doSubstitution(number: Double, output: StringBuilder?, position: Int, recursionCount: Int) {
        val transformed = transformNumber(number)
        if (transformed.isInfinite()) {
            val infinite = ruleSet!!.findRule(Double.POSITIVE_INFINITY)
            infinite!!.doFormat(transformed, output, position + pos, recursionCount)
            return
        }
        if (transformed == floor(transformed) && ruleSet != null) ruleSet.format(transformed.toLong(), output, position + pos, recursionCount)
        else if (ruleSet != null) ruleSet.format(transformed, output, position + pos, recursionCount)
        else { val formatted = numberFormat!!.format(transformed); output!!.insert(position + pos, formatted) }
    }
    abstract fun transformNumber(number: Long): Long
    abstract fun transformNumber(number: Double): Double
    abstract fun composeRuleValue(newRuleValue: Double, oldRuleValue: Double): Double
    abstract fun calcUpperBound(oldUpperBound: Double): Double
    abstract fun tokenChar(): Char
    override fun doParse(text: String?, position: ParsePosition?, baseValue: Double, upperBound: Double,
                         lenient: Boolean, executedMask: Int, recursionCount: Int): Number? {
        val bound = calcUpperBound(upperBound)
        var result: Number?
        if (ruleSet != null) {
            result = ruleSet.parse(text, position, bound, executedMask, recursionCount)
            if (lenient && !ruleSet.isFractionSet() && position!!.index == 0) {
                result = owner(ruleSet).getDecimalFormat().parse(text, position)
            }
        } else result = numberFormat!!.parse(text, position)
        return if (position!!.index != 0) {
            boxResult(composeRuleValue(numberFormatDoubleValue(result!!), baseValue))
        } else result
    }
    override fun isModulusSubstitution() = false
    override fun setDecimalFormatSymbols(symbols: DecimalFormatSymbols?) { numberFormat?.setDecimalFormatSymbols(symbols) }
    companion object {
        private const val MAX_INT64_IN_DOUBLE = 0x1fffffffffffffL
        internal fun owner(set: RbnfRuleSetCore?): RbnfSubstitutionOwner = set!!.owner as RbnfSubstitutionOwner
        internal fun boxResult(value: Double): Number = if (value == value.toLong().toDouble()) value.toLong() else value
        fun makeSubstitution(position: Int, rule: RbnfRuleCore?, predecessor: RbnfRuleCore?, ruleSet: RbnfRuleSetCore?,
                             formatter: RbnfSubstitutionOwner?, description: String?): RbnfSubstitutionCore? {
            if (description!!.isEmpty()) return null
            return when (description[0]) {
                '<' -> when {
                    rule!!.getBaseValue() == -1L -> throw IllegalArgumentException("<< not allowed in negative-number rule")
                    rule.getBaseValue() in -4L..-2L -> RbnfIntegralPartSubstitution(position, ruleSet, description)
                    ruleSet!!.isFractionSet() -> RbnfNumeratorSubstitution(position, rule.getBaseValue().toDouble(), formatter!!.getDefaultRuleSet(), description)
                    else -> RbnfMultiplierSubstitution(position, rule, ruleSet, description)
                }
                '>' -> when {
                    rule!!.getBaseValue() == -1L -> RbnfAbsoluteValueSubstitution(position, ruleSet, description)
                    rule.getBaseValue() in -4L..-2L -> RbnfFractionalPartSubstitution(position, ruleSet, description)
                    ruleSet!!.isFractionSet() -> throw IllegalArgumentException(">> not allowed in fraction rule set")
                    else -> RbnfModulusSubstitutionCore(position, rule, predecessor, ruleSet, description)
                }
                '=' -> RbnfSameValueSubstitution(position, ruleSet, description)
                else -> throw IllegalArgumentException("Illegal substitution character")
            }
        }
    }
}

internal class RbnfSameValueSubstitution(position: Int, set: RbnfRuleSetCore?, description: String?) : RbnfSubstitutionCore(position, set, description) {
    init { if (description == "==") throw IllegalArgumentException("== is not a legal token") }
    override fun transformNumber(number: Long) = number
    override fun transformNumber(number: Double) = number
    override fun composeRuleValue(newRuleValue: Double, oldRuleValue: Double) = newRuleValue
    override fun calcUpperBound(oldUpperBound: Double) = oldUpperBound
    override fun tokenChar() = '='
}
internal class RbnfMultiplierSubstitution(position: Int, private val owningRule: RbnfRuleCore?, set: RbnfRuleSetCore?, description: String?) : RbnfSubstitutionCore(position, set, description) {
    private var divisor = owningRule!!.getDivisor()
    init {
        if (divisor == 0L) throw IllegalStateException("Substitution with divisor 0 ${description!!.subSlice(0, position)} | ${description.subSlice(position)}")
    }
    override fun setDivisor(radix: Int, exponent: Short) {
        divisor = RbnfRuleCore.power(radix.toLong(), exponent)
        if (divisor == 0L) throw IllegalStateException("Substitution with divisor 0")
    }
    override fun equals(other: Any?): Boolean = super.equals(other) && divisor == (other as RbnfMultiplierSubstitution).divisor
    override fun transformNumber(number: Long): Long = floor(RbnfMath.divide(number, divisor).toDouble()).toLong()
    override fun transformNumber(number: Double): Double =
        if (ruleSet != null || owningRule!!.hasModulusSubstitution() || (owningRule.formatter!! as RbnfSubstitutionOwner).getRoundingMode() == BigDecimal.ROUND_FLOOR) floor(number / divisor)
        else number / divisor
    override fun composeRuleValue(newRuleValue: Double, oldRuleValue: Double) = newRuleValue * divisor
    override fun calcUpperBound(oldUpperBound: Double) = divisor.toDouble()
    override fun tokenChar() = '<'
}
internal class RbnfModulusSubstitutionCore(position: Int, rule: RbnfRuleCore?, predecessor: RbnfRuleCore?, set: RbnfRuleSetCore?, description: String?) : RbnfSubstitutionCore(position, set, description), RbnfModulusSubstitution {
    private var divisor = rule!!.getDivisor()
    private val ruleToUse: RbnfRuleCore?
    init {
        if (divisor == 0L) throw IllegalStateException("Substitution with bad divisor ($divisor) ${description!!.subSlice(0, position)} | ${description.subSlice(position)}")
        ruleToUse = if (description == ">>>") predecessor else null
    }
    override fun setDivisor(radix: Int, exponent: Short) {
        divisor = RbnfRuleCore.power(radix.toLong(), exponent)
        if (divisor == 0L) throw IllegalStateException("Substitution with bad divisor")
    }
    override fun equals(other: Any?): Boolean = super.equals(other) && divisor == (other as RbnfModulusSubstitutionCore).divisor
    override fun doSubstitution(number: Long, output: StringBuilder?, position: Int, recursionCount: Int) {
        if (ruleToUse == null) super.doSubstitution(number, output, position, recursionCount)
        else ruleToUse.doFormat(transformNumber(number), output, position + getPos(), recursionCount)
    }
    override fun doSubstitution(number: Double, output: StringBuilder?, position: Int, recursionCount: Int) {
        if (ruleToUse == null) super.doSubstitution(number, output, position, recursionCount)
        else ruleToUse.doFormat(transformNumber(number), output, position + getPos(), recursionCount)
    }
    override fun transformNumber(number: Long) = RbnfMath.remainder(number, divisor)
    override fun transformNumber(number: Double) = number % divisor
    override fun doParse(text: String?, position: ParsePosition?, baseValue: Double, upperBound: Double, lenient: Boolean, executedMask: Int, recursionCount: Int): Number? {
        if (ruleToUse == null) return super.doParse(text, position, baseValue, upperBound, lenient, executedMask, recursionCount)
        val result = ruleToUse.doParse(text, position, false, upperBound, executedMask, recursionCount)
        return if (position!!.index != 0) boxResult(composeRuleValue(numberFormatDoubleValue(result), baseValue)) else result
    }
    override fun composeRuleValue(newRuleValue: Double, oldRuleValue: Double) = oldRuleValue - oldRuleValue % divisor + newRuleValue
    override fun calcUpperBound(oldUpperBound: Double) = divisor.toDouble()
    override fun isModulusSubstitution() = true
    override fun tokenChar() = '>'
}
internal class RbnfIntegralPartSubstitution(position: Int, set: RbnfRuleSetCore?, description: String?) : RbnfSubstitutionCore(position, set, description) {
    override fun transformNumber(number: Long) = number
    override fun transformNumber(number: Double) = floor(number)
    override fun composeRuleValue(newRuleValue: Double, oldRuleValue: Double) = newRuleValue + oldRuleValue
    override fun calcUpperBound(oldUpperBound: Double) = Double.MAX_VALUE
    override fun tokenChar() = '<'
}
internal class RbnfFractionalPartSubstitution(position: Int, set: RbnfRuleSetCore?, description: String?) : RbnfSubstitutionCore(position, set, description) {
    private val byDigits: Boolean
    private val useSpaces: Boolean
    init {
        if (description == ">>" || description == ">>>" || set === ruleSet) { byDigits = true; useSpaces = description != ">>>" }
        else {
            byDigits = false
            useSpaces = true
            if (ruleSet != null) ruleSet.makeIntoFractionRuleSet() else throw IllegalArgumentException("rule set is missing")
        }
    }
    override fun doSubstitution(number: Double, output: StringBuilder?, position: Int, recursionCount: Int) {
        if (!byDigits) super.doSubstitution(number, output, position, recursionCount)
        else {
            val quantity = DecimalQuantity_DualStorageBCD(number)
            quantity.roundToInfinity()
            var pad = false
            var magnitude = quantity.getLowerDisplayMagnitude()
            while (magnitude < 0) {
                if (pad && useSpaces) output!!.insert(position + getPos(), ' ') else pad = true
                ruleSet!!.format(quantity.getDigit(magnitude++).toLong(), output, position + getPos(), recursionCount)
            }
        }
    }
    override fun transformNumber(number: Long) = 0L
    override fun transformNumber(number: Double) = number - floor(number)
    override fun doParse(text: String?, position: ParsePosition?, baseValue: Double, upperBound: Double, lenient: Boolean, executedMask: Int, recursionCount: Int): Number? {
        if (!byDigits) return super.doParse(text, position, baseValue, 0.0, lenient, executedMask, recursionCount)
        var workText = text!!
        val workPos = ParsePosition(1)
        val quantity = DecimalQuantity_DualStorageBCD()
        var totalDigits = 0
        while (workText.isNotEmpty() && workPos.index != 0) {
            workPos.index = 0
            var digit = rbnfIntValue(ruleSet!!.parse(workText, workPos, 10.0, executedMask, recursionCount)!!)
            if (lenient && workPos.index == 0) {
                val n = owner(ruleSet).getDecimalFormat().parse(workText, workPos)
                if (n != null) digit = rbnfIntValue(n)
            }
            if (workPos.index != 0) {
                quantity.appendDigit(digit.toByte(), 0, true)
                ++totalDigits
                position!!.index += workPos.index
                workText = workText.subSlice(workPos.index)
                while (workText.isNotEmpty() && workText[0] == ' ') { workText = workText.subSlice(1); ++position.index }
            }
        }
        quantity.adjustMagnitude(-totalDigits)
        return composeRuleValue(quantity.toDouble(), baseValue)
    }
    override fun composeRuleValue(newRuleValue: Double, oldRuleValue: Double) = newRuleValue + oldRuleValue
    override fun calcUpperBound(oldUpperBound: Double) = 0.0
    override fun tokenChar() = '>'
}
internal class RbnfAbsoluteValueSubstitution(position: Int, set: RbnfRuleSetCore?, description: String?) : RbnfSubstitutionCore(position, set, description) {
    override fun transformNumber(number: Long) = abs(number)
    override fun transformNumber(number: Double) = abs(number)
    override fun composeRuleValue(newRuleValue: Double, oldRuleValue: Double) = -newRuleValue
    override fun calcUpperBound(oldUpperBound: Double) = Double.MAX_VALUE
    override fun tokenChar() = '>'
}
internal class RbnfNumeratorSubstitution(position: Int, private val denominator: Double, set: RbnfRuleSetCore?, description: String?) : RbnfSubstitutionCore(position, set, fixDescription(description)) {
    private val withZeros = description!!.endsWith("<<")
    override fun equals(other: Any?): Boolean = super.equals(other) && denominator == (other as RbnfNumeratorSubstitution).denominator && withZeros == other.withZeros
    override fun doSubstitution(number: Double, output: StringBuilder?, position: Int, recursionCount: Int) {
        val transformed = transformNumber(number)
        var position = position
        if (withZeros && ruleSet != null) {
            var n = transformed.toLong()
            val length = output!!.length
            while (true) {
                n *= 10
                if (!(n < denominator)) break
                output.insert(position + getPos(), ' ')
                ruleSet.format(0L, output, position + getPos(), recursionCount)
            }
            position += output.length - length
        }
        if (transformed == floor(transformed) && ruleSet != null) ruleSet.format(transformed.toLong(), output, position + getPos(), recursionCount)
        else if (ruleSet != null) ruleSet.format(transformed, output, position + getPos(), recursionCount)
        else { val formatted = numberFormat!!.format(transformed); output!!.insert(position + getPos(), formatted) }
    }
    override fun transformNumber(number: Long) = RbnfMath.roundToLong(number * denominator)
    override fun transformNumber(number: Double) = RbnfMath.roundToLong(number * denominator).toDouble()
    override fun doParse(text: String?, position: ParsePosition?, baseValue: Double, upperBound: Double, lenient: Boolean, executedMask: Int, recursionCount: Int): Number? {
        var zeroCount = 0
        var text = text
        if (withZeros) {
            var workText = text!!
            val workPos = ParsePosition(1)
            while (workText.isNotEmpty() && workPos.index != 0) {
                workPos.index = 0
                rbnfIntValue(ruleSet!!.parse(workText, workPos, 1.0, executedMask, recursionCount)!!)
                if (workPos.index == 0) break
                ++zeroCount
                position!!.index += workPos.index
                workText = workText.subSlice(workPos.index)
                while (workText.isNotEmpty() && workText[0] == ' ') { workText = workText.subSlice(1); ++position.index }
            }
            text = text.subSlice(position!!.index)
            position.index = 0
        }
        var result = super.doParse(text, position, if (withZeros) 1.0 else baseValue, upperBound, false, executedMask, recursionCount)
        if (withZeros) {
            val n = rbnfLongValue(result!!)
            var d = 1L
            while (d <= n) d *= 10
            while (zeroCount > 0) { d *= 10; --zeroCount }
            result = n.toDouble() / d.toDouble()
        }
        return result
    }
    override fun composeRuleValue(newRuleValue: Double, oldRuleValue: Double) = newRuleValue / oldRuleValue
    override fun calcUpperBound(oldUpperBound: Double) = denominator
    override fun tokenChar() = '<'
    companion object {
        fun fixDescription(description: String?): String = if (description!!.endsWith("<<")) description.subSlice(0, description.length - 1) else description
    }
}
private fun String.subSlice(start: Int, end: Int = length): String {
    if (start < 0 || end > length || start > end) throw IndexOutOfBoundsException()
    return substring(start, end)
}
internal expect fun assertRbnfSubstitutionHash()
internal expect fun rbnfIntValue(value: Number): Int
internal expect fun rbnfLongValue(value: Number): Long
