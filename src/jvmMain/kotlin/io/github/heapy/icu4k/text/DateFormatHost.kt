// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text
import io.github.heapy.icu4k.impl.JavaTimeConverters
import io.github.heapy.icu4k.util.Calendar
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
