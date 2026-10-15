// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.number.Padder.PadPosition
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Recursive-descent parser for LDML decimal-format patterns. */
open class PatternStringParser {
    open class ParsedPatternInfo private constructor(@JvmField var pattern: String?) : AffixPatternProvider {
        @JvmField var positive: ParsedSubpatternInfo? = null
        @JvmField var negative: ParsedSubpatternInfo? = null
        private fun endpoints(flags: Int): Long {
            val selected = if (flags and AffixPatternProvider.Flags.NEGATIVE_SUBPATTERN != 0) negative!! else positive!!
            return if (flags and AffixPatternProvider.Flags.PADDING != 0) selected.paddingEndpoints
            else if (flags and AffixPatternProvider.Flags.PREFIX != 0) selected.prefixEndpoints else selected.suffixEndpoints
        }
        override fun charAt(flags: Int, index: Int): Char {
            val endpoints = endpoints(flags)
            val left = endpoints.toInt(); val right = (endpoints ushr 32).toInt()
            if (index < 0 || index >= right - left) throw IndexOutOfBoundsException()
            return pattern!![left + index]
        }
        override fun length(flags: Int): Int = getLengthFromEndpoints(endpoints(flags))
        override fun getString(flags: Int): String {
            val endpoints = endpoints(flags)
            val left = endpoints.toInt(); val right = (endpoints ushr 32).toInt()
            return if (left == right) "" else pattern!!.substring(left, right)
        }
        override fun positiveHasPlusSign(): Boolean = positive!!.hasPlusSign
        override fun hasNegativeSubpattern(): Boolean = negative != null
        override fun negativeHasMinusSign(): Boolean = negative!!.hasMinusSign
        override fun hasCurrencySign(): Boolean = positive!!.hasCurrencySign || negative?.hasCurrencySign == true
        override fun containsSymbolType(type: Int): Boolean = AffixUtils.containsType(pattern, type)
        override fun hasBody(): Boolean = positive!!.integerTotal > 0
        override fun currencyAsDecimal(): Boolean = positive!!.hasCurrencyDecimal
        companion object {
            internal fun create(pattern: String?) = ParsedPatternInfo(pattern)
            @JvmStatic fun getLengthFromEndpoints(endpoints: Long): Int = (endpoints ushr 32).toInt() - endpoints.toInt() }
    }
    open class ParsedSubpatternInfo {
        @JvmField var groupingSizes = 0x0000ffffffff0000L
        @JvmField var integerLeadingHashSigns = 0
        @JvmField var integerTrailingHashSigns = 0
        @JvmField var integerNumerals = 0
        @JvmField var integerAtSigns = 0
        @JvmField var integerTotal = 0
        @JvmField var fractionNumerals = 0
        @JvmField var fractionHashSigns = 0
        @JvmField var fractionTotal = 0
        @JvmField var hasDecimal = false
        @JvmField var widthExceptAffixes = 0
        @JvmField var paddingLocation: PadPosition? = null
        @JvmField var rounding: DecimalQuantity_DualStorageBCD? = null
        @JvmField var exponentHasPlusSign = false
        @JvmField var exponentZeros = 0
        @JvmField var hasPercentSign = false
        @JvmField var hasPerMilleSign = false
        @JvmField var hasCurrencySign = false
        @JvmField var hasCurrencyDecimal = false
        @JvmField var hasMinusSign = false
        @JvmField var hasPlusSign = false
        @JvmField var prefixEndpoints = 0L
        @JvmField var suffixEndpoints = 0L
        @JvmField var paddingEndpoints = 0L
    }
    companion object {
        const val IGNORE_ROUNDING_NEVER = 0
        const val IGNORE_ROUNDING_IF_CURRENCY = 1
        const val IGNORE_ROUNDING_ALWAYS = 2
        @JvmStatic fun parseToPatternInfo(patternString: String?): ParsedPatternInfo =
            PatternStringParserCore.parseToPatternInfo(patternString)
        @JvmStatic @JvmOverloads fun parseToProperties(pattern: String?, ignoreRounding: Int = IGNORE_ROUNDING_NEVER): DecimalFormatProperties =
            PatternStringParserCore.parseToDecimalFormatProperties(pattern, ignoreRounding)
        @JvmStatic @JvmOverloads fun parseToExistingProperties(pattern: String?, properties: DecimalFormatProperties?, ignoreRounding: Int = IGNORE_ROUNDING_NEVER) =
            PatternStringParserCore.parseToExistingProperties(pattern, properties, ignoreRounding)
    }
}
