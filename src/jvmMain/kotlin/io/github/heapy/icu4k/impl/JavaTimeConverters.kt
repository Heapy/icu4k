// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import java.time.*
import java.time.chrono.ChronoLocalDate
import java.time.chrono.ChronoLocalDateTime
import java.time.temporal.ChronoField.MILLI_OF_SECOND
import java.time.temporal.Temporal

@Deprecated("This API is ICU internal only.")
object JavaTimeConverters {
    private const val HOUR=3600000L
    private const val DAY=24*HOUR
    @JvmStatic fun temporalToCalendar(value: ZonedDateTime): Calendar = millis(value.toEpochSecond()*1000+value.get(MILLI_OF_SECOND),zoneIdToTimeZone(value.zone))
    @JvmStatic fun temporalToCalendar(value: OffsetDateTime): Calendar = millis(value.toEpochSecond()*1000+value.get(MILLI_OF_SECOND),zoneOffsetToTimeZone(value.offset))
    @JvmStatic fun temporalToCalendar(value: OffsetTime): Calendar = temporalToCalendar(value.atDate(LocalDate.now()))
    @JvmStatic fun temporalToCalendar(value: LocalTime): Calendar = millis(value.toNanoOfDay()/1000000)
    @JvmStatic fun temporalToCalendar(value: LocalDateTime): Calendar {
        val offset=ZoneId.systemDefault().rules.getOffset(value)
        return millis(value.toEpochSecond(offset)*1000+value.get(MILLI_OF_SECOND),TimeZone.getDefault())
    }
    internal fun temporalToCalendar(value: ChronoLocalDate): Calendar = millis(value.toEpochDay()*DAY)
    @JvmStatic fun temporalToCalendar(value: Temporal): Calendar = when(value) {
        is Instant -> throw IllegalArgumentException("java.time.Instant cannot be formatted, it does not have enough information")
        is ZonedDateTime -> temporalToCalendar(value)
        is OffsetDateTime -> temporalToCalendar(value)
        is OffsetTime -> temporalToCalendar(value)
        is LocalDate -> temporalToCalendar(value as ChronoLocalDate)
        is LocalDateTime -> temporalToCalendar(value)
        is LocalTime -> temporalToCalendar(value)
        is ChronoLocalDate -> temporalToCalendar(value)
        // Source has no ChronoLocalDateTime overload: this branch recursively calls Temporal.
        is ChronoLocalDateTime<*> -> temporalToCalendar(value as Temporal)
        else -> throw IllegalArgumentException("This type cannot be formatted: "+value.javaClass.name)
    }
    @JvmStatic fun zoneIdToTimeZone(value: ZoneId): TimeZone = TimeZone.getTimeZone(value.id)
    @JvmStatic fun zoneOffsetToTimeZone(value: ZoneOffset): TimeZone = SimpleTimeZone(value.totalSeconds*1000,value.id)
    @JvmStatic fun dayOfWeekToCalendar(value: DayOfWeek): Calendar = millis(HOUR*12+(3+value.value)*DAY)
    @JvmStatic fun monthToCalendar(value: Month): Calendar = millis(HOUR*12+(value.value-1)*DAY*31)
    private fun millis(value: Long,zone: TimeZone=TimeZone.GMT_ZONE): Calendar = GregorianCalendar(zone,ULocale.US).apply {
        setGregorianChange(Date(Long.MIN_VALUE));setTimeInMillis(value)
    }
    @JvmStatic fun convertCalendar(input: java.util.Calendar): Calendar {
        val zone=TimeZone.getTimeZone(input.timeZone.id)
        val result=when(input.calendarType) {
            "iso8601" -> GregorianCalendar(zone).apply { setGregorianChange(Date(Long.MIN_VALUE)) }
            "buddhist" -> BuddhistCalendar(zone)
            "japanese" -> JapaneseCalendar(zone)
            else -> GregorianCalendar(zone)
        }
        result.setLenient(input.isLenient);result.setFirstDayOfWeek(input.firstDayOfWeek)
        result.setMinimalDaysInFirstWeek(input.minimalDaysInFirstWeek);result.setTimeInMillis(input.timeInMillis)
        return result
    }
}
