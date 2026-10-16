// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2014, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

/** PersianCalendar's corrected 33-year arithmetic, limits and field conversion. */
internal class PersianCalendarCore(private val owner: Owner) {
    interface Owner {
        fun newerField(defaultField: Int, alternateField: Int): Int
        fun internalGet(field: Int, defaultValue: Int): Int
        fun internalSet(field: Int, value: Int)
    }
    fun handleGetExtendedYear(): Int = if (owner.newerField(19, 1) == 19) owner.internalGet(19, 1) else owner.internalGet(1, 1)
    fun handleGetLimit(field: Int, type: Int): Int {
        if (field !in LIMITS.indices || type !in LIMITS[field].indices) throw IndexOutOfBoundsException()
        return LIMITS[field][type]
    }
    fun handleGetMonthLength(extendedYear: Int, month: Int): Int {
        var year = extendedYear
        var m = month
        if (m < 0 || m > 11) {
            val remainder = IntArray(1)
            year += CalendarDateCore.floorDivide(m, 12, remainder)
            m = remainder[0]
        }
        return MONTH_COUNT[m][if (isLeapYear(year)) 1 else 0]
    }
    fun handleGetYearLength(year: Int): Int = if (isLeapYear(year)) 366 else 365
    @Suppress("UNUSED_PARAMETER")
    fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int {
        var year = extendedYear
        var m = month
        if (m < 0 || m > 11) {
            val remainder = IntArray(1)
            year += CalendarDateCore.floorDivide(m, 12, remainder)
            m = remainder[0]
        }
        var day = 1948319L + firstJulianOfYear(year)
        if (m != 0) day += MONTH_COUNT[m][2]
        return day.toInt()
    }
    fun handleComputeFields(julianDay: Int) {
        // The subtraction wraps as Int before widening in the source.
        val days = (julianDay - 1948320).toLong()
        var year = 1 + CalendarDateCore.floorDivide(33 * days + 3, 12053L).toInt()
        val first = firstJulianOfYear(year)
        var dayOfYear = (days - first).toInt()
        if (dayOfYear == 365 && corrected(year)) { year++; dayOfYear = 0 }
        val month = if (dayOfYear < 216) dayOfYear / 31 else (dayOfYear - 6) / 30
        ++dayOfYear
        if (month !in MONTH_COUNT.indices) throw IndexOutOfBoundsException()
        val day = dayOfYear - MONTH_COUNT[month][2]
        owner.internalSet(0, 0)
        owner.internalSet(1, year)
        owner.internalSet(19, year)
        owner.internalSet(2, month)
        owner.internalSet(23, month)
        owner.internalSet(5, day)
        owner.internalSet(6, dayOfYear)
    }
    fun getRelatedYearDifference(): Int = 622
    fun getType(): String = "persian"
    companion object {
        fun isLeapYear(year: Int): Boolean {
            if (corrected(year)) return false
            if (corrected(year - 1)) return true
            val remainder = IntArray(1)
            CalendarDateCore.floorDivide(25 * year + 11, 33, remainder)
            return remainder[0] < 8
        }
        fun firstJulianOfYear(year: Int): Long {
            var day = 365L * (year - 1L) + CalendarDateCore.floorDivide(8L * year + 21, 33L)
            if (corrected(year - 1)) day--
            return day
        }
        private fun corrected(year: Int): Boolean = year in 1502..2987 && CORRECTIONS[year - 1502]
        private val CORRECTIONS = BooleanArray(2987 - 1502 + 1).apply {
            for (year in intArrayOf(
                1502,1601,1634,1667,1700,1733,1766,1799,1832,1865,1898,1931,1964,1997,2030,2059,
                2063,2096,2129,2158,2162,2191,2195,2224,2228,2257,2261,2290,2294,2323,2327,2356,
                2360,2389,2393,2422,2426,2455,2459,2488,2492,2521,2525,2554,2558,2587,2591,2620,
                2624,2653,2657,2686,2690,2719,2723,2748,2752,2756,2781,2785,2789,2818,2822,2847,
                2851,2855,2880,2884,2888,2913,2917,2921,2946,2950,2954,2979,2983,2987,
            )) this[year - 1502] = true
        }
        private val MONTH_COUNT = arrayOf(
            intArrayOf(31,31,0), intArrayOf(31,31,31), intArrayOf(31,31,62), intArrayOf(31,31,93),
            intArrayOf(31,31,124), intArrayOf(31,31,155), intArrayOf(30,30,186), intArrayOf(30,30,216),
            intArrayOf(30,30,246), intArrayOf(30,30,276), intArrayOf(30,30,306), intArrayOf(29,30,336),
        )
        private val LIMITS = arrayOf(
            intArrayOf(0,0,0,0), intArrayOf(-5000000,-5000000,5000000,5000000), intArrayOf(0,0,11,11), intArrayOf(1,1,52,53),
            intArrayOf(), intArrayOf(1,1,29,31), intArrayOf(1,1,365,366), intArrayOf(), intArrayOf(-1,-1,5,5),
            intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(),
            intArrayOf(-5000000,-5000000,5000000,5000000), intArrayOf(), intArrayOf(-5000000,-5000000,5000000,5000000),
            intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(0,0,11,11),
        )
    }
}
