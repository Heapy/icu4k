// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// Built-in creator from ICU4J 78.3 NumberFormat.createInstance.
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.number.NumberFormatPatternData
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale

/** Built-in numeric style creation and algorithmic dispatch, before the RBNF facade is wired. */
internal object NumberFormatDataFactory {
    /** Construct RBNF and select its default rule set, in that order. */
    fun interface AlgorithmicFactory {
        fun create(locale: ULocale?, rulesType: Int, ruleSetName: String): NumberFormat
    }
    fun create(desiredLocale: ULocale?, choice: Int, algorithmic: AlgorithmicFactory): NumberFormat? {
        var pattern = NumberFormatPatternData.getPatternForStyle(desiredLocale, choice)
        val symbols = DecimalFormatSymbols(desiredLocale)
        if (choice == NumberFormat.CURRENCYSTYLE || choice == NumberFormat.ISOCURRENCYSTYLE ||
            choice == NumberFormat.ACCOUNTINGCURRENCYSTYLE || choice == NumberFormat.CASHCURRENCYSTYLE ||
            choice == NumberFormat.STANDARDCURRENCYSTYLE) {
            val currencyPattern = symbols.getCurrencyPattern()
            if (currencyPattern != null) pattern = currencyPattern
        }
        if (choice == NumberFormat.ISOCURRENCYSTYLE) pattern = pattern.replace("¤", "¤¤")
        val ns: NumberingSystem? = NumberingSystem.getInstance(desiredLocale)
        if (ns == null) return null
        val format: NumberFormat
        if (ns.isAlgorithmic()) {
            val description = ns.getDescription()!!
            val firstSlash = description.indexOf('/')
            val lastSlash = description.lastIndexOf('/')
            val nsLocale: ULocale?
            val ruleSetName: String
            var desiredRulesType = 4 // RuleBasedNumberFormat.NUMBERING_SYSTEM
            if (lastSlash > firstSlash) {
                val localeID = description.substring(0, firstSlash)
                val ruleSetGroup = description.substring(firstSlash + 1, lastSlash)
                ruleSetName = description.substring(lastSlash + 1)
                nsLocale = ULocale(localeID)
                if (ruleSetGroup == "SpelloutRules") desiredRulesType = 1 // RuleBasedNumberFormat.SPELLOUT
            } else {
                nsLocale = desiredLocale
                ruleSetName = description
            }
            format = algorithmic.create(nsLocale, desiredRulesType, ruleSetName)
        } else {
            val f = DecimalFormat(pattern, symbols, choice)
            if (choice == NumberFormat.INTEGERSTYLE) {
                f.setMaximumFractionDigits(0)
                f.setDecimalSeparatorAlwaysShown(false)
                f.setParseIntegerOnly(true)
            }
            if (choice == NumberFormat.CASHCURRENCYSTYLE) f.setCurrencyUsage(Currency.CurrencyUsage.CASH)
            if (choice == NumberFormat.PLURALCURRENCYSTYLE) f.setCurrencyPluralInfo(CurrencyPluralInfo.getInstance(desiredLocale))
            format = f
        }
        val valid = symbols.getLocale(ULocale.VALID_LOCALE)
        val actual = symbols.getLocale(ULocale.ACTUAL_LOCALE)
        format.setLocale(valid, actual)
        return format
    }
}
