// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2011, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

/** Shared Coptic/Ethiopic thirteen-month calendar arithmetic and Temporal overrides. */
internal class CECalendarCore(private val owner: Owner) {
    interface Owner {
        fun getJDEpochOffset(): Int
        fun extendedYearToEra(year: Int): Int
        fun extendedYearToYear(year: Int): Int
        fun internalSet(field: Int, value: Int)
        fun get(field: Int): Int
        fun set(field: Int, value: Int)
        fun baseGetTemporalMonthCode(): String
        fun baseSetTemporalMonthCode(code: String?)
    }
    fun handleComputeFields(julianDay: Int) {
        val date = IntArray(3)
        jdToCE(julianDay, owner.getJDEpochOffset(), date)
        owner.internalSet(19, date[0])
        owner.internalSet(0, owner.extendedYearToEra(date[0]))
        owner.internalSet(1, owner.extendedYearToYear(date[0]))
        owner.internalSet(2, date[1])
        owner.internalSet(23, date[1])
        owner.internalSet(5, date[2])
        owner.internalSet(6, 30 * date[1] + date[2])
    }
    @Suppress("UNUSED_PARAMETER")
    fun handleComputeMonthStart(year: Int, month: Int, useMonth: Boolean): Int = ceToJD(year.toLong(), month, 0, owner.getJDEpochOffset())
    fun handleGetLimit(field: Int, type: Int): Int {
        if (field !in LIMITS.indices || type !in LIMITS[field].indices) throw IndexOutOfBoundsException()
        return LIMITS[field][type]
    }
    fun handleGetMonthLength(year: Int, month: Int): Int = if ((month + 1) % 13 != 0) 30 else (year % 4) / 3 + 5
    fun getTemporalMonthCode(): String = if (owner.get(2) == 12) "M13" else owner.baseGetTemporalMonthCode()
    fun setTemporalMonthCode(code: String?) {
        if (code!! == "M13") { owner.set(2, 12); owner.set(22, 0); return }
        owner.baseSetTemporalMonthCode(code)
    }
    companion object {
        fun ceToJD(year: Long, month: Int, day: Int, epoch: Int): Int {
            var y = year
            var m = month
            if (m >= 0) { y += m / 13; m %= 13 }
            else { ++m; y += m / 13 - 1; m = m % 13 + 12 }
            return (epoch + 365L * y + CalendarDateCore.floorDivide(y, 4L) + 30 * m + day - 1).toInt()
        }
        fun jdToCE(julianDay: Int, epoch: Int, fields: IntArray?) {
            val remainder = IntArray(1)
            val cycle = CalendarDateCore.floorDivide(julianDay - epoch, 1461, remainder)
            put(fields, 0, 4 * cycle + (remainder[0] / 365 - remainder[0] / 1460))
            val doy = if (remainder[0] == 1460) 365 else remainder[0] % 365
            put(fields, 1, doy / 30)
            put(fields, 2, doy % 30 + 1)
        }
        private fun put(fields: IntArray?, index: Int, value: Int) {
            val array = fields!!
            if (index !in array.indices) throw IndexOutOfBoundsException()
            array[index] = value
        }
        private val LIMITS = arrayOf(
            intArrayOf(0,0,1,1), intArrayOf(1,1,5000000,5000000), intArrayOf(0,0,12,12), intArrayOf(1,1,52,53),
            intArrayOf(), intArrayOf(1,1,5,30), intArrayOf(1,1,365,366), intArrayOf(), intArrayOf(-1,-1,1,5),
            intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(),
            intArrayOf(-5000000,-5000000,5000000,5000000), intArrayOf(), intArrayOf(-5000000,-5000000,5000000,5000000),
            intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(0,0,12,12),
        )
    }
}
