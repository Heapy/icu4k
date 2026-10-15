// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// Port of ICU4J 78.3 NFRule.
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.PatternProps
import kotlin.math.ln

internal interface RbnfSubstitution {
    fun getPos(): Int
    fun setDivisor(radix: Int, exponent: Short)
    fun isModulusSubstitution(): Boolean
    fun doSubstitution(number: Long, output: StringBuilder?, position: Int, recursionCount: Int)
    fun doSubstitution(number: Double, output: StringBuilder?, position: Int, recursionCount: Int)
    fun doParse(text: String?, position: ParsePosition?, baseValue: Double, upperBound: Double,
                lenient: Boolean, executedMask: Int, recursionCount: Int): Number?
    fun setDecimalFormatSymbols(symbols: DecimalFormatSymbols?)
}
/** Corresponds to the concrete ModulusSubstitution type check, distinct from its virtual query. */
internal interface RbnfModulusSubstitution : RbnfSubstitution

internal interface RbnfRuleOwner {
    fun makeSubstitution(position: Int, rule: RbnfRuleCore, predecessor: RbnfRuleCore?,
                         ruleSet: RbnfRuleSetCore, token: String): RbnfSubstitution?
    fun createPluralFormat(type: PluralRules.PluralType, pattern: String): PluralFormatCore
    fun lenientParseEnabled(): Boolean
    fun getLenientScanner(): RbnfLenientScanner?
}

internal class RbnfRuleCore(val formatter: RbnfRuleOwner?, description: String?) : RbnfRule {
    private var baseValue = 0L
    private var radix = 10
    private var exponent: Short = 0
    private var decimalPoint = '\u0000'
    private var ruleText: String? = null
    private var rulePatternFormat: PluralFormatCore? = null
    private var sub1: RbnfSubstitution? = null
    private var sub2: RbnfSubstitution? = null

    init { ruleText = if (description == null) null else parseRuleDescriptor(description) }

    private fun parseRuleDescriptor(input: String): String {
        var description = input
        var p = description.indexOf(':')
        if (p != -1) {
            val descriptor = description.ruleSubstring(0, p)
            ++p
            while (p < description.length && PatternProps.isWhiteSpace(description[p].code)) ++p
            description = description.ruleSubstring(p)
            val length = descriptor.length
            val first = charAt(descriptor, 0)
            val last = charAt(descriptor, length - 1)
            if (first in '0'..'9' && last != 'x') {
                var value = 0L
                var c = '\u0000'
                p = 0
                while (p < length) {
                    c = descriptor[p]
                    if (c in '0'..'9') value = value * 10 + (c - '0')
                    else if (c == '/' || c == '>') break
                    else if (!PatternProps.isWhiteSpace(c.code) && c != ',' && c != '.') {
                        throw IllegalArgumentException("Illegal character $c in rule descriptor")
                    }
                    ++p
                }
                setBaseValue(value)
                if (c == '/') {
                    value = 0
                    ++p
                    while (p < length) {
                        c = descriptor[p]
                        if (c in '0'..'9') value = value * 10 + (c - '0')
                        else if (c == '>') break
                        else if (!PatternProps.isWhiteSpace(c.code) && c != ',' && c != '.') {
                            throw IllegalArgumentException("Illegal character $c in rule descriptor")
                        }
                        ++p
                    }
                    radix = value.toInt()
                    if (radix == 0) throw IllegalArgumentException("Rule can't have radix of 0")
                    exponent = expectedExponent()
                }
                if (c == '>') {
                    while (p < length) {
                        c = descriptor[p]
                        if (c == '>' && exponent > 0) exponent = (exponent - 1).toShort()
                        else throw IllegalArgumentException("Illegal character in rule descriptor")
                        ++p
                    }
                }
            } else if (descriptor == "-x") setBaseValue(NEGATIVE_NUMBER_RULE.toLong())
            else if (length == 3) {
                when {
                    first == '0' && last == 'x' -> { setBaseValue(PROPER_FRACTION_RULE.toLong()); decimalPoint = descriptor[1] }
                    first == 'x' && last == 'x' -> { setBaseValue(IMPROPER_FRACTION_RULE.toLong()); decimalPoint = descriptor[1] }
                    first == 'x' && last == '0' -> { setBaseValue(DEFAULT_RULE.toLong()); decimalPoint = descriptor[1] }
                    descriptor == "NaN" -> setBaseValue(NAN_RULE.toLong())
                    descriptor == "Inf" -> setBaseValue(INFINITY_RULE.toLong())
                }
            }
        }
        if (description.isNotEmpty() && description[0] == '\'') description = description.ruleSubstring(1)
        return description
    }

