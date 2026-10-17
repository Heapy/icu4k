// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.getAvailableLocales
import java.util.Locale

fun Calendar.Companion.getInstance(locale: Locale?): Calendar = getInstance(ULocale.forLocale(locale))
fun Calendar.Companion.getInstance(zone: TimeZone?, locale: Locale?): Calendar = getInstance(zone, ULocale.forLocale(locale))
fun Calendar.Companion.getAvailableLocales(): Array<Locale> = ICUResourceBundle.getAvailableLocales()

@Suppress("UNUSED_PARAMETER")
fun Calendar.getDisplayName(locale: Locale?): String = calendarClassName(this)
