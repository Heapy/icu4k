// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.Date

/** GregorianCalendar's hybrid-calendar algorithms; Calendar state/dispatch is supplied by its owner. */
internal class GregorianCalendarCore<C : Any>(private val owner: Owner<C>) {
    interface Owner<C : Any> {
        fun cutoverExtendedYear(date: Date): Int
        fun isLeapYear(year: Int): Boolean
        fun getGregorianMonth(): Int
        fun getGregorianDayOfMonth(): Int
        fun getGregorianDayOfYear(): Int
        fun getGregorianYear(): Int
        fun internalSet(field: Int, value: Int)
        fun internalGet(field: Int): Int
        fun internalGet(field: Int, defaultValue: Int): Int
        fun internalGetMonth(): Int
        fun newerField(defaultField: Int, alternateField: Int): Int
        fun baseComputeJulianDay(bestField: Int): Int
        fun get(field: Int): Int
        fun set(field: Int, value: Int)
        fun getYearLength(year: Int): Int
        fun getFirstDayOfWeek(): Int
        fun getMinimalDaysInFirstWeek(): Int
        fun weekNumber(dayOfPeriod: Int, dayOfWeek: Int): Int
        fun baseRoll(field: Int, amount: Int)
        fun getMinimum(field: Int): Int
        fun baseGetActualMaximum(field: Int): Int
        fun cloneCalendar(): C
        fun setLenient(calendar: C, value: Boolean)
        fun get(calendar: C, field: Int): Int
        fun set(calendar: C, field: Int, value: Int)
        fun getTime(calendar: C): Date
        fun setTime(calendar: C, date: Date)
        fun zoneUsesDaylightTime(): Boolean
        fun complete()
    }

    private var gregorianCutover = -12219292800000L
    private var cutoverJulianDay = 2299161
    private var gregorianCutoverYear = 1582
    var isGregorian = false
    var invertGregorian = false

    fun setGregorianChange(date: Date?) {
        gregorianCutover = date!!.getTime()
        when {
            gregorianCutover <= Grego.MIN_MILLIS -> { gregorianCutoverYear = Int.MIN_VALUE; cutoverJulianDay = Int.MIN_VALUE }
            gregorianCutover >= Grego.MAX_MILLIS -> { gregorianCutoverYear = Int.MAX_VALUE; cutoverJulianDay = Int.MAX_VALUE }
            else -> {
                // Retain the source setter's epoch-day value (without adding EPOCH_JULIAN_DAY).
                cutoverJulianDay = Grego.floorDivide(gregorianCutover, 86400000).toInt()
                gregorianCutoverYear = owner.cutoverExtendedYear(date)
            }
        }
    }
    fun getGregorianChange(): Date = Date(gregorianCutover)
    fun isLeapYear(year: Int): Boolean = if (year >= gregorianCutoverYear) Grego.isLeapYear(year) else year % 4 == 0
    fun hashCode(baseHashCode: Int): Int = baseHashCode xor gregorianCutover.toInt()
    fun hasSameCutover(other: GregorianCalendarCore<*>): Boolean = gregorianCutover == other.gregorianCutover

    fun handleGetLimit(field: Int, limitType: Int): Int {
        if (field !in limits.indices || limitType !in limits[field].indices) throw IndexOutOfBoundsException()
        return limits[field][limitType]
    }
    fun handleGetMonthLength(extendedYear: Int, month: Int): Int {
        var year = extendedYear
        var m = month
        if (m < 0 || m > 11) { val q = floorDivide(m, 12); year += q; m -= q * 12 }
        return months[m][if (owner.isLeapYear(year)) 1 else 0]
    }
    fun handleGetYearLength(year: Int): Int = if (owner.isLeapYear(year)) 366 else 365

    fun handleComputeFields(julianDay: Int) {
        var year: Int
        val month: Int
        val dayOfMonth: Int
        var dayOfYear: Int
        if (julianDay >= cutoverJulianDay) {
            month = owner.getGregorianMonth()
            dayOfMonth = owner.getGregorianDayOfMonth()
            dayOfYear = owner.getGregorianDayOfYear()
            year = owner.getGregorianYear()
        } else {
            val epochDay = (julianDay - (1721426 - 2)).toLong()
            year = Grego.floorDivide(4 * epochDay + 1464, 1461).toInt()
            val january1 = 365L * (year - 1L) + Grego.floorDivide(year - 1L, 4)
            dayOfYear = (epochDay - january1).toInt()
            val leap = year and 3 == 0
            val correction = if (dayOfYear >= if (leap) 60 else 59) { if (leap) 1 else 2 } else 0
            month = (12 * (dayOfYear + correction) + 6) / 367
            dayOfMonth = dayOfYear - months[month][if (leap) 3 else 2] + 1
            dayOfYear++
        }
        owner.internalSet(MONTH, month)
        owner.internalSet(ORDINAL_MONTH, month)
        owner.internalSet(DAY_OF_MONTH, dayOfMonth)
        owner.internalSet(DAY_OF_YEAR, dayOfYear)
        owner.internalSet(EXTENDED_YEAR, year)
        var era = AD
        if (year < 1) { era = BC; year = 1 - year }
        owner.internalSet(ERA, era)
        owner.internalSet(YEAR, year)
    }

    fun handleGetExtendedYear(): Int = if (owner.newerField(EXTENDED_YEAR, YEAR) == EXTENDED_YEAR) {
        owner.internalGet(EXTENDED_YEAR, 1970)
    } else {
        if (owner.internalGet(ERA, AD) == BC) 1 - owner.internalGet(YEAR, 1) else owner.internalGet(YEAR, 1970)
    }

