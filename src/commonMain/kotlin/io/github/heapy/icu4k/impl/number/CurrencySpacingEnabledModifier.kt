// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.jvm.JvmStatic

/** Constant affixes with CLDR currency spacing at currency/number boundaries. */
open class CurrencySpacingEnabledModifier(prefix: FormattedStringBuilder?, suffix: FormattedStringBuilder?,
    overwrite: Boolean, strong: Boolean, symbols: DecimalFormatSymbols?
) : ConstantMultiFieldModifier(prefix, suffix, overwrite, strong) {
    private val afterPrefixUnicodeSet: UnicodeSet?
    private val afterPrefixInsert: String?
    private val beforeSuffixUnicodeSet: UnicodeSet?
    private val beforeSuffixInsert: String?

    init {
        if (prefix!!.length > 0 && prefix.fieldAt(prefix.length - 1) === NumberFormatField.CURRENCY) {
            val prefixCp = prefix.getLastCodePoint()
            val currencySet = getUnicodeSet(symbols, IN_CURRENCY, PREFIX)
            if (currencySet.contains(prefixCp)) {
                afterPrefixUnicodeSet = getUnicodeSet(symbols, IN_NUMBER, PREFIX).freeze()
                afterPrefixInsert = getInsertString(symbols, PREFIX)
            } else {
                afterPrefixUnicodeSet = null
                afterPrefixInsert = null
            }
        } else {
            afterPrefixUnicodeSet = null
            afterPrefixInsert = null
        }
        if (suffix!!.length > 0 && suffix.fieldAt(0) === NumberFormatField.CURRENCY) {
            val suffixCp = suffix.getFirstCodePoint()
            val currencySet = getUnicodeSet(symbols, IN_CURRENCY, SUFFIX)
            if (currencySet.contains(suffixCp)) {
                beforeSuffixUnicodeSet = getUnicodeSet(symbols, IN_NUMBER, SUFFIX).freeze()
                beforeSuffixInsert = getInsertString(symbols, SUFFIX)
            } else {
                beforeSuffixUnicodeSet = null
                beforeSuffixInsert = null
            }
        } else {
            beforeSuffixUnicodeSet = null
            beforeSuffixInsert = null
        }
    }

    override fun apply(output: FormattedStringBuilder?, leftIndex: Int, rightIndex: Int): Int {
        var length = 0
        if (rightIndex - leftIndex > 0 && afterPrefixUnicodeSet != null &&
            afterPrefixUnicodeSet.contains(output!!.codePointAt(leftIndex))) {
            length += output.insert(leftIndex, afterPrefixInsert!!, null)
        }
        // Keep ICU's original index here, including when a prefix spacer was inserted.
        if (rightIndex - leftIndex > 0 && beforeSuffixUnicodeSet != null &&
            beforeSuffixUnicodeSet.contains(output!!.codePointBefore(rightIndex))) {
            length += output.insert(rightIndex + length, beforeSuffixInsert!!, null)
        }
        length += super.apply(output, leftIndex, rightIndex + length)
        return length
    }

    companion object {
        private val UNISET_DIGIT = UnicodeSet("[:digit:]").freeze()
        private val UNISET_NOTSZ = UnicodeSet("[[:^S:]&[:^Z:]]").freeze()
        internal const val PREFIX: Byte = 0
        internal const val SUFFIX: Byte = 1
        internal const val IN_CURRENCY: Short = 0
        internal const val IN_NUMBER: Short = 1

        @JvmStatic fun applyCurrencySpacing(output: FormattedStringBuilder?, prefixStart: Int, prefixLen: Int,
                                            suffixStart: Int, suffixLen: Int, symbols: DecimalFormatSymbols?): Int {
            var length = 0
            val hasNumber = suffixStart - prefixStart - prefixLen > 0
            if (prefixLen > 0 && hasNumber) {
                length += applyCurrencySpacingAffix(output, prefixStart + prefixLen, PREFIX, symbols)
            }
            if (suffixLen > 0 && hasNumber) {
                length += applyCurrencySpacingAffix(output, suffixStart + length, SUFFIX, symbols)
            }
            return length
        }

        private fun applyCurrencySpacingAffix(output: FormattedStringBuilder?, index: Int, affix: Byte,
                                              symbols: DecimalFormatSymbols?): Int {
            val field = if (affix == PREFIX) output!!.fieldAt(index - 1) else output!!.fieldAt(index)
            if (field !== NumberFormatField.CURRENCY) return 0
            val affixCp = if (affix == PREFIX) output.codePointBefore(index) else output.codePointAt(index)
            if (!getUnicodeSet(symbols, IN_CURRENCY, affix).contains(affixCp)) return 0
            val numberCp = if (affix == PREFIX) output.codePointAt(index) else output.codePointBefore(index)
            if (!getUnicodeSet(symbols, IN_NUMBER, affix).contains(numberCp)) return 0
            return output.insert(index, getInsertString(symbols, affix)!!, null)
        }

        private fun getUnicodeSet(symbols: DecimalFormatSymbols?, position: Short, affix: Byte): UnicodeSet {
            val pattern = symbols!!.getPatternForCurrencySpacing(
                if (position == IN_CURRENCY) DecimalFormatSymbols.CURRENCY_SPC_CURRENCY_MATCH
                else DecimalFormatSymbols.CURRENCY_SPC_SURROUNDING_MATCH, affix == SUFFIX)!!
            return when (pattern) {
                "[:digit:]" -> UNISET_DIGIT
                "[[:^S:]&[:^Z:]]" -> UNISET_NOTSZ
                else -> UnicodeSet(pattern)
            }
        }
        private fun getInsertString(symbols: DecimalFormatSymbols?, affix: Byte): String? =
            symbols!!.getPatternForCurrencySpacing(DecimalFormatSymbols.CURRENCY_SPC_INSERT, affix == SUFFIX)
    }
}
