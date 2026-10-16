// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

/** Calendar limits and the clone-based search for limits at the current date. */
internal class CalendarLimitsCore(private val fields: IntArray?, private val owner: Owner) {
    interface Owner {
        fun handleGetLimit(field: Int, type: Int): Int
        fun getLimit(field: Int, type: Int): Int
        fun getMinimalDaysInFirstWeek(): Int
        fun storedFirstDayOfWeek(): Int
        fun get(field: Int): Int
        fun set(field: Int, value: Int)
        fun getActualMaximum(field: Int): Int
        fun getActualMinimum(field: Int): Int
        fun handleGetMonthLength(year: Int, month: Int): Int
        fun handleGetYearLength(year: Int): Int
        fun inTemporalLeapYear(): Boolean
        fun cloneCalendar(): Owner
        fun complete()
        fun setLenient(value: Boolean)
        fun prepareGetActual(field: Int, minimum: Boolean)
        fun add(field: Int, amount: Int)
    }

    fun getLimit(field: Int, type: Int): Int = when (field) {
        7, 9, 10, 11, 12, 13, 14, 15, 16, 18, 20, 21, 22 -> {
            if (type !in 0..3) throw IndexOutOfBoundsException()
            when (field) {
                7, 18 -> if (type < 2) 1 else 7
                9, 22 -> if (type < 2) 0 else 1
                10 -> if (type < 2) 0 else 11
                11 -> if (type < 2) 0 else 23
                12, 13 -> if (type < 2) 0 else 59
                14 -> if (type < 2) 0 else 999
                15 -> intArrayOf(-86400000, -57600000, 43200000, 108000000)[type]
                16 -> if (type < 2) 0 else 7200000
                20 -> if (type < 2) -0x7F000000 else 0x7F000000
                else -> if (type < 2) 0 else 86399999
            }
        }
        4 -> when (type) {
            0 -> if (owner.getMinimalDaysInFirstWeek() == 1) 1 else 0
            1 -> 1
            else -> {
                val minDays = owner.getMinimalDaysInFirstWeek()
                val days = owner.handleGetLimit(5, type)
                if (type == 2) (days + (7 - minDays)) / 7 else (days + 6 + (7 - minDays)) / 7
            }
        }
        else -> owner.handleGetLimit(field, type)
    }

    fun getActualMaximum(field: Int): Int = when (field) {
        5, 6 -> {
            val cal = owner.cloneCalendar()
            cal.setLenient(true)
            cal.prepareGetActual(field, false)
            if (field == 5) owner.handleGetMonthLength(cal.get(19), cal.get(2))
            else owner.handleGetYearLength(cal.get(19))
        }
        0, 7, 9, 10, 11, 12, 13, 14, 15, 16, 18, 20, 21 -> owner.getLimit(field, 3)
        23 -> owner.getLimit(field, if (owner.inTemporalLeapYear()) 3 else 2)
        else -> actualHelper(field, owner.getLimit(field, 2), owner.getLimit(field, 3))
    }

    fun getActualMinimum(field: Int): Int = when (field) {
        7, 9, 10, 11, 12, 13, 14, 15, 16, 18, 20, 21 -> owner.getLimit(field, 0)
        else -> actualHelper(field, owner.getLimit(field, 1), owner.getLimit(field, 0))
    }

    fun prepareGetActual(field: Int, minimum: Boolean) {
        owner.set(21, 0)
        when (field) {
            1, 19 -> owner.set(6, owner.getLimit(6, 1))
            17 -> owner.set(3, owner.getLimit(3, 1))
            2 -> owner.set(5, owner.getLimit(5, 1))
            8 -> { owner.set(5, 1); owner.set(7, owner.get(7)) }
            4, 3 -> {
                var dow = owner.storedFirstDayOfWeek()
                if (minimum) { dow = (dow + 6) % 7; if (dow < 1) dow += 7 }
                owner.set(7, dow)
            }
        }
        owner.set(field, owner.getLimit(field, 1))
    }

    fun pinField(field: Int) {
        val max = owner.getActualMaximum(field)
        val min = owner.getActualMinimum(field)
        if (fields!![field] > max) owner.set(field, max)
        else if (fields[field] < min) owner.set(field, min)
    }

    private fun actualHelper(field: Int, startValue: Int, end: Int): Int {
        if (startValue == end) return startValue
        val delta = if (end > startValue) 1 else -1
        val work = owner.cloneCalendar()
        work.complete()
        work.setLenient(true)
        work.prepareGetActual(field, delta < 0)
        work.set(field, startValue)
        if (work.get(field) != startValue && field != 4 && delta > 0) return startValue
        var start = startValue
        var result = start
        do {
            start += delta
            work.add(field, delta)
            if (work.get(field) != start) break
            result = start
        } while (start != end)
        return result
    }
}
