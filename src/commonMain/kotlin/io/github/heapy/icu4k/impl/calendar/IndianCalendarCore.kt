// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2011, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

/** IndianCalendar's Saka-year, month and Julian-day algorithms. */
internal class IndianCalendarCore(private val owner: Owner) {
    interface Owner {
        fun newerField(defaultField: Int, alternateField: Int): Int
        fun internalGet(field: Int, defaultValue: Int): Int
        fun internalSet(field: Int, value: Int)
        fun getGregorianYear(): Int
        fun computeGregorianMonthStart(year: Int, month: Int): Int
        fun baseGetYearLength(year: Int): Int
    }
    fun handleGetExtendedYear(): Int = if (owner.newerField(19, 1) == 19) owner.internalGet(19, 1) else owner.internalGet(1, 1)
    fun handleGetYearLength(year: Int): Int = owner.baseGetYearLength(year)
    fun handleGetMonthLength(extendedYear: Int, month: Int): Int {
        var year = extendedYear
        var m = month
        if (m < 0 || m > 11) {
            val remainder = IntArray(1)
            year += CalendarDateCore.floorDivide(m, 12, remainder)
            m = remainder[0]
        }
        if (CalendarDateCore.isGregorianLeapYear(year + 78) && m == 0) return 31
        if (m in 1..5) return 31
        return 30
    }
    fun handleComputeFields(julianDay: Int) {
        val gregorianYear = owner.getGregorianYear()
        var year = gregorianYear - 78
        val start = gregorianToJD(gregorianYear, 0, 1)
        var yday = (julianDay - start).toInt()
        val leapMonth: Int
        if (yday < 80) {
            year -= 1
            leapMonth = if (CalendarDateCore.isGregorianLeapYear(gregorianYear - 1)) 31 else 30
            yday += leapMonth + 31 * 5 + 30 * 3 + 10
        } else {
            leapMonth = if (CalendarDateCore.isGregorianLeapYear(gregorianYear)) 31 else 30
            yday -= 80
        }
        val month: Int
        val day: Int
        if (yday < leapMonth) { month = 0; day = yday + 1 }
        else {
            var mday = yday - leapMonth
            if (mday < 31 * 5) { month = mday / 31 + 1; day = mday % 31 + 1 }
            else { mday -= 31 * 5; month = mday / 30 + 6; day = mday % 30 + 1 }
        }
        owner.internalSet(0, 0)
        owner.internalSet(19, year)
        owner.internalSet(1, year)
        owner.internalSet(2, month)
        owner.internalSet(23, month)
        owner.internalSet(5, day)
        owner.internalSet(6, yday + 1)
    }
    fun handleGetLimit(field: Int, type: Int): Int {
        if (field !in LIMITS.indices || type !in LIMITS[field].indices) throw IndexOutOfBoundsException()
        return LIMITS[field][type]
    }
    @Suppress("UNUSED_PARAMETER")
    fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int {
        var year = extendedYear
        var m = month
        if (m < 0 || m > 11) { year += m / 12; m %= 12 }
        return indianToJD(year, m + 1, 1).toInt()
    }
    private fun indianToJD(year: Int, month: Int, day: Int): Double {
        val gregorianYear = year + 78
        val leapMonth: Int
        val start: Double
        if (CalendarDateCore.isGregorianLeapYear(gregorianYear)) {
            leapMonth = 31; start = gregorianToJD(gregorianYear, 2, 21)
        } else { leapMonth = 30; start = gregorianToJD(gregorianYear, 2, 22) }
        if (month == 1) return start + (day - 1)
        var jd = start + leapMonth
        var m = month - 2
        m = minOf(m, 5)
        jd += m * 31
        if (month >= 8) { m = month - 7; jd += m * 30 }
        jd += day - 1
        return jd
    }
    private fun gregorianToJD(year: Int, month: Int, day: Int): Double = (owner.computeGregorianMonthStart(year, month) + day).toDouble() - 0.5
    fun getRelatedYearDifference(): Int = 79
    fun getType(): String = "indian"

    companion object {
        private val LIMITS = arrayOf(
            intArrayOf(0,0,0,0), intArrayOf(-5000000,-5000000,5000000,5000000), intArrayOf(0,0,11,11), intArrayOf(1,1,52,53),
            intArrayOf(), intArrayOf(1,1,30,31), intArrayOf(1,1,365,366), intArrayOf(), intArrayOf(-1,-1,5,5),
            intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(),
            intArrayOf(-5000000,-5000000,5000000,5000000), intArrayOf(), intArrayOf(-5000000,-5000000,5000000,5000000),
            intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(0,0,11,11),
        )
    }
}
