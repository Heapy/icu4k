// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.number.Modifier.Signum
import io.github.heapy.icu4k.number.SignDisplay
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.BigDecimal
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Utilities for LDML decimal-format patterns. */
open class PatternStringUtils {
    enum class PatternSignType {
        POS, POS_SIGN, NEG;
        companion object { @JvmField val VALUES: Array<PatternSignType> = entries.toTypedArray() }
    }
    companion object {
        @JvmStatic fun ignoreRoundingIncrement(roundIncrDec: BigDecimal?, maxFrac: Int): Boolean =
            PatternStringUtilsCore.ignoreRoundingIncrementValue(roundIncrDec!!.toDouble(), maxFrac)

        @JvmStatic fun propertiesToPatternString(properties: DecimalFormatProperties?): String {
            val source = properties!!
            // Read virtual getters in ICU's order; the rounding getter is called after affix setup.
            val p = PatternStringParserCore.PatternProperties()
            p.groupingSize = source.getGroupingSize()
            p.secondaryGroupingSize = source.getSecondaryGroupingSize()
            p.groupingUsed = source.getGroupingUsed()
            p.formatWidth = source.getFormatWidth()
            p.padPosition = source.getPadPosition()
            p.padString = source.getPadString()
            p.minimumIntegerDigits = source.getMinimumIntegerDigits()
            p.maximumIntegerDigits = source.getMaximumIntegerDigits()
            p.minimumFractionDigits = source.getMinimumFractionDigits()
            p.maximumFractionDigits = source.getMaximumFractionDigits()
            p.minimumSignificantDigits = source.getMinimumSignificantDigits()
            p.maximumSignificantDigits = source.getMaximumSignificantDigits()
            p.decimalSeparatorAlwaysShown = source.getDecimalSeparatorAlwaysShown()
            p.currencyAsDecimal = source.getCurrencyAsDecimal()
            p.minimumExponentDigits = source.getMinimumExponentDigits()
            p.exponentSignAlwaysShown = source.getExponentSignAlwaysShown()
            val affixes = PropertiesAffixPatternProvider.forProperties(source)
            return PatternStringUtilsCore.propertiesToPatternString(p, affixes) { source.getRoundingIncrement() }
        }

        @JvmStatic fun convertLocalized(input: String?, symbols: DecimalFormatSymbols?, toLocalized: Boolean): String? {
            if (input == null) return null
            // All getter calls precede quote replacement, including a possible null-symbol failure.
            val local = Array<String?>(21) { row ->
                val s = symbols!!
                when (row) {
                    0 -> s.getPercentString()
                    1 -> s.getPerMillString()
                    2 -> s.getDecimalSeparatorString()
                    3 -> s.getGroupingSeparatorString()
                    4 -> s.getMinusSignString()
                    5 -> s.getPlusSignString()
                    6 -> s.getPatternSeparator().toString()
                    7 -> s.getSignificantDigit().toString()
                    8 -> s.getExponentSeparator()
                    9 -> s.getPadEscape().toString()
                    10 -> s.getDigit().toString()
                    else -> s.getDigitStringsLocal()[row - 11]
                }
            }
            return PatternStringUtilsCore.convertLocalized(input, { local[it]!! }, toLocalized)
        }

        @JvmStatic fun patternInfoToStringBuilder(patternInfo: AffixPatternProvider?, isPrefix: Boolean,
                                                  patternSignType: PatternSignType?, approximately: Boolean,
                                                  plural: StandardPlural?, perMilleReplacesPercent: Boolean,
                                                  output: StringBuilder?) =
            PatternStringUtilsCore.patternInfoToStringBuilder(patternInfo, isPrefix, patternSignType,
                approximately, plural, perMilleReplacesPercent, output)

        @JvmStatic fun resolveSignDisplay(signDisplay: SignDisplay?, signum: Signum?): PatternSignType =
            PatternStringUtilsCore.resolveSignDisplay(signDisplay, signum)
    }
}
