// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StaticUnicodeSets
import io.github.heapy.icu4k.impl.StaticUnicodeSets.Key
import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.impl.stringSegmentCodePointAt
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.impl.number.Grouper
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.jvm.JvmStatic

open class DecimalMatcher private constructor(symbols: DecimalFormatSymbols?, grouper: Grouper?, parseFlags: Int) : NumberParseMatcher {
    private val groupingSeparator: String?
    private val decimalSeparator: String?
    private val groupingUniSet: UnicodeSet
    private val decimalUniSet: UnicodeSet
    private val separatorSet: UnicodeSet
    private val leadSet: UnicodeSet?
    private val digitStrings: Array<String>?
    private val requireGroupingMatch: Boolean
    private val groupingDisabled: Boolean
    private val integerOnly: Boolean
    private val grouping1: Int
    private val grouping2: Int
    init {
        if (parseFlags and ParsingUtils.PARSE_FLAG_MONETARY_SEPARATORS != 0) {
            groupingSeparator = symbols!!.getMonetaryGroupingSeparatorString()
            decimalSeparator = symbols.getMonetaryDecimalSeparatorString()
        } else {
            groupingSeparator = symbols!!.getGroupingSeparatorString()
            decimalSeparator = symbols.getDecimalSeparatorString()
        }
        val strict = parseFlags and ParsingUtils.PARSE_FLAG_STRICT_SEPARATORS != 0
        var groupingKey = StaticUnicodeSets.chooseFrom(groupingSeparator, if (strict) Key.STRICT_COMMA else Key.ALL_SEPARATORS, if (strict) Key.STRICT_PERIOD else Key.ALL_SEPARATORS)
        if (groupingKey == null) groupingKey = StaticUnicodeSets.chooseFrom(groupingSeparator, Key.OTHER_GROUPING_SEPARATORS)
        groupingUniSet = if (groupingKey != null) StaticUnicodeSets.get(groupingKey) else if (groupingSeparator!!.isNotEmpty()) UnicodeSet().add(stringSegmentCodePointAt(groupingSeparator!!, 0)).freeze() else UnicodeSet.EMPTY
        val decimalKey = StaticUnicodeSets.chooseFrom(decimalSeparator, if (strict) Key.STRICT_COMMA else Key.COMMA, if (strict) Key.STRICT_PERIOD else Key.PERIOD)
        decimalUniSet = if (decimalKey != null) StaticUnicodeSets.get(decimalKey) else if (decimalSeparator!!.isNotEmpty()) UnicodeSet().add(stringSegmentCodePointAt(decimalSeparator!!, 0)).freeze() else UnicodeSet.EMPTY
        if (groupingKey != null && decimalKey != null) {
            separatorSet = groupingUniSet
            // Deliberately follows ICU4J's ordering of the two cached lead sets.
            leadSet = StaticUnicodeSets.get(if (strict) Key.DIGITS_OR_ALL_SEPARATORS else Key.DIGITS_OR_STRICT_ALL_SEPARATORS)
        } else {
            separatorSet = UnicodeSet().addAll(groupingUniSet).addAll(decimalUniSet).freeze()
            leadSet = null
        }
        val zero = symbols.getCodePointZero()
        digitStrings = if (zero == -1 || !UCharacter.isDigit(zero) || UCharacter.digit(zero) != 0) symbols.getDigitStringsLocal() else null
        requireGroupingMatch = parseFlags and ParsingUtils.PARSE_FLAG_STRICT_GROUPING_SIZE != 0
        groupingDisabled = parseFlags and ParsingUtils.PARSE_FLAG_GROUPING_DISABLED != 0
        integerOnly = parseFlags and ParsingUtils.PARSE_FLAG_INTEGER_ONLY != 0
        grouping1 = grouper!!.getPrimary().toInt()
        grouping2 = grouper.getSecondary().toInt()
    }
    override fun match(segment: StringSegment?, result: ParsedNumber?): Boolean = match(segment, result, 0)
    open fun match(segment: StringSegment?, result: ParsedNumber?, exponentSign: Int): Boolean {
        if (result!!.seenNumber() && exponentSign == 0) return false
        else if (exponentSign != 0) assertDecimalMatcher { result.quantity != null }
        val initialOffset = segment!!.getOffset()
        var maybeMore = false
        var digitsConsumed: DecimalQuantity_DualStorageBCD? = null
        var digitsAfterDecimalPlace = 0
        var actualGroupingString: String? = null
        var actualDecimalString: String? = null
        var currGroupOffset = 0
        var currGroupSepType = 0
        var currGroupCount = 0
        var prevGroupOffset = -1
        var prevGroupSepType = -1
        var prevGroupCount = -1
        while (segment.length > 0) {
            maybeMore = false
            var digit: Byte = -1
            val cp = segment.getCodePoint()
            if (UCharacter.isDigit(cp)) {
                segment.adjustOffset(if (cp >= 0x10000) 2 else 1)
                digit = UCharacter.digit(cp).toByte()
            }
            if (digit.toInt() == -1 && digitStrings != null) {
                for (i in digitStrings.indices) {
                    val str = digitStrings[i]
                    if (str.isEmpty()) continue
                    val overlap = segment.getCommonPrefixLength(str)
                    if (overlap == str.length) { segment.adjustOffset(overlap); digit = i.toByte(); break }
                    maybeMore = maybeMore || overlap == segment.length
                }
            }
            if (digit >= 0) {
                if (digitsConsumed == null) digitsConsumed = DecimalQuantity_DualStorageBCD()
                digitsConsumed.appendDigit(digit, 0, true)
                currGroupCount++
                if (actualDecimalString != null) digitsAfterDecimalPlace++
                continue
            }
            var isDecimal = false
            var isGrouping = false
            if (actualDecimalString == null && decimalSeparator!!.isNotEmpty()) {
                val overlap = segment.getCommonPrefixLength(decimalSeparator)
                maybeMore = maybeMore || overlap == segment.length
                if (overlap == decimalSeparator!!.length) { isDecimal = true; actualDecimalString = decimalSeparator }
            }
            if (actualGroupingString != null) {
                val overlap = segment.getCommonPrefixLength(actualGroupingString)
                maybeMore = maybeMore || overlap == segment.length
                if (overlap == actualGroupingString.length) isGrouping = true
            }
            if (!groupingDisabled && actualGroupingString == null && actualDecimalString == null && groupingSeparator!!.isNotEmpty()) {
                val overlap = segment.getCommonPrefixLength(groupingSeparator)
                maybeMore = maybeMore || overlap == segment.length
                if (overlap == groupingSeparator!!.length) { isGrouping = true; actualGroupingString = groupingSeparator }
            }
            if (!isGrouping && actualDecimalString == null && decimalUniSet.contains(cp)) {
                isDecimal = true; actualDecimalString = UCharacter.toString(cp)
            }
            if (!groupingDisabled && actualGroupingString == null && actualDecimalString == null && groupingUniSet.contains(cp)) {
                isGrouping = true; actualGroupingString = UCharacter.toString(cp)
            }
            if (!isDecimal && !isGrouping) break
            if (isDecimal && integerOnly) break
            else if (currGroupSepType == 2 && isGrouping) break
            val prevValidSecondary = validateGroup(prevGroupSepType, prevGroupCount, false)
            val currValidPrimary = validateGroup(currGroupSepType, currGroupCount, true)
            if (!prevValidSecondary || isDecimal && !currValidPrimary) {
                if (isGrouping && currGroupCount == 0) assertDecimalMatcher { currGroupSepType == 1 }
                else if (requireGroupingMatch) digitsConsumed = null
                break
            } else if (requireGroupingMatch && currGroupCount == 0 && currGroupSepType == 1) break
            else {
                prevGroupOffset = currGroupOffset
                prevGroupCount = currGroupCount
                prevGroupSepType = if (isDecimal) -1 else currGroupSepType
            }
            if (currGroupCount != 0) currGroupOffset = segment.getOffset()
            currGroupSepType = if (isGrouping) 1 else 2
            currGroupCount = 0
            segment.adjustOffset(if (isGrouping) actualGroupingString!!.length else actualDecimalString!!.length)
        }
        if (currGroupSepType != 2 && currGroupCount == 0) {
            maybeMore = true
            segment.setOffset(currGroupOffset)
            currGroupOffset = prevGroupOffset; currGroupSepType = prevGroupSepType; currGroupCount = prevGroupCount
            prevGroupOffset = -1; prevGroupSepType = 0; prevGroupCount = 1
        }
        var prevValidSecondary = validateGroup(prevGroupSepType, prevGroupCount, false)
        var currValidPrimary = validateGroup(currGroupSepType, currGroupCount, true)
        if (!requireGroupingMatch) {
            var digitsToRemove = 0
            if (!prevValidSecondary) {
                segment.setOffset(prevGroupOffset); digitsToRemove += prevGroupCount; digitsToRemove += currGroupCount
            } else if (!currValidPrimary && (prevGroupSepType != 0 || prevGroupCount != 0)) {
                maybeMore = true; segment.setOffset(currGroupOffset); digitsToRemove += currGroupCount
            }
            if (digitsToRemove != 0) { digitsConsumed!!.adjustMagnitude(-digitsToRemove); digitsConsumed.truncate() }
            prevValidSecondary = true; currValidPrimary = true
        }
        if (currGroupSepType != 2 && (!prevValidSecondary || !currValidPrimary)) digitsConsumed = null
        if (digitsConsumed == null) {
            maybeMore = maybeMore || segment.length == 0
            segment.setOffset(initialOffset)
            return maybeMore
        }
        digitsConsumed.adjustMagnitude(-digitsAfterDecimalPlace)
        if (exponentSign != 0 && segment.getOffset() != initialOffset) {
            var overflow = false
            if (digitsConsumed.fitsInLong()) {
                val exponentLong = digitsConsumed.toLong(false)
                assertDecimalMatcher { exponentLong >= 0 }
                if (exponentLong <= Int.MAX_VALUE) {
                    try { result.quantity!!.adjustMagnitude(exponentSign * exponentLong.toInt()) }
                    catch (e: ArithmeticException) { overflow = true }
                } else overflow = true
            } else overflow = true
            if (overflow) {
                if (exponentSign == -1) result.quantity!!.clear()
                else { result.quantity = null; result.flags = result.flags or ParsedNumber.FLAG_INFINITY }
            }
        } else result.quantity = digitsConsumed
        if (actualDecimalString != null) result.flags = result.flags or ParsedNumber.FLAG_HAS_DECIMAL_SEPARATOR
        result.setCharsConsumed(segment)
        return segment.length == 0 || maybeMore
    }
    private fun validateGroup(sepType: Int, count: Int, isPrimary: Boolean): Boolean {
        if (requireGroupingMatch) {
            return when (sepType) {
                -1 -> true
                0 -> if (isPrimary) true else count != 0 && count <= grouping2
                1 -> count == if (isPrimary) grouping1 else grouping2
                else -> { assertDecimalMatcher { sepType == 2 }; true }
            }
        }
        return sepType != 1 || count != 1
    }
    override fun smokeTest(segment: StringSegment?): Boolean {
        if (digitStrings == null && leadSet != null) return segment!!.startsWith(leadSet)
        if (segment!!.startsWith(separatorSet) || UCharacter.isDigit(segment.getCodePoint())) return true
        if (digitStrings == null) return false
        for (str in digitStrings) if (segment.startsWith(str)) return true
        return false
    }
    override fun postProcess(result: ParsedNumber?) {}
    override fun toString(): String = "<DecimalMatcher>"
    companion object {
        @JvmStatic fun getInstance(symbols: DecimalFormatSymbols?, grouper: Grouper?, parseFlags: Int): DecimalMatcher = DecimalMatcher(symbols, grouper, parseFlags)
    }
}
internal expect fun assertDecimalMatcher(condition: () -> Boolean)