    private fun extractSubstitutions(owner: RbnfRuleSetCore, text: String, predecessor: RbnfRuleCore?) {
        ruleText = text
        sub1 = extractSubstitution(owner, predecessor)
        sub2 = if (sub1 == null) null else extractSubstitution(owner, predecessor)
        val current = ruleText!!
        val start = current.indexOf("$(")
        val end = if (start >= 0) current.indexOf(")$", start) else -1
        if (end >= 0) {
            val endType = current.indexOf(',', start)
            if (endType < 0) throw IllegalArgumentException("Rule \"$current\" does not have a defined type")
            val typeName = current.ruleSubstring(start + 2, endType)
            val type = when (typeName) {
                "cardinal" -> PluralRules.PluralType.CARDINAL
                "ordinal" -> PluralRules.PluralType.ORDINAL
                else -> throw IllegalArgumentException("$typeName is an unknown type")
            }
            rulePatternFormat = formatter!!.createPluralFormat(type, current.ruleSubstring(endType + 1, end))
        }
    }
    private fun extractSubstitution(owner: RbnfRuleSetCore, predecessor: RbnfRuleCore?): RbnfSubstitution? {
        val current = ruleText!!
        var start = -1
        for (prefix in RULE_PREFIXES) {
            val pos = current.indexOf(prefix)
            if (pos != -1 && (start == -1 || pos < start)) start = pos
        }
        if (start == -1) return null
        var end: Int
        if (current.startsWith(">>>", start)) end = start + 2 else {
            val c = current[start]
            end = current.indexOf(c, start + 1)
            if (c == '<' && end != -1 && end < current.length - 1 && current[end + 1] == c) ++end
        }
        if (end == -1) return null
        val result = formatter!!.makeSubstitution(start, this, predecessor, owner, current.ruleSubstring(start, end + 1))
        ruleText = current.ruleSubstring(0, start) + current.ruleSubstring(end + 1)
        return result
    }
    override fun setBaseValue(value: Long) {
        baseValue = value
        radix = 10
        if (baseValue >= 1) {
            exponent = expectedExponent()
            sub1?.setDivisor(radix, exponent)
            sub2?.setDivisor(radix, exponent)
        } else exponent = 0
    }
    private fun expectedExponent(): Short {
        if (radix == 0 || baseValue < 1) return 0
        val result = (ln(baseValue.toDouble()) / ln(radix.toDouble())).toInt().toShort()
        return if (power(radix.toLong(), (result + 1).toShort()) <= baseValue) (result + 1).toShort() else result
    }
    override fun getDecimalPoint() = decimalPoint
    override fun getBaseValue() = baseValue
    fun getDivisor(): Long = power(radix.toLong(), exponent)
    fun hasModulusSubstitution(): Boolean = sub1 is RbnfModulusSubstitution || sub2 is RbnfModulusSubstitution
    override fun equals(other: Any?): Boolean = other is RbnfRuleCore && baseValue == other.baseValue &&
        radix == other.radix && exponent == other.exponent && ruleText!!.equals(other.ruleText) && sub1 == other.sub1 && sub2 == other.sub2
    override fun hashCode(): Int { assertRbnfRuleHash(); return 42 }
    override fun toString(): String = buildString {
        when (baseValue) {
            NEGATIVE_NUMBER_RULE.toLong() -> append("-x: ")
            IMPROPER_FRACTION_RULE.toLong() -> append('x').append(if (decimalPoint == '\u0000') '.' else decimalPoint).append("x: ")
            PROPER_FRACTION_RULE.toLong() -> append('0').append(if (decimalPoint == '\u0000') '.' else decimalPoint).append("x: ")
            DEFAULT_RULE.toLong() -> append('x').append(if (decimalPoint == '\u0000') '.' else decimalPoint).append("0: ")
            INFINITY_RULE.toLong() -> append("Inf: ")
            NAN_RULE.toLong() -> append("NaN: ")
            else -> {
                append(baseValue)
                if (radix != 10) append('/').append(radix)
                repeat((expectedExponent() - exponent).coerceAtLeast(0)) { append('>') }
                append(": ")
            }
        }
        val text = ruleText!!
        if (text.startsWith(" ") && (sub1 == null || sub1!!.getPos() != 0)) append('\'')
        val copy = StringBuilder(text)
        sub2?.let { copy.insert(it.getPos(), it.toString()) }
        sub1?.let { copy.insert(it.getPos(), it.toString()) }
        append(copy).append(';')
    }

