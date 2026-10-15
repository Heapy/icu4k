// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.text.NumberingSystem
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** NumberFormat's style-to-pattern lookup, shared before its formatter facade is complete. */
internal object NumberFormatPatternData {
    const val NUMBERSTYLE = 0
    const val CURRENCYSTYLE = 1
    const val PERCENTSTYLE = 2
    const val SCIENTIFICSTYLE = 3
    const val INTEGERSTYLE = 4
    const val ISOCURRENCYSTYLE = 5
    const val PLURALCURRENCYSTYLE = 6
    const val ACCOUNTINGCURRENCYSTYLE = 7
    const val CASHCURRENCYSTYLE = 8
    const val STANDARDCURRENCYSTYLE = 9
    fun getPatternForStyle(locale: ULocale?, choice: Int): String =
        getPatternForStyleAndNumberingSystem(locale, NumberingSystem.getInstance(locale).getName(), choice)
    fun getPatternForStyleAndNumberingSystem(locale: ULocale?, nsName: String?, choice: Int): String {
        val key = when (choice) {
            NUMBERSTYLE, INTEGERSTYLE, PLURALCURRENCYSTYLE -> "decimalFormat"
            CURRENCYSTYLE -> if (locale!!.getKeywordValue("cf") == "account") "accountingFormat" else "currencyFormat"
            CASHCURRENCYSTYLE, ISOCURRENCYSTYLE, STANDARDCURRENCYSTYLE -> "currencyFormat"
            PERCENTSTYLE -> "percentFormat"
            SCIENTIFICSTYLE -> "scientificFormat"
            ACCOUNTINGCURRENCYSTYLE -> "accountingFormat"
            else -> { assertInvalidNumberPatternStyle(); "decimalFormat" }
        }
        val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
        return bundle.findStringWithFallback("NumberElements/$nsName/patterns/$key")
            ?: bundle.getStringWithFallback("NumberElements/latn/patterns/$key")
    }
}
internal expect fun assertInvalidNumberPatternStyle()
