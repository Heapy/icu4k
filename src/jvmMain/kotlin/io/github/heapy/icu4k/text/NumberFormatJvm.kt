// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.getAvailableLocales
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun NumberFormat.Companion.getInstance(locale: Locale?): NumberFormat = getInstance(ULocale.forLocale(locale))
fun NumberFormat.Companion.getInstance(locale: Locale?, style: Int): NumberFormat = getInstance(ULocale.forLocale(locale), style)
fun NumberFormat.Companion.getNumberInstance(locale: Locale?): NumberFormat = getNumberInstance(ULocale.forLocale(locale))
fun NumberFormat.Companion.getIntegerInstance(locale: Locale?): NumberFormat = getIntegerInstance(ULocale.forLocale(locale))
fun NumberFormat.Companion.getCurrencyInstance(locale: Locale?): NumberFormat = getCurrencyInstance(ULocale.forLocale(locale))
fun NumberFormat.Companion.getPercentInstance(locale: Locale?): NumberFormat = getPercentInstance(ULocale.forLocale(locale))
fun NumberFormat.Companion.getScientificInstance(locale: Locale?): NumberFormat = getScientificInstance(ULocale.forLocale(locale))
fun NumberFormat.Companion.getAvailableLocales(): Array<Locale> = serviceOrNull()?.getAvailableLocales()
    ?: ICUResourceBundle.getAvailableLocales()
