package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.CurrencyService
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.getAvailableLocales
import java.util.Date
import java.util.Locale

fun Currency.Companion.getInstance(locale: Locale?): Currency? = getInstance(ULocale.forLocale(locale))
fun Currency.Companion.fromJavaCurrency(currency: java.util.Currency?): Currency = getInstance(currency!!.currencyCode)
fun Currency.toJavaCurrency(): java.util.Currency = java.util.Currency.getInstance(getCurrencyCode())
fun Currency.getSymbol(locale: Locale?): String? = getSymbol(ULocale.forLocale(locale))
fun Currency.getName(locale: Locale?, nameStyle: Int, isChoiceFormat: BooleanArray?): String? = getName(ULocale.forLocale(locale), nameStyle, isChoiceFormat)
fun Currency.getName(locale: Locale?, nameStyle: Int, pluralCount: String?, isChoiceFormat: BooleanArray?): String? = getName(ULocale.forLocale(locale), nameStyle, pluralCount, isChoiceFormat)
fun Currency.getDisplayName(locale: Locale?): String? = getDisplayName(ULocale.forLocale(locale))
fun Currency.Companion.getAvailableCurrencyCodes(locale: ULocale?, date: Date?): Array<String>? = getAvailableCurrencyCodes(locale, date!!.time)
fun Currency.Companion.getAvailableCurrencyCodes(locale: Locale?, date: Date?): Array<String>? = getAvailableCurrencyCodes(ULocale.forLocale(locale), date!!.time)
fun Currency.Companion.isAvailable(code: String?, from: Date?, to: Date?): Boolean = isAvailable(code, from?.time, to?.time)
fun Currency.Companion.getAvailableLocales(): Array<Locale> = if (CurrencyService.isInitialized() && !CurrencyService.get().isDefault()) CurrencyService.get().getAvailableLocales() else ICUResourceBundle.getAvailableLocales()!!
