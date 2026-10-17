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
fun ChineseCalendar(locale: java.util.Locale?): ChineseCalendar = ChineseCalendar(TimeZone.forLocaleOrDefault(locale), ULocale.forLocale(locale))
fun ChineseCalendar(zone: TimeZone?, locale: java.util.Locale?): ChineseCalendar = ChineseCalendar(zone, ULocale.forLocale(locale))
fun BuddhistCalendar(locale: java.util.Locale?): BuddhistCalendar = BuddhistCalendar(TimeZone.forLocaleOrDefault(locale), ULocale.forLocale(locale))
fun BuddhistCalendar(zone: TimeZone?, locale: java.util.Locale?): BuddhistCalendar = BuddhistCalendar(zone, ULocale.forLocale(locale))
fun TaiwanCalendar(locale: java.util.Locale?): TaiwanCalendar = TaiwanCalendar(TimeZone.forLocaleOrDefault(locale), ULocale.forLocale(locale))
fun TaiwanCalendar(zone: TimeZone?, locale: java.util.Locale?): TaiwanCalendar = TaiwanCalendar(zone, ULocale.forLocale(locale))
fun PersianCalendar(locale: java.util.Locale?): PersianCalendar = PersianCalendar(TimeZone.forLocaleOrDefault(locale), ULocale.forLocale(locale))
fun PersianCalendar(zone: TimeZone?, locale: java.util.Locale?): PersianCalendar = PersianCalendar(zone, ULocale.forLocale(locale))
fun IndianCalendar(locale: java.util.Locale?): IndianCalendar = IndianCalendar(TimeZone.forLocaleOrDefault(locale), ULocale.forLocale(locale))
fun IndianCalendar(zone: TimeZone?, locale: java.util.Locale?): IndianCalendar = IndianCalendar(zone, ULocale.forLocale(locale))
fun HebrewCalendar(locale: java.util.Locale?): HebrewCalendar = HebrewCalendar(TimeZone.forLocaleOrDefault(locale), ULocale.forLocale(locale))
fun HebrewCalendar(zone: TimeZone?, locale: java.util.Locale?): HebrewCalendar = HebrewCalendar(zone, ULocale.forLocale(locale))
fun CopticCalendar(locale: java.util.Locale?): CopticCalendar = CopticCalendar(TimeZone.forLocaleOrDefault(locale), ULocale.forLocale(locale))
fun CopticCalendar(zone: TimeZone?, locale: java.util.Locale?): CopticCalendar = CopticCalendar(zone, ULocale.forLocale(locale))
fun EthiopicCalendar(locale: java.util.Locale?): EthiopicCalendar = EthiopicCalendar(TimeZone.forLocaleOrDefault(locale), ULocale.forLocale(locale))
fun EthiopicCalendar(zone: TimeZone?, locale: java.util.Locale?): EthiopicCalendar = EthiopicCalendar(zone, ULocale.forLocale(locale))
