// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text
import io.github.heapy.icu4k.impl.getAvailableLocales
import io.github.heapy.icu4k.impl.JavaTimeConverters
import io.github.heapy.icu4k.util.*
internal actual fun dateFormatCalendarInput(input: Any): Calendar? = when(input) {
    is java.util.Calendar -> JavaTimeConverters.convertCalendar(input)
    is java.time.temporal.Temporal -> JavaTimeConverters.temporalToCalendar(input)
    is java.time.DayOfWeek -> JavaTimeConverters.dayOfWeekToCalendar(input)
    is java.time.Month -> JavaTimeConverters.monthToCalendar(input)
    else -> null
}
internal actual fun dateFormatClassName(input: Any): String=input.javaClass.name
internal actual fun invalidDateFormatField(message: String): Nothing=throw java.io.InvalidObjectException(message)
fun DateFormat.format(date: java.time.temporal.Temporal,output: StringBuilder?,position: FormatFieldPosition?): StringBuilder = format(JavaTimeConverters.temporalToCalendar(date),output,position)
fun DateFormat.format(date: java.time.temporal.Temporal): String = format(date,StringBuilder(64),FormatFieldPosition(0)).toString()

internal actual fun dateFormatBooleanAttributes(): MutableSet<DateFormat.BooleanAttribute> = java.util.EnumSet.allOf(DateFormat.BooleanAttribute::class.java)

internal actual fun dateFormatAvailableLocales(): Array<MessageFormatLocale>? = io.github.heapy.icu4k.impl.ICUResourceBundle.getAvailableLocales()
fun DateFormat.Companion.getTimeInstance(style: Int, locale: java.util.Locale?): DateFormat = getTimeInstance(style, ULocale.forLocale(locale))
fun DateFormat.Companion.getDateInstance(style: Int, locale: java.util.Locale?): DateFormat = getDateInstance(style, ULocale.forLocale(locale))
fun DateFormat.Companion.getDateTimeInstance(dateStyle: Int, timeStyle: Int, locale: java.util.Locale?): DateFormat = getDateTimeInstance(dateStyle, timeStyle, ULocale.forLocale(locale))
fun DateFormat.Companion.getDateInstance(calendar: Calendar?, style: Int, locale: java.util.Locale?): DateFormat = getDateInstance(calendar, style, ULocale.forLocale(locale))
fun DateFormat.Companion.getTimeInstance(calendar: Calendar?, style: Int, locale: java.util.Locale?): DateFormat = getTimeInstance(calendar, style, ULocale.forLocale(locale))
fun DateFormat.Companion.getDateTimeInstance(calendar: Calendar?, dateStyle: Int, timeStyle: Int, locale: java.util.Locale?): DateFormat = getDateTimeInstance(calendar, dateStyle, timeStyle, ULocale.forLocale(locale))
fun DateFormat.Companion.getInstance(calendar: Calendar?, locale: java.util.Locale?): DateFormat = getInstance(calendar, ULocale.forLocale(locale))
fun DateFormat.Companion.getInstanceForSkeleton(skeleton: String?, locale: java.util.Locale?): DateFormat = getPatternInstance(skeleton, ULocale.forLocale(locale))
fun DateFormat.Companion.getInstanceForSkeleton(calendar: Calendar?, skeleton: String?, locale: java.util.Locale?): DateFormat = getPatternInstance(calendar, skeleton, ULocale.forLocale(locale))
fun DateFormat.Companion.getPatternInstance(skeleton: String?, locale: java.util.Locale?): DateFormat = getInstanceForSkeleton(skeleton, locale)
fun DateFormat.Companion.getPatternInstance(calendar: Calendar?, skeleton: String?, locale: java.util.Locale?): DateFormat = getInstanceForSkeleton(calendar, skeleton, locale)
