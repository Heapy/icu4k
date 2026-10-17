// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

/** Kotlin constructor-shaped JVM Locale adapters for the common calendar classes. */
fun GregorianCalendar(locale: java.util.Locale?): GregorianCalendar = GregorianCalendar(TimeZone.forLocaleOrDefault(locale), ULocale.forLocale(locale))
fun GregorianCalendar(zone: TimeZone?, locale: java.util.Locale?): GregorianCalendar = GregorianCalendar(zone, ULocale.forLocale(locale))
fun JapaneseCalendar(locale: java.util.Locale?): JapaneseCalendar = JapaneseCalendar(TimeZone.forLocaleOrDefault(locale), ULocale.forLocale(locale))
fun JapaneseCalendar(zone: TimeZone?, locale: java.util.Locale?): JapaneseCalendar = JapaneseCalendar(zone, ULocale.forLocale(locale))
fun IslamicCalendar(locale: java.util.Locale?): IslamicCalendar = IslamicCalendar(TimeZone.forLocaleOrDefault(locale), ULocale.forLocale(locale))
fun IslamicCalendar(zone: TimeZone?, locale: java.util.Locale?): IslamicCalendar = IslamicCalendar(zone, ULocale.forLocale(locale))
