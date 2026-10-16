// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.util.Date

/** Base Calendar equality, configuration equivalence, hashes and temporal comparisons. */
internal class CalendarComparisonCore<C : Any>(private val owner: Owner<C>) {
    interface Owner<C : Any> {
        val self: C
        fun sameRuntimeClass(value: Any): Boolean
        fun asCalendar(value: Any?): C?
        fun isEquivalentTo(calendar: C): Boolean
        fun getTimeInMillis(calendar: C): Long
        fun getTime(calendar: C): Date
        fun isLenient(calendar: C): Boolean
        fun getFirstDayOfWeek(calendar: C): Int
        fun getMinimalDaysInFirstWeek(calendar: C): Int
        fun getTimeZone(calendar: C): Any?
        fun getRepeatedWallTimeOption(calendar: C): Int
        fun getSkippedWallTimeOption(calendar: C): Int
        fun storedLenient(): Boolean
        fun storedFirstDayOfWeek(): Int
        fun storedMinimalDaysInFirstWeek(): Int
        fun storedRepeatedWallTime(): Int
        fun storedSkippedWallTime(): Int
        fun storedZoneHashCode(): Int
    }

    fun calendarEquals(value: Any?): Boolean {
        if (value == null) return false
        if (owner.self === value) return true
        if (!owner.sameRuntimeClass(value)) return false
        val calendar = owner.asCalendar(value)!!
        return owner.isEquivalentTo(calendar) && owner.getTimeInMillis(owner.self) == owner.getTime(calendar).getTime()
    }

    fun isEquivalentTo(other: C?): Boolean {
        val calendar = other!!
        return owner.sameRuntimeClass(calendar) &&
            owner.isLenient(owner.self) == owner.isLenient(calendar) &&
            owner.getFirstDayOfWeek(owner.self) == owner.getFirstDayOfWeek(calendar) &&
            owner.getMinimalDaysInFirstWeek(owner.self) == owner.getMinimalDaysInFirstWeek(calendar) &&
            zonesEqual(owner.getTimeZone(owner.self), owner.getTimeZone(calendar)) &&
            owner.getRepeatedWallTimeOption(owner.self) == owner.getRepeatedWallTimeOption(calendar) &&
            owner.getSkippedWallTimeOption(owner.self) == owner.getSkippedWallTimeOption(calendar)
    }

    private fun zonesEqual(zone: Any?, other: Any?): Boolean = zone!!.equals(other)

    fun calendarHashCode(): Int = (if (owner.storedLenient()) 1 else 0) or
        (owner.storedFirstDayOfWeek() shl 1) or (owner.storedMinimalDaysInFirstWeek() shl 4) or
        (owner.storedRepeatedWallTime() shl 7) or (owner.storedSkippedWallTime() shl 9) or
        (owner.storedZoneHashCode() shl 11)

    fun before(value: Any?): Boolean = compare(value) < 0
    fun after(value: Any?): Boolean = compare(value) > 0

    fun compareTo(other: C?): Int {
        val difference = owner.getTimeInMillis(owner.self) - owner.getTimeInMillis(other!!)
        return if (difference < 0) -1 else if (difference > 0) 1 else 0
    }

    private fun compare(value: Any?): Long {
        val calendar = owner.asCalendar(value)
        val millis = when {
            calendar != null -> owner.getTimeInMillis(calendar)
            value is Date -> value.getTime()
            else -> throw IllegalArgumentException("${value}is not a Calendar or Date")
        }
        return owner.getTimeInMillis(owner.self) - millis
    }
}
