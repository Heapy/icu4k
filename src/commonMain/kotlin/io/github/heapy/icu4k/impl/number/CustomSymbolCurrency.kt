// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.checkedSetArrayElement
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmStatic

/** Currency retaining a DecimalFormatSymbols symbol and international symbol override. */
open class CustomSymbolCurrency(isoCode: String?, private val symbol1: String?, private val symbol2: String?) : Currency(isoCode) {
    override fun getName(locale: ULocale?, nameStyle: Int, isChoiceFormat: BooleanArray?): String? {
        if (nameStyle == SYMBOL_NAME) {
            if (isChoiceFormat != null) checkedSetArrayElement(isChoiceFormat, 0, false)
            return symbol1
        }
        return super.getName(locale, nameStyle, isChoiceFormat)
    }
    override fun getName(locale: ULocale?, nameStyle: Int, pluralCount: String?, isChoiceFormat: BooleanArray?): String? =
        super.getName(locale, nameStyle, pluralCount, isChoiceFormat)
    override fun getCurrencyCode(): String? = symbol2
    override fun hashCode(): Int = super.hashCode() xor symbol1!!.hashCode() xor symbol2!!.hashCode()
    override fun equals(other: Any?): Boolean = super.equals(other) &&
        (other as CustomSymbolCurrency).symbol1!!.equals(symbol1) && other.symbol2!!.equals(symbol2)

    companion object {
        private const val serialVersionUID = 2497493016770137670L

        @JvmStatic fun resolve(currency: Currency?, locale: ULocale?, symbols: DecimalFormatSymbols?): Currency {
            val resolved = currency ?: symbols!!.getCurrency() ?: return Currency.getInstance("XXX")
            if (!resolved.equals(symbols!!.getCurrency())) return resolved
            val currency1Sym = symbols.getCurrencySymbol()
            val currency2Sym = symbols.getInternationalCurrencySymbol()
            val currency1 = resolved.getName(symbols.getULocale(), SYMBOL_NAME, null)
            val currency2 = resolved.getCurrencyCode()
            if (!currency1!!.equals(currency1Sym) || !currency2!!.equals(currency2Sym)) {
                return CustomSymbolCurrency(currency2, currency1Sym, currency2Sym)
            }
            return resolved
        }
    }
}
