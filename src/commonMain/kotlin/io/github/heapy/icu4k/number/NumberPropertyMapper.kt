// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.CompactStyle
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.Currency.CurrencyUsage

/** Legacy DecimalFormat property validation and projection into formatter macro settings. */
internal object NumberPropertyMapper {
    fun create(properties: DecimalFormatProperties?, symbols: DecimalFormatSymbols?): UnlocalizedNumberFormatter =
        NumberFormatter.with().macros(oldToNew(properties, symbols, null))
    fun create(properties: DecimalFormatProperties?, symbols: DecimalFormatSymbols?, exportedProperties: DecimalFormatProperties?): UnlocalizedNumberFormatter =
        NumberFormatter.with().macros(oldToNew(properties, symbols, exportedProperties))
    fun create(pattern: String?, symbols: DecimalFormatSymbols?): UnlocalizedNumberFormatter {
        val properties = PatternStringParser.parseToProperties(pattern)
        return create(properties, symbols)
    }
    fun oldToNew(properties: DecimalFormatProperties?, symbols: DecimalFormatSymbols?,
        exportedProperties: DecimalFormatProperties?): MacroProps {
        val macros = MacroProps()
        val locale = symbols!!.getULocale()
        macros.symbols = symbols
        val p = properties!!
        var rules = p.getPluralRules()
        if (rules == null && p.getCurrencyPluralInfo() != null) rules = p.getCurrencyPluralInfo()!!.getPluralRules()
        macros.rules = rules
        val affixProvider = PropertiesAffixPatternProvider.forProperties(p)
        macros.affixProvider = affixProvider
        val useCurrency = p.getCurrency() != null || p.getCurrencyPluralInfo() != null ||
            p.getCurrencyUsage() != null || affixProvider.hasCurrencySign()
        val currency = CustomSymbolCurrency.resolve(p.getCurrency(), locale, symbols)
        val currencyUsage = p.getCurrencyUsage()
        val explicitCurrencyUsage = currencyUsage != null
        val usage = currencyUsage ?: CurrencyUsage.STANDARD
        if (useCurrency) macros.unit = currency

        var maxInt = p.getMaximumIntegerDigits()
        var minInt = p.getMinimumIntegerDigits()
        var maxFrac = p.getMaximumFractionDigits()
        var minFrac = p.getMinimumFractionDigits()
        var minSig = p.getMinimumSignificantDigits()
        var maxSig = p.getMaximumSignificantDigits()
        var roundingIncrement = p.getRoundingIncrement()
        val mathContext = RoundingUtils.getMathContextOrUnlimited(p)
        val explicitMinMaxFrac = minFrac != -1 || maxFrac != -1
        val explicitMinMaxSig = minSig != -1 || maxSig != -1
        if (useCurrency) {
            if (minFrac == -1 && maxFrac == -1) {
                minFrac = currency.getDefaultFractionDigits(usage)
                maxFrac = currency.getDefaultFractionDigits(usage)
            } else if (minFrac == -1) {
                minFrac = minOf(maxFrac, currency.getDefaultFractionDigits(usage))
            } else if (maxFrac == -1) {
                maxFrac = maxOf(minFrac, currency.getDefaultFractionDigits(usage))
            }
        }
        if (minInt == 0 && maxFrac != 0) {
            minFrac = if (minFrac < 0 || (minFrac == 0 && maxInt == 0)) 1 else minFrac
            maxFrac = if (maxFrac < 0) -1 else if (maxFrac < minFrac) minFrac else maxFrac
            minInt = 0
            maxInt = if (maxInt < 0 || maxInt > RoundingUtils.MAX_INT_FRAC_SIG) -1 else maxInt
        } else {
            minFrac = if (minFrac < 0) 0 else minFrac
            maxFrac = if (maxFrac < 0) -1 else if (maxFrac < minFrac) minFrac else maxFrac
            minInt = if (minInt <= 0 || minInt > RoundingUtils.MAX_INT_FRAC_SIG) 1 else minInt
            maxInt = if (maxInt < 0) -1 else if (maxInt < minInt) minInt
                else if (maxInt > RoundingUtils.MAX_INT_FRAC_SIG) -1 else maxInt
        }
        var rounding: Precision? = null
        if (explicitCurrencyUsage) {
            rounding = Precision.constructCurrency(usage).withCurrency(currency)
        } else if (roundingIncrement != null) {
            if (PatternStringUtils.ignoreRoundingIncrement(roundingIncrement, maxFrac)) {
                rounding = Precision.constructFraction(minFrac, maxFrac)
            } else {
                if (minFrac > roundingIncrement.scale()) roundingIncrement = roundingIncrement.setScale(minFrac)
                rounding = Precision.constructIncrement(roundingIncrement)
            }
        } else if (explicitMinMaxSig) {
            minSig = if (minSig < 1) 1 else if (minSig > RoundingUtils.MAX_INT_FRAC_SIG) RoundingUtils.MAX_INT_FRAC_SIG else minSig
            maxSig = if (maxSig < 0) RoundingUtils.MAX_INT_FRAC_SIG else if (maxSig < minSig) minSig
                else if (maxSig > RoundingUtils.MAX_INT_FRAC_SIG) RoundingUtils.MAX_INT_FRAC_SIG else maxSig
            rounding = Precision.constructSignificant(minSig, maxSig)
        } else if (explicitMinMaxFrac) {
            rounding = Precision.constructFraction(minFrac, maxFrac)
        } else if (useCurrency) {
            rounding = Precision.constructCurrency(usage)
        }
        if (rounding != null) {
            rounding = rounding.withMode(mathContext)
            macros.precision = rounding
        }
        macros.integerWidth = IntegerWidth.zeroFillTo(minInt).truncateAt(maxInt)
        macros.grouping = Grouper.forProperties(p)
        if (p.getFormatWidth() > 0) macros.padder = Padder.forProperties(p)
        macros.decimal = if (p.getDecimalSeparatorAlwaysShown()) DecimalSeparatorDisplay.ALWAYS else DecimalSeparatorDisplay.AUTO
        macros.sign = if (p.getSignAlwaysShown()) SignDisplay.ALWAYS else SignDisplay.AUTO

        if (p.getMinimumExponentDigits() != -1) {
            if (maxInt > 8) {
                maxInt = minInt
                macros.integerWidth = IntegerWidth.zeroFillTo(minInt).truncateAt(maxInt)
            } else if (maxInt > minInt && minInt > 1) {
                minInt = 1
                macros.integerWidth = IntegerWidth.zeroFillTo(minInt).truncateAt(maxInt)
            }
            val engineering = if (maxInt < 0) -1 else maxInt
            macros.notation = ScientificNotation(engineering, engineering == minInt,
                p.getMinimumExponentDigits(), if (p.getExponentSignAlwaysShown()) SignDisplay.ALWAYS else SignDisplay.AUTO)
            if (macros.precision is FractionPrecision) {
                val originalMaxInt = p.getMaximumIntegerDigits()
                var originalMinInt = p.getMinimumIntegerDigits()
                val originalMinFrac = p.getMinimumFractionDigits()
                val originalMaxFrac = p.getMaximumFractionDigits()
                if (originalMinInt == 0 && originalMaxFrac == 0) {
                    macros.precision = Precision.unlimited().withMode(mathContext)
                } else if (originalMinInt == 0 && originalMinFrac == 0) {
                    macros.precision = Precision.constructSignificant(1, originalMaxFrac + 1).withMode(mathContext)
                } else {
                    val originalMaxSig = originalMinInt + originalMaxFrac
                    if (originalMaxInt > originalMinInt && originalMinInt > 1) originalMinInt = 1
                    val originalMinSig = originalMinInt + originalMinFrac
                    macros.precision = Precision.constructSignificant(originalMinSig, originalMaxSig).withMode(mathContext)
                }
            }
        }
        if (p.getCompactStyle() != null) {
            macros.notation = if (p.getCompactCustomData() != null) CompactNotation(p.getCompactCustomData())
                else if (p.getCompactStyle() == CompactStyle.LONG) Notation.compactLong() else Notation.compactShort()
        }
        macros.scale = RoundingUtils.scaleFromProperties(p)

        if (exportedProperties != null) {
            exportedProperties.setCurrency(currency)
            exportedProperties.setMathContext(mathContext)
            exportedProperties.setRoundingMode(mathContext.getRoundingMode())
            exportedProperties.setMinimumIntegerDigits(minInt)
            exportedProperties.setMaximumIntegerDigits(if (maxInt == -1) Int.MAX_VALUE else maxInt)
            val resolvedRounding = if (rounding is CurrencyPrecision) rounding.withCurrency(currency) else rounding
            var exportMinFrac = minFrac
            var exportMaxFrac = maxFrac
            var exportMinSig = minSig
            var exportMaxSig = maxSig
            var increment: io.github.heapy.icu4k.util.BigDecimal? = null
            when (resolvedRounding) {
                is Precision.FractionRounderImpl -> {
                    exportMinFrac = resolvedRounding.minFrac; exportMaxFrac = resolvedRounding.maxFrac
                }
                is Precision.IncrementRounderImpl -> {
                    increment = resolvedRounding.increment
                    exportMinFrac = increment.scale(); exportMaxFrac = increment.scale()
                }
                is Precision.SignificantRounderImpl -> {
                    exportMinSig = resolvedRounding.minSig; exportMaxSig = resolvedRounding.maxSig
                }
            }
            exportedProperties.setMinimumFractionDigits(exportMinFrac)
            exportedProperties.setMaximumFractionDigits(exportMaxFrac)
            exportedProperties.setMinimumSignificantDigits(exportMinSig)
            exportedProperties.setMaximumSignificantDigits(exportMaxSig)
            exportedProperties.setRoundingIncrement(increment)
        }
        return macros
    }
}