    override fun doFormat(number: Long, output: StringBuilder?, position: Int, recursionCount: Int) {
        val text = ruleText!!
        var pluralStart = text.length
        var lengthOffset = 0
        val plural = rulePatternFormat
        if (plural == null) output!!.insert(position, text) else {
            pluralStart = text.indexOf("$(")
            val end = text.indexOf(")$", pluralStart)
            val initialLength = output!!.length
            if (end < text.length - 1) output.insert(position, text.ruleSubstring(end + 2))
            output.insert(position, plural.format(RbnfMath.divide(number, getDivisor()).toDouble()))
            if (pluralStart > 0) output.insert(position, text.ruleSubstring(0, pluralStart))
            lengthOffset = text.length - (output.length - initialLength)
        }
        sub2?.let { it.doSubstitution(number, output, position - (if (it.getPos() > pluralStart) lengthOffset else 0), recursionCount) }
        sub1?.let { it.doSubstitution(number, output, position - (if (it.getPos() > pluralStart) lengthOffset else 0), recursionCount) }
    }
    override fun doFormat(number: Double, output: StringBuilder?, position: Int, recursionCount: Int) {
        val text = ruleText!!
        var pluralStart = text.length
        var lengthOffset = 0
        val plural = rulePatternFormat
        if (plural == null) output!!.insert(position, text) else {
            pluralStart = text.indexOf("$(")
            val end = text.indexOf(")$", pluralStart)
            val initialLength = output!!.length
            if (end < text.length - 1) output.insert(position, text.ruleSubstring(end + 2))
            val pluralValue = if (number >= 0 && number < 1) RbnfMath.roundToLong(number * getDivisor()).toDouble() else number / getDivisor()
            output.insert(position, plural.format(pluralValue.toLong().toDouble()))
            if (pluralStart > 0) output.insert(position, text.ruleSubstring(0, pluralStart))
            lengthOffset = text.length - (output.length - initialLength)
        }
        sub2?.let { it.doSubstitution(number, output, position - (if (it.getPos() > pluralStart) lengthOffset else 0), recursionCount) }
        sub1?.let { it.doSubstitution(number, output, position - (if (it.getPos() > pluralStart) lengthOffset else 0), recursionCount) }
    }
    override fun shouldRollBack(number: Long): Boolean {
        if (!(sub1?.isModulusSubstitution() == true || sub2?.isModulusSubstitution() == true)) return false
        val divisor = getDivisor()
        return RbnfMath.remainder(number, divisor) == 0L && RbnfMath.remainder(baseValue, divisor) != 0L
    }
    override fun doParse(text: String?, position: ParsePosition?, fractionRule: Boolean, upperBound: Double,
                         executedMask: Int, recursionCount: Int): Number {
        val pp = ParsePosition(0)
        val current = ruleText!!
        val sub1Pos = sub1?.getPos() ?: current.length
        val sub2Pos = sub2?.getPos() ?: current.length
        val workText = stripPrefix(text, current.ruleSubstring(0, sub1Pos), pp)
        val prefixLength = text!!.length - workText!!.length
        if (pp.index == 0 && sub1Pos != 0) return ZERO
        if (baseValue == INFINITY_RULE.toLong()) { position!!.index = pp.index; return Double.POSITIVE_INFINITY }
        if (baseValue == NAN_RULE.toLong()) { position!!.index = pp.index; return Double.NaN }
        var highWaterMark = 0
        var result = 0.0
        var start = 0
        val tempBaseValue = maxOf(0L, baseValue).toDouble()
        do {
            pp.index = 0
            var partial = numberFormatDoubleValue(matchToDelimiter(workText, start, tempBaseValue,
                current.ruleSubstring(sub1Pos, sub2Pos), rulePatternFormat, pp, sub1, upperBound, executedMask, recursionCount)!!)
            if (pp.index != 0 || sub1 == null) {
                start = pp.index
                val workText2 = workText.ruleSubstring(pp.index)
                val pp2 = ParsePosition(0)
                partial = numberFormatDoubleValue(matchToDelimiter(workText2, 0, partial, current.ruleSubstring(sub2Pos),
                    rulePatternFormat, pp2, sub2, upperBound, executedMask, recursionCount)!!)
                if (pp2.index != 0 || sub2 == null) {
                    if (prefixLength + pp.index + pp2.index > highWaterMark) {
                        highWaterMark = prefixLength + pp.index + pp2.index
                        result = partial
                    }
                }
            }
        } while (sub1Pos != sub2Pos && pp.index > 0 && pp.index < workText.length && pp.index != start)
        position!!.index = highWaterMark
        if (fractionRule && highWaterMark > 0 && sub1 == null) result = 1 / result
        return if (result == result.toLong().toDouble()) result.toLong() else result
    }
    private fun stripPrefix(text: String?, prefix: String, pp: ParsePosition): String? {
        if (prefix.isEmpty()) return text
        val length = prefixLength(text, prefix)
        if (length != 0) { pp.index += length; return text!!.ruleSubstring(length) }
        return text
    }
    private fun matchToDelimiter(text: String, start: Int, base: Double, delimiter: String, plural: PluralFormatCore?,
                                 pp: ParsePosition, sub: RbnfSubstitution?, upperBound: Double, mask: Int, recursion: Int): Number? {
        if (!allIgnorable(delimiter)) {
            val tempPP = ParsePosition(0)
            var found = findText(text, delimiter, plural, start)!!
            var dPos = arrayAt(found, 0)
            var dLen = arrayAt(found, 1)
            while (dPos >= 0) {
                val subText = text.ruleSubstring(0, dPos)
                if (subText.isNotEmpty()) {
                    val value = sub!!.doParse(subText, tempPP, base, upperBound, formatter!!.lenientParseEnabled(), mask, recursion)
                    if (tempPP.index == dPos) { pp.index = dPos + dLen; return value }
                }
                tempPP.index = 0
                found = findText(text, delimiter, plural, dPos + dLen)!!
                dPos = arrayAt(found, 0)
                dLen = arrayAt(found, 1)
            }
            pp.index = 0
            return ZERO
        } else if (sub == null) return base
        else {
            val tempPP = ParsePosition(0)
            var result: Number = ZERO
            val value = sub.doParse(text, tempPP, base, upperBound, formatter!!.lenientParseEnabled(), mask, recursion)
            if (tempPP.index != 0) { pp.index = tempPP.index; if (value != null) result = value }
            return result
        }
    }
    private fun prefixLength(text: String?, prefix: String): Int {
        if (prefix.isEmpty()) return 0
        val scanner = formatter!!.getLenientScanner()
        if (scanner != null) {
            if (text!!.startsWith(prefix)) return prefix.length
            return scanner.prefixLength(text, prefix)
        }
        return if (text!!.startsWith(prefix)) prefix.length else 0
    }
    private fun findText(text: String, key: String, plural: PluralFormatCore?, startAt: Int): IntArray? {
        val scanner = formatter!!.getLenientScanner()
        if (plural != null) {
            val position = FormatFieldPosition(NumberFormat.INTEGER_FIELD)
            position.setBeginIndex(startAt)
            plural.parseType(text, scanner, position)
            val start = position.getBeginIndex()
            if (start >= 0) {
                val current = ruleText!!
                val pluralStart = current.indexOf("$(")
                val pluralSuffix = current.indexOf(")$", pluralStart) + 2
                val length = position.getEndIndex() - start
                val prefix = current.ruleSubstring(0, pluralStart)
                val suffix = current.ruleSubstring(pluralSuffix)
                if (text.regionMatches(start - prefix.length, prefix, 0, prefix.length) &&
                    text.regionMatches(start + length, suffix, 0, suffix.length)) {
                    return intArrayOf(start - prefix.length, length + prefix.length + suffix.length)
                }
            }
            return intArrayOf(-1, 0)
        }
        val result = intArrayOf(text.indexOf(key, startAt), key.length)
        return if (scanner != null && result[0] < 0) scanner.findText(text, key, startAt) else result
    }
    private fun allIgnorable(text: String?): Boolean {
        if (text == null || text.isEmpty()) return true
        val scanner = formatter!!.getLenientScanner()
        return scanner != null && scanner.allIgnorable(text)
    }
    override fun setDecimalFormatSymbols(symbols: DecimalFormatSymbols?) {
        sub1?.setDecimalFormatSymbols(symbols)
        sub2?.setDecimalFormatSymbols(symbols)
    }