    fun handleComputeJulianDay(bestField: Int): Int {
        invertGregorian = false
        var day = owner.baseComputeJulianDay(bestField)
        if (isGregorian != (day >= cutoverJulianDay)) {
            invertGregorian = true
            day = owner.baseComputeJulianDay(bestField)
        }
        return day
    }

    fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int {
        var year = extendedYear
        var m = month
        if (m < 0 || m > 11) { val q = floorDivide(m, 12); year += q; m -= q * 12 }
        var leap = year % 4 == 0
        val y = year - 1
        var day = 365 * y + floorDivide(y, 4) + (1721426 - 3)
        isGregorian = year >= gregorianCutoverYear
        if (invertGregorian) isGregorian = !isGregorian
        if (isGregorian) {
            leap = leap && (year % 100 != 0 || year % 400 == 0)
            day += floorDivide(y, 400) - floorDivide(y, 100) + 2
        }
        if (m != 0) day += months[m][if (leap) 3 else 2]
        return day
    }

    fun roll(field: Int, amount: Int) {
        if (field != WEEK_OF_YEAR) { owner.baseRoll(field, amount); return }
        var week = owner.get(WEEK_OF_YEAR)
        val isoYear = owner.get(YEAR_WOY)
        var isoDay = owner.internalGet(DAY_OF_YEAR)
        if (owner.internalGetMonth() == 0) {
            if (week >= 52) isoDay += owner.getYearLength(isoYear)
        } else if (week == 1) isoDay -= owner.getYearLength(isoYear - 1)
        week += amount
        if (week < 1 || week > 52) {
            var lastDay = owner.getYearLength(isoYear)
            var lastRelativeDow = (lastDay - isoDay + owner.internalGet(DAY_OF_WEEK) - owner.getFirstDayOfWeek()) % 7
            if (lastRelativeDow < 0) lastRelativeDow += 7
            if (6 - lastRelativeDow >= owner.getMinimalDaysInFirstWeek()) lastDay -= 7
            val lastWeek = owner.weekNumber(lastDay, lastRelativeDow + 1)
            week = (week + lastWeek - 1) % lastWeek + 1
        }
        owner.set(WEEK_OF_YEAR, week)
        owner.set(YEAR, isoYear)
    }

    fun getActualMinimum(field: Int): Int = owner.getMinimum(field)
    fun getActualMaximum(field: Int): Int {
        if (field != YEAR) return owner.baseGetActualMaximum(field)
        val calendar = owner.cloneCalendar()
        owner.setLenient(calendar, true)
        val era = owner.get(calendar, ERA)
        val date = owner.getTime(calendar)
        var low = limits[YEAR][1]
        var high = limits[YEAR][2] + 1
        while (low + 1 < high) {
            val year = (low + high) / 2
            owner.set(calendar, YEAR, year)
            if (owner.get(calendar, YEAR) == year && owner.get(calendar, ERA) == era) low = year
            else { high = year; owner.setTime(calendar, date) }
        }
        return low
    }

    fun inDaylightTime(): Boolean {
        if (!owner.zoneUsesDaylightTime()) return false
        owner.complete()
        return owner.internalGet(DST_OFFSET) != 0
    }

    fun copyFor(owner: Owner<C>): GregorianCalendarCore<C> = GregorianCalendarCore(owner).also {
        it.gregorianCutover = gregorianCutover; it.cutoverJulianDay = cutoverJulianDay
        it.gregorianCutoverYear = gregorianCutoverYear; it.isGregorian = isGregorian; it.invertGregorian = invertGregorian
    }

    companion object {
        const val BC = 0; const val AD = 1
        private const val ERA = 0; private const val YEAR = 1; private const val MONTH = 2
        private const val WEEK_OF_YEAR = 3; private const val DAY_OF_MONTH = 5; private const val DAY_OF_YEAR = 6
        private const val DAY_OF_WEEK = 7; private const val DST_OFFSET = 16; private const val YEAR_WOY = 17
        private const val EXTENDED_YEAR = 19; private const val ORDINAL_MONTH = 23
        private fun floorDivide(numerator: Int, denominator: Int): Int =
            if (numerator >= 0) numerator / denominator else (numerator + 1) / denominator - 1
        private val months = arrayOf(
            intArrayOf(31,31,0,0), intArrayOf(28,29,31,31), intArrayOf(31,31,59,60), intArrayOf(30,30,90,91),
            intArrayOf(31,31,120,121), intArrayOf(30,30,151,152), intArrayOf(31,31,181,182), intArrayOf(31,31,212,213),
            intArrayOf(30,30,243,244), intArrayOf(31,31,273,274), intArrayOf(30,30,304,305), intArrayOf(31,31,334,335),
        )
        private val limits = arrayOf(
            intArrayOf(0,0,1,1), intArrayOf(1,1,5828963,5838270), intArrayOf(0,0,11,11), intArrayOf(1,1,52,53),
            intArrayOf(), intArrayOf(1,1,28,31), intArrayOf(1,1,365,366), intArrayOf(), intArrayOf(-1,-1,4,5),
            intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(),
            intArrayOf(-5838270,-5838270,5828964,5838271), intArrayOf(), intArrayOf(-5838269,-5838269,5828963,5838270),
            intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(0,0,11,11),
        )
    }
}
