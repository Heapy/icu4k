// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.number.Padder.PadPosition
import io.github.heapy.icu4k.impl.units.UnitDecimal
import io.github.heapy.icu4k.util.decimalFromValue

/** Recursive-descent parser with raw and public decimal-property projection. */
internal object PatternStringParserCore {
    const val IGNORE_ROUNDING_NEVER = 0
    const val IGNORE_ROUNDING_IF_CURRENCY = 1
    const val IGNORE_ROUNDING_ALWAYS = 2
    typealias ParsedPatternInfo = PatternStringParser.ParsedPatternInfo
    typealias ParsedSubpatternInfo = PatternStringParser.ParsedSubpatternInfo
    class PatternProperties {
        var groupingSize = -1
        var groupingUsed = true
        var secondaryGroupingSize = -1
        var minimumFractionDigits = -1
        var maximumFractionDigits = -1
        var roundingIncrement: UnitDecimal? = null
        var minimumSignificantDigits = -1
        var maximumSignificantDigits = -1
        var decimalSeparatorAlwaysShown = false
        var currencyAsDecimal = false
        var exponentSignAlwaysShown = false
        var minimumExponentDigits = -1
        var minimumIntegerDigits = -1
        var maximumIntegerDigits = -1
        var formatWidth = -1
        var padString: String? = null
        var padPosition: PadPosition? = null
        var positivePrefixPattern: String? = null
        var positiveSuffixPattern: String? = null
        var negativePrefixPattern: String? = null
        var negativeSuffixPattern: String? = null
        var magnitudeMultiplier = 0
    }
    private class State(val pattern: String?) {
        var offset = 0
        fun peek(): Int = if (offset == pattern!!.length) -1 else cpAt(pattern, offset)
        fun peek2(): Int {
            if (offset == pattern!!.length) return -1
            val next = offset + charCount(cpAt(pattern, offset))
            return if (next == pattern.length) -1 else cpAt(pattern, next)
        }
        fun next(): Int = peek().also { offset += charCount(it) }
        fun error(message: String): Nothing = throw IllegalArgumentException("Malformed pattern for ICU DecimalFormat: \"$pattern\": $message at position $offset")
    }
    fun parseToPatternInfo(pattern: String?): ParsedPatternInfo {
        val state = State(pattern)
        val result = ParsedPatternInfo.create(pattern)
        result.positive = ParsedSubpatternInfo().also { consumeSubpattern(state, it) }
        if (state.peek() == 59) {
            state.next()
            if (state.peek() != -1) result.negative = ParsedSubpatternInfo().also { consumeSubpattern(state, it) }
        }
        if (state.peek() != -1) state.error("Found unquoted special character")
        return result
    }
    private fun consumeSubpattern(state: State, result: ParsedSubpatternInfo) {
        consumePadding(state, result, PadPosition.BEFORE_PREFIX)
        result.prefixEndpoints = consumeAffix(state, result)
        consumePadding(state, result, PadPosition.AFTER_PREFIX)
        consumeFormat(state, result)
        consumeExponent(state, result)
        consumePadding(state, result, PadPosition.BEFORE_SUFFIX)
        result.suffixEndpoints = consumeAffix(state, result)
        consumePadding(state, result, PadPosition.AFTER_SUFFIX)
    }
    private fun consumePadding(state: State, result: ParsedSubpatternInfo, location: PadPosition) {
        if (state.peek() != 42) return
        if (result.paddingLocation != null) state.error("Cannot have multiple pad specifiers")
        result.paddingLocation = location
        state.next()
        result.paddingEndpoints = result.paddingEndpoints or state.offset.toLong()
        consumeLiteral(state)
        result.paddingEndpoints = result.paddingEndpoints or (state.offset.toLong() shl 32)
    }
    private fun consumeAffix(state: State, result: ParsedSubpatternInfo): Long {
        var endpoints = state.offset.toLong()
        while (true) {
            when (state.peek()) {
                35, 64, 59, 42, 46, 44, in 48..57, -1 -> break
                37 -> result.hasPercentSign = true
                0x2030 -> result.hasPerMilleSign = true
                0xa4 -> result.hasCurrencySign = true
                45 -> result.hasMinusSign = true
                43 -> result.hasPlusSign = true
            }
            consumeLiteral(state)
        }
        endpoints = endpoints or (state.offset.toLong() shl 32)
        return endpoints
    }
    private fun consumeLiteral(state: State) {
        if (state.peek() == -1) state.error("Expected unquoted literal but found EOL")
        else if (state.peek() == 39) {
            state.next()
            while (state.peek() != 39) {
                if (state.peek() == -1) state.error("Expected quoted literal but found EOL") else state.next()
            }
            state.next()
        } else state.next()
    }
    private fun consumeFormat(state: State, result: ParsedSubpatternInfo) {
        consumeInteger(state, result)
        if (state.peek() == 46) {
            state.next(); result.hasDecimal = true; result.widthExceptAffixes++
            consumeFraction(state, result)
        } else if (state.peek() == 0xa4) {
            val next = state.peek2()
            if (next != 35 && next !in 48..57) return
            result.hasCurrencySign = true; result.hasCurrencyDecimal = true; result.hasDecimal = true
            result.widthExceptAffixes++; state.next(); consumeFraction(state, result)
        }
    }
    private fun consumeInteger(state: State, result: ParsedSubpatternInfo) {
        while (true) {
            when (state.peek()) {
                44 -> { result.widthExceptAffixes++; result.groupingSizes = result.groupingSizes shl 16 }
                35 -> {
                    if (result.integerNumerals > 0) state.error("# cannot follow 0 before decimal point")
                    result.widthExceptAffixes++; result.groupingSizes++
                    if (result.integerAtSigns > 0) result.integerTrailingHashSigns++ else result.integerLeadingHashSigns++
                    result.integerTotal++
                }
                64 -> {
                    if (result.integerNumerals > 0) state.error("Cannot mix 0 and @")
                    if (result.integerTrailingHashSigns > 0) state.error("Cannot nest # inside of a run of @")
                    result.widthExceptAffixes++; result.groupingSizes++; result.integerAtSigns++; result.integerTotal++
                }
                in 48..57 -> {
                    if (result.integerAtSigns > 0) state.error("Cannot mix @ and 0")
                    result.widthExceptAffixes++; result.groupingSizes++; result.integerNumerals++; result.integerTotal++
                    if (state.peek() != 48 && result.rounding == null) result.rounding = DecimalQuantity_DualStorageBCD()
                    result.rounding?.appendDigit((state.peek() - 48).toByte(), 0, true)
                }
                else -> break
            }
            state.next()
        }
        val g1 = result.groupingSizes.toShort().toInt()
        val g2 = (result.groupingSizes ushr 16).toShort().toInt()
        val g3 = (result.groupingSizes ushr 32).toShort().toInt()
        if (g1 == 0 && g2 != -1) state.error("Trailing grouping separator is invalid")
        if (g2 == 0 && g3 != -1) state.error("Grouping width of zero is invalid")
    }
    private fun consumeFraction(state: State, result: ParsedSubpatternInfo) {
        var zeroCounter = 0
        while (true) {
            when (state.peek()) {
                35 -> { result.widthExceptAffixes++; result.fractionHashSigns++; result.fractionTotal++; zeroCounter++ }
                in 48..57 -> {
                    if (result.fractionHashSigns > 0) state.error("0 cannot follow # after decimal point")
                    result.widthExceptAffixes++; result.fractionNumerals++; result.fractionTotal++
                    if (state.peek() == 48) zeroCounter++ else {
                        if (result.rounding == null) result.rounding = DecimalQuantity_DualStorageBCD()
                        result.rounding!!.appendDigit((state.peek() - 48).toByte(), zeroCounter, false)
                        zeroCounter = 0
                    }
                }
                else -> return
            }
            state.next()
        }
    }
    private fun consumeExponent(state: State, result: ParsedSubpatternInfo) {
        if (state.peek() != 69) return
        if ((result.groupingSizes and 0xffff0000L) != 0xffff0000L) state.error("Cannot have grouping separator in scientific notation")
        state.next(); result.widthExceptAffixes++
        if (state.peek() == 43) { state.next(); result.exponentHasPlusSign = true; result.widthExceptAffixes++ }
        while (state.peek() == 48) { state.next(); result.exponentZeros++; result.widthExceptAffixes++ }
    }
    fun parseToProperties(pattern: String?, ignoreRounding: Int = IGNORE_ROUNDING_NEVER): PatternProperties {
        if (pattern == null || pattern.isEmpty()) return PatternProperties()
        return toProperties(parseToPatternInfo(pattern), ignoreRounding)
    }
    fun parseToDecimalFormatProperties(pattern: String?, ignoreRounding: Int = IGNORE_ROUNDING_NEVER): DecimalFormatProperties =
        DecimalFormatProperties().also { parseToExistingProperties(pattern, it, ignoreRounding) }
    fun parseToExistingProperties(pattern: String?, properties: DecimalFormatProperties?, ignoreRounding: Int = IGNORE_ROUNDING_NEVER) {
        if (pattern.isNullOrEmpty()) { properties!!.clear(); return }
        val values = toProperties(parseToPatternInfo(pattern), ignoreRounding)
        properties!!.setGroupingSize(values.groupingSize)
        properties.setGroupingUsed(values.groupingUsed)
        properties.setSecondaryGroupingSize(values.secondaryGroupingSize)
        properties.setMinimumFractionDigits(values.minimumFractionDigits)
        properties.setMaximumFractionDigits(values.maximumFractionDigits)
        properties.setRoundingIncrement(values.roundingIncrement?.let { decimalFromValue(it) })
        properties.setMinimumSignificantDigits(values.minimumSignificantDigits)
        properties.setMaximumSignificantDigits(values.maximumSignificantDigits)
        properties.setDecimalSeparatorAlwaysShown(values.decimalSeparatorAlwaysShown)
        properties.setCurrencyAsDecimal(values.currencyAsDecimal)
        properties.setExponentSignAlwaysShown(values.exponentSignAlwaysShown)
        properties.setMinimumExponentDigits(values.minimumExponentDigits)
        properties.setMinimumIntegerDigits(values.minimumIntegerDigits)
        properties.setMaximumIntegerDigits(values.maximumIntegerDigits)
        properties.setFormatWidth(values.formatWidth)
        properties.setPadString(values.padString)
        properties.setPadPosition(values.padPosition)
        properties.setPositivePrefixPattern(values.positivePrefixPattern)
        properties.setPositiveSuffixPattern(values.positiveSuffixPattern)
        properties.setNegativePrefixPattern(values.negativePrefixPattern)
        properties.setNegativeSuffixPattern(values.negativeSuffixPattern)
        properties.setMagnitudeMultiplier(values.magnitudeMultiplier)
    }
    fun toProperties(info: ParsedPatternInfo, ignoreRounding: Int): PatternProperties {
        val p = info.positive!!
        val ignore = when (ignoreRounding) {
            IGNORE_ROUNDING_NEVER -> false
            IGNORE_ROUNDING_IF_CURRENCY -> p.hasCurrencySign
            else -> { assertPatternStringParser(ignoreRounding == IGNORE_ROUNDING_ALWAYS); true }
        }
        return PatternProperties().apply {
            val g1 = p.groupingSizes.toShort().toInt()
            val g2 = (p.groupingSizes ushr 16).toShort().toInt()
            val g3 = (p.groupingSizes ushr 32).toShort().toInt()
            groupingSize = if (g2 != -1) g1 else -1; groupingUsed = g2 != -1
            secondaryGroupingSize = if (g3 != -1) g2 else -1
            val minInt: Int
            val minFrac: Int
            if (p.integerTotal == 0 && p.fractionTotal > 0) { minInt = 0; minFrac = maxOf(1, p.fractionNumerals) }
            else if (p.integerNumerals == 0 && p.fractionNumerals == 0) { minInt = 1; minFrac = 0 }
            else { minInt = p.integerNumerals; minFrac = p.fractionNumerals }
            if (p.integerAtSigns > 0) {
                minimumSignificantDigits = p.integerAtSigns
                maximumSignificantDigits = p.integerAtSigns + p.integerTrailingHashSigns
            } else if (!ignore) {
                minimumFractionDigits = minFrac; maximumFractionDigits = p.fractionTotal
                roundingIncrement = p.rounding?.core?.toDecimal()?.setScale(p.fractionNumerals, UnitDecimal.Rounding.UNNECESSARY)
            }
            decimalSeparatorAlwaysShown = p.hasDecimal && p.fractionTotal == 0
            currencyAsDecimal = p.hasCurrencyDecimal
            if (p.exponentZeros > 0) {
                exponentSignAlwaysShown = p.exponentHasPlusSign; minimumExponentDigits = p.exponentZeros
                if (p.integerAtSigns == 0) { minimumIntegerDigits = p.integerNumerals; maximumIntegerDigits = p.integerTotal }
                else { minimumIntegerDigits = 1; maximumIntegerDigits = -1 }
            } else minimumIntegerDigits = minInt
            positivePrefixPattern = info.getString(AffixPatternProvider.Flags.PREFIX)
            positiveSuffixPattern = info.getString(0)
            if (p.paddingLocation != null) {
                formatWidth = p.widthExceptAffixes + AffixUtils.estimateLength(positivePrefixPattern) + AffixUtils.estimateLength(positiveSuffixPattern)
                val raw = info.getString(AffixPatternProvider.Flags.PADDING)
                padString = when (raw.length) { 1 -> raw; 2 -> if (raw[0] == '\'') "'" else raw; else -> raw.substring(1, raw.length - 1) }
                padPosition = p.paddingLocation
            }
            if (info.negative != null) {
                negativePrefixPattern = info.getString(AffixPatternProvider.FLAG_NEG_PREFIX)
                negativeSuffixPattern = info.getString(AffixPatternProvider.FLAG_NEG_SUFFIX)
            }
            magnitudeMultiplier = if (p.hasPercentSign) 2 else if (p.hasPerMilleSign) 3 else 0
        }
    }
    private fun charCount(cp: Int): Int = if (cp >= 0x10000) 2 else 1
    private fun cpAt(text: String, offset: Int): Int {
        val first = text[offset]
        if (first.isHighSurrogate() && offset + 1 < text.length) {
            val second = text[offset + 1]
            if (second.isLowSurrogate()) return 0x10000 + ((first.code - 0xd800) shl 10) + second.code - 0xdc00
        }
        return first.code
    }
}
internal expect fun assertPatternStringParser(condition: Boolean)
