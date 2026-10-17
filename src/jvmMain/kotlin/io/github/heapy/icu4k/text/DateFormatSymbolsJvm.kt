// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION", "UNUSED_PARAMETER")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.getAvailableLocales
import io.github.heapy.icu4k.util.*
import java.util.Locale
import java.util.ResourceBundle

fun DateFormatSymbols(locale: Locale?): DateFormatSymbols=DateFormatSymbols(ULocale.forLocale(locale))
fun DateFormatSymbols(calendar: Calendar?,locale: Locale?): DateFormatSymbols=DateFormatSymbols(calendar,ULocale.forLocale(locale))
fun DateFormatSymbols(calendarClass: Class<out Calendar>?,locale: ULocale?): DateFormatSymbols=DateFormatSymbols(calendarClass?.kotlin,locale)
fun DateFormatSymbols(calendarClass: Class<out Calendar>?,locale: Locale?): DateFormatSymbols=DateFormatSymbols(calendarClass?.kotlin,ULocale.forLocale(locale))
fun DateFormatSymbols(bundle: UResourceBundle?,locale: Locale?): DateFormatSymbols=DateFormatSymbols(bundle,ULocale.forLocale(locale))
fun DateFormatSymbols(bundle: ResourceBundle?,locale: ULocale?): DateFormatSymbols=DateFormatSymbols((bundle as Any?) as ICUResourceBundle?,locale)
fun DateFormatSymbols(bundle: ResourceBundle?,locale: Locale?): DateFormatSymbols {
    val converted=ULocale.forLocale(locale)
    return DateFormatSymbols(bundle,converted)
}
fun DateFormatSymbols.Companion.getInstance(locale: Locale?): DateFormatSymbols=DateFormatSymbols(locale)
fun DateFormatSymbols.Companion.getAvailableLocales(): Array<Locale> = ICUResourceBundle.getAvailableLocales()
@Deprecated("ICU 4.0: this method always returns null.")
fun DateFormatSymbols.Companion.getDateFormatBundle(calendar: Calendar?,locale: Locale?): ResourceBundle?=null
@Deprecated("ICU 4.0: this method always returns null.")
fun DateFormatSymbols.Companion.getDateFormatBundle(calendarClass: Class<out Calendar>?,locale: Locale?): ResourceBundle?=null
@Deprecated("ICU 4.0: this method always returns null.")
fun DateFormatSymbols.Companion.getDateFormatBundle(calendarClass: Class<out Calendar>?,locale: ULocale?): ResourceBundle?=null
