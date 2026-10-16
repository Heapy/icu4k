// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2000-2014, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.CalendarAstronomer
import io.github.heapy.icu4k.impl.CalendarCache
import io.github.heapy.icu4k.text.RbnfMath
import kotlin.math.PI
import kotlin.math.floor

/** ChineseCalendar's lunisolar calculations and leap-month field/operation hooks. */
internal class ChineseCalendarCore(private val owner: Owner, private val astroOffset: (Long)->Int = { 28800000 }) {
    interface Copy {
        fun set(field: Int, value: Int)
        fun get(field: Int): Int
        fun roll(field: Int, amount: Int)
        fun setLenient(value: Boolean)
        fun prepareGetActual(field: Int, minimum: Boolean)
    }
    interface Owner: Copy {
        fun internalGet(field: Int): Int
        fun internalGet(field: Int, defaultValue: Int): Int
        fun internalSet(field: Int, value: Int)
        fun newestStamp(first: Int, last: Int, best: Int): Int
        fun getStamp(field: Int): Int
        fun resolveFields(table: Array<Array<IntArray>>): Int
        fun computeGregorianFields(julianDay: Int)
        fun getGregorianYear(): Int
        fun getGregorianMonth(): Int
        fun complete()
        fun getActualMaximum(field: Int): Int
        fun superGetActualMaximum(field: Int): Int
        fun superAdd(field: Int, amount: Int)
        fun superRoll(field: Int, amount: Int)
        fun superGetTemporalMonthCode(): String
        fun superSetTemporalMonthCode(value: String?)
        fun cloneCalendar(): Copy
    }
    private var winterSolsticeCache = CalendarCache()
    private var newYearCache = CalendarCache()
    private var hasLeapMonthBetweenWinterSolstices = false
    fun copyForOwner(newOwner: Owner): ChineseCalendarCore = ChineseCalendarCore(newOwner, astroOffset).also {
        // Calendar.clone is shallow for ChineseCalendar's per-instance caches.
        it.winterSolsticeCache = winterSolsticeCache; it.newYearCache = newYearCache
        it.hasLeapMonthBetweenWinterSolstices = hasLeapMonthBetweenWinterSolstices
    }
    fun handleGetLimit(field: Int, type: Int): Int {
        if (field !in LIMITS.indices || type !in LIMITS[field].indices) throw IndexOutOfBoundsException()
        return LIMITS[field][type]
    }
    fun handleGetExtendedYear(): Int = if (owner.newestStamp(0, 1, 0) <= owner.getStamp(19)) owner.internalGet(19, 1)
        else (owner.internalGet(0, 1) - 1) * 60 + owner.internalGet(1, 1) - 2636 - 1
    fun handleGetMonthLength(year: Int, month: Int): Int = handleGetMonthLengthWithLeap(year, month, owner.internalGet(22))
    private fun handleGetMonthLengthWithLeap(year: Int, month: Int, leap: Int): Int {
        val start = handleComputeMonthStartWithLeap(year, month, leap) - EPOCH_JULIAN_DAY + 1
        return newMoonNear(start + SYNODIC_GAP, true) - start
    }
    fun getFieldResolutionTable(): Array<Array<IntArray>> = CHINESE_DATE_PRECEDENCE
    private fun offsetMonth(moon: Int, dom: Int, delta: Int) {
        var newMoon = moon + (CalendarAstronomer.SYNODIC_MONTH * (delta - 0.5)).toInt()
        newMoon = newMoonNear(newMoon, true)
        val jd = newMoon + EPOCH_JULIAN_DAY - 1 + dom
        if (dom > 29) {
            owner.set(20, jd - 1)
            owner.complete()
            if (owner.getActualMaximum(5) >= dom) owner.set(20, jd)
        } else owner.set(20, jd)
    }
    fun add(field: Int, amount: Int) {
        if (field == 2 || field == 23) {
            if (amount != 0) {
                val dom = owner.get(5)
                val day = owner.get(20) - EPOCH_JULIAN_DAY
                offsetMonth(day - dom + 1, dom, amount)
            }
        } else owner.superAdd(field, amount)
    }
    fun roll(field: Int, amount: Int) {
        if (field == 2 || field == 23) {
            if (amount != 0) {
                val dom = owner.get(5)
                val day = owner.get(20) - EPOCH_JULIAN_DAY
                val moon = day - dom + 1
                var m = owner.get(2)
                if (hasLeapMonthBetweenWinterSolstices) {
                    if (owner.get(22) == 1) m++
                    else {
                        var moon1 = moon - (CalendarAstronomer.SYNODIC_MONTH * (m - 0.5)).toInt()
                        moon1 = newMoonNear(moon1, true)
                        if (isLeapMonthBetween(moon1, moon)) m++
                    }
                }
                val n = if (hasLeapMonthBetweenWinterSolstices) 13 else 12
                var newM = (m + amount) % n
                if (newM < 0) newM += n
                if (newM != m) offsetMonth(moon, dom, newM - m)
            }
        } else owner.superRoll(field, amount)
    }
    fun daysToMillis(days: Int): Long {
        val millis = days * ONE_DAY
        return millis - astroOffset(millis)
    }
    fun millisToDays(millis: Long): Int = CalendarDateCore.floorDivide(millis + astroOffset(millis), ONE_DAY).toInt()
    fun winterSolstice(year: Int): Int {
        var cached = winterSolsticeCache.get(year.toLong())
        if (cached == CalendarCache.EMPTY) {
            val ms = daysToMillis(CalendarDateCore.computeGregorianMonthStart(year, 11) + 1 - EPOCH_JULIAN_DAY)
            cached = millisToDays(CalendarAstronomer(ms).getSunTime(CalendarAstronomer.WINTER_SOLSTICE, true)).toLong()
            winterSolsticeCache.put(year.toLong(), cached)
        }
        return cached.toInt()
    }
    fun newMoonNear(days: Int, after: Boolean): Int = millisToDays(CalendarAstronomer(daysToMillis(days)).getMoonTime(CalendarAstronomer.NEW_MOON, after))
    fun synodicMonthsBetween(day1: Int, day2: Int): Int = RbnfMath.roundToLong((day2 - day1) / CalendarAstronomer.SYNODIC_MONTH).toInt()
    fun majorSolarTerm(days: Int): Int {
        var term = (floor(6 * CalendarAstronomer(daysToMillis(days)).getSunLongitude() / PI).toInt() + 2) % 12
        if (term < 1) term += 12
        return term
    }
    fun hasNoMajorSolarTerm(newMoon: Int): Boolean {
        val first = majorSolarTerm(newMoon)
        val next = newMoonNear(newMoon + SYNODIC_GAP, true)
        return first == majorSolarTerm(next)
    }
    fun isLeapMonthBetween(first: Int, last: Int): Boolean {
        if (synodicMonthsBetween(first, last) >= 50) throw IllegalArgumentException("isLeapMonthBetween($first, $last): Invalid parameters")
        return last >= first && (isLeapMonthBetween(first, newMoonNear(last - SYNODIC_GAP, false)) || hasNoMajorSolarTerm(last))
    }
    fun handleComputeFields(julianDay: Int) {
        val days = julianDay - EPOCH_JULIAN_DAY
        val year = owner.getGregorianYear()
        val gmonth = owner.getGregorianMonth()
        val info = computeMonthInfo(days, year)
        var extended = year - 1
        var cycleYear = year - -2636
        if (info.month < 10 || gmonth >= 6) { extended++; cycleYear++ }
        val dom = days - info.thisMoon + 1
        val remainder = IntArray(1)
        val cycle = CalendarDateCore.floorDivide(cycleYear - 1, 60, remainder)
        var start = newYear(year)
        if (days < start) start = newYear(year - 1)
        hasLeapMonthBetweenWinterSolstices = info.hasLeapMonthBetweenWinterSolstices
        owner.internalSet(19, extended); owner.internalSet(0, cycle + 1); owner.internalSet(1, remainder[0] + 1)
        owner.internalSet(2, info.month); owner.internalSet(23, info.ordinalMonth); owner.internalSet(5, dom)
        owner.internalSet(22, if (info.isLeapMonth) 1 else 0); owner.internalSet(6, days - start + 1)
    }
    class MonthInfo(val month: Int, val ordinalMonth: Int, val thisMoon: Int, val isLeapMonth: Boolean, val hasLeapMonthBetweenWinterSolstices: Boolean)
    fun computeMonthInfo(days: Int, year: Int): MonthInfo {
        val before: Int
        var after = winterSolstice(year)
        if (days < after) before = winterSolstice(year - 1)
        else { before = after; after = winterSolstice(year + 1) }
        val firstMoon = newMoonNear(before + 1, true)
        val lastMoon = newMoonNear(after + 1, false)
        val thisMoon = newMoonNear(days + 1, false)
        val hasLeap = synodicMonthsBetween(firstMoon, lastMoon) == 12
        var month = synodicMonthsBetween(firstMoon, thisMoon)
        var start = newYear(year)
        if (days < start) start = newYear(year - 1)
        if (hasLeap && isLeapMonthBetween(firstMoon, thisMoon)) month--
        if (month < 1) month += 12
        var ordinal = synodicMonthsBetween(start, thisMoon)
        if (ordinal < 0) ordinal += 12
        val leap = hasLeap && hasNoMajorSolarTerm(thisMoon) && !isLeapMonthBetween(firstMoon, newMoonNear(thisMoon - SYNODIC_GAP, false))
        return MonthInfo(month - 1, ordinal, thisMoon, leap, hasLeap)
    }
    fun newYear(year: Int): Int {
        var cached = newYearCache.get(year.toLong())
        if (cached == CalendarCache.EMPTY) {
            val before = winterSolstice(year - 1)
            val after = winterSolstice(year)
            val moon1 = newMoonNear(before + 1, true)
            val moon2 = newMoonNear(moon1 + SYNODIC_GAP, true)
            val moon11 = newMoonNear(after + 1, false)
            cached = (if (synodicMonthsBetween(moon1, moon11) == 12 && (hasNoMajorSolarTerm(moon1) || hasNoMajorSolarTerm(moon2))) newMoonNear(moon2 + SYNODIC_GAP, true) else moon2).toLong()
            newYearCache.put(year.toLong(), cached)
        }
        return cached.toInt()
    }
    fun handleComputeMonthStart(year: Int, month: Int, useMonth: Boolean): Int = handleComputeMonthStartWithLeap(year, month, if (useMonth) owner.internalGet(22) else 0)
    private fun handleComputeMonthStartWithLeap(year: Int, month: Int, leap: Int): Int {
        var y = year; var m = month
        if (m < 0 || m > 11) { val remainder = IntArray(1); y += CalendarDateCore.floorDivide(m, 12, remainder); m = remainder[0] }
        val start = newYear(y)
        var moon = newMoonNear(start + m * 29, true)
        var julian = moon + EPOCH_JULIAN_DAY
        owner.computeGregorianFields(julian)
        val info = computeMonthInfo(moon, owner.getGregorianYear())
        if (m != info.month || info.isLeapMonth != (leap != 0)) { moon = newMoonNear(moon + SYNODIC_GAP, true); julian = moon + EPOCH_JULIAN_DAY }
        return julian - 1
    }
    fun getType(): String = "chinese"
    fun haveDefaultCentury(): Boolean = false
    fun inTemporalLeapYear(): Boolean = owner.getActualMaximum(6) > 360
    fun getTemporalMonthCode(): String {
        if (owner.get(22) != 0) {
            val month = owner.get(2)
            if (month !in LEAP_CODES.indices) throw IndexOutOfBoundsException()
            return LEAP_CODES[month]
        }
        return owner.superGetTemporalMonthCode()
    }
    fun setTemporalMonthCode(value: String?) {
        val code = value!!
        if (code.length != 4 || code[0] != 'M' || code[3] != 'L') { owner.set(22, 0); owner.superSetTemporalMonthCode(value); return }
        for (month in LEAP_CODES.indices) if (code == LEAP_CODES[month]) { owner.set(2, month); owner.set(22, 1); return }
        throw IllegalArgumentException("Incorrect temporal Month code: $code")
    }
    fun internalGetMonth(): Int {
        if (owner.resolveFields(CalendarFieldsCore.MONTH_PRECEDENCE) == 2) return owner.internalGet(2)
        val temp = owner.cloneCalendar()
        temp.set(2, 0); temp.set(22, 0); temp.set(5, 1)
        temp.roll(2, owner.internalGet(23))
        owner.internalSet(22, temp.get(22))
        val month = temp.get(2)
        owner.internalSet(2, month)
        return month
    }
    fun internalGetMonth(defaultValue: Int): Int = if (owner.resolveFields(CalendarFieldsCore.MONTH_PRECEDENCE) == 2) owner.internalGet(2, defaultValue) else internalGetMonth()
    fun getActualMaximum(field: Int): Int {
        if (field == 5) {
            val copy = owner.cloneCalendar()
            copy.setLenient(true); copy.prepareGetActual(field, false)
            val year = copy.get(19); val month = copy.get(2); val leap = copy.get(22)
            return handleGetMonthLengthWithLeap(year, month, leap)
        }
        return owner.superGetActualMaximum(field)
    }
    companion object {
        private const val EPOCH_JULIAN_DAY = 2440588
        private const val ONE_DAY = 86400000L
        private const val SYNODIC_GAP = 25
        private val LEAP_CODES = Array(12) { "M" + (it + 1).toString().padStart(2, '0') + "L" }
        private val LIMITS = arrayOf(
            intArrayOf(1,1,83333,83333),intArrayOf(1,1,60,60),intArrayOf(0,0,11,11),intArrayOf(1,1,50,55),
            intArrayOf(),intArrayOf(1,1,29,30),intArrayOf(1,1,353,385),intArrayOf(),intArrayOf(-1,-1,5,5),
            intArrayOf(),intArrayOf(),intArrayOf(),intArrayOf(),intArrayOf(),intArrayOf(),intArrayOf(),intArrayOf(),
            intArrayOf(-5000000,-5000000,5000000,5000000),intArrayOf(),intArrayOf(-5000000,-5000000,5000000,5000000),
            intArrayOf(),intArrayOf(),intArrayOf(0,0,1,1),intArrayOf(0,0,11,12),
        )
        val CHINESE_DATE_PRECEDENCE = arrayOf(
            arrayOf(intArrayOf(5),intArrayOf(3,7),intArrayOf(4,7),intArrayOf(8,7),intArrayOf(3,18),intArrayOf(4,18),intArrayOf(8,18),intArrayOf(6),intArrayOf(32 or 5,22)),
            arrayOf(intArrayOf(3),intArrayOf(4),intArrayOf(8),intArrayOf(32 or 8,7),intArrayOf(32 or 8,18)),
        )
    }
}