    companion object {
        const val NEGATIVE_NUMBER_RULE = -1
        const val IMPROPER_FRACTION_RULE = -2
        const val PROPER_FRACTION_RULE = -3
        const val DEFAULT_RULE = -4
        const val INFINITY_RULE = -5
        const val NAN_RULE = -6
        private val ZERO: Number = 0L
        private val RULE_PREFIXES = arrayOf("<<", "<%", "<#", "<0", ">>", ">%", ">#", ">0", "=%", "=#", "=0")
        private fun charAt(text: String, index: Int): Char {
            if (index !in text.indices) throw IndexOutOfBoundsException()
            return text[index]
        }
        private fun arrayAt(array: IntArray, index: Int): Int {
            if (index !in array.indices) throw IndexOutOfBoundsException()
            return array[index]
        }
        fun power(inputBase: Long, inputExponent: Short): Long {
            var base = inputBase
            var exponent = inputExponent.toInt()
            if (exponent < 0) throw IllegalArgumentException("Exponent can not be negative")
            if (base < 0) throw IllegalArgumentException("Base can not be negative")
            var result = 1L
            while (exponent > 0) {
                if ((exponent and 1) == 1) result *= base
                base *= base
                exponent = exponent shr 1
            }
            return result
        }
        fun makeRules(description: String?, owner: RbnfRuleSetCore, predecessor: RbnfRuleCore?,
                      formatter: RbnfRuleOwner?, returnList: MutableList<RbnfRule>) {
            val rule1 = RbnfRuleCore(formatter, description)
            val text = rule1.ruleText!!
            val brack1 = text.indexOf('[')
            val brack2 = if (brack1 < 0) -1 else text.indexOf(']')
            if (brack2 < 0 || brack1 > brack2 || rule1.baseValue == PROPER_FRACTION_RULE.toLong() ||
                rule1.baseValue == NEGATIVE_NUMBER_RULE.toLong() || rule1.baseValue == INFINITY_RULE.toLong() || rule1.baseValue == NAN_RULE.toLong()) {
                rule1.extractSubstitutions(owner, text, predecessor)
            } else {
                var rule2: RbnfRuleCore? = null
                val buffer = StringBuilder()
                val orElse = text.indexOf('|')
                val mod = rule1.getDivisor()
                if (rule1.baseValue > 0 && mod == 0L) throw IllegalArgumentException("value out of range")
                if ((rule1.baseValue > 0 && RbnfMath.remainder(rule1.baseValue, mod) == 0L) ||
                    rule1.baseValue == IMPROPER_FRACTION_RULE.toLong() || rule1.baseValue == DEFAULT_RULE.toLong()) {
                    rule2 = RbnfRuleCore(formatter, null)
                    when {
                        rule1.baseValue >= 0 -> { rule2.baseValue = rule1.baseValue; if (!owner.isFractionSet()) ++rule1.baseValue }
                        rule1.baseValue == IMPROPER_FRACTION_RULE.toLong() -> rule2.baseValue = PROPER_FRACTION_RULE.toLong()
                        rule1.baseValue == DEFAULT_RULE.toLong() -> { rule2.baseValue = rule1.baseValue; rule1.baseValue = IMPROPER_FRACTION_RULE.toLong() }
                    }
                    rule2.radix = rule1.radix
                    rule2.exponent = rule1.exponent
                    buffer.append(text.ruleSubstring(0, brack1))
                    if (orElse >= 0) buffer.append(text.ruleSubstring(orElse + 1, brack2))
                    if (brack2 + 1 < text.length) buffer.append(text.ruleSubstring(brack2 + 1, text.length))
                    rule2.extractSubstitutions(owner, buffer.toString(), predecessor)
                }
                buffer.setLength(0)
                buffer.append(text.ruleSubstring(0, brack1))
                buffer.append(text.ruleSubstring(brack1 + 1, if (orElse >= 0) orElse else brack2))
                if (brack2 + 1 < text.length) buffer.append(text.ruleSubstring(brack2 + 1))
                rule1.extractSubstitutions(owner, buffer.toString(), predecessor)
                if (rule2 != null) {
                    if (rule2.baseValue >= 0) returnList.add(rule2) else owner.setNonNumericalRule(rule2)
                }
            }
            if (rule1.baseValue >= 0) returnList.add(rule1) else owner.setNonNumericalRule(rule1)
        }
    }
}
internal expect fun assertRbnfRuleHash()

private fun String.ruleSubstring(start: Int, end: Int = length): String {
    if (start < 0 || end > length || start > end) throw IndexOutOfBoundsException()
    return substring(start, end)
}
