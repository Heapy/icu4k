// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

/** Base Calendar.roll; subclasses may replace individual fields through their owner. */
internal class CalendarRollCore(private val owner: Owner) {
    interface Owner {
        fun complete()
        fun get(field: Int): Int
        fun internalGet(field: Int): Int
        fun internalGetMonth(): Int
        fun set(field: Int, value: Int)
        fun clear(field: Int)
        fun getActualMinimum(field: Int): Int
        fun getActualMaximum(field: Int): Int
        fun getMaximum(field: Int): Int
        fun pinField(field: Int)
        fun isEra0CountingBackward(): Boolean
        fun getFirstDayOfWeek(): Int
        fun getMinimalDaysInFirstWeek(): Int
        fun getTimeInMillis(): Long
        fun setTimeInMillis(value: Long)
        fun storedTimeInMillis(): Long
        fun assignStoredTime(value: Long)
        fun fieldName(field: Int): String?
    }

    fun roll(field: Int, amount: Int) {
        if (amount == 0) return
        owner.complete()
        when (field) {
            5, 9, 12, 13, 14, 21, 0 -> {
                val min = owner.getActualMinimum(field)
                val max = owner.getActualMaximum(field)
                val gap = max - min + 1
                var value = rem(owner.internalGet(field) + amount - min, gap)
                if (value < 0) value += gap
                owner.set(field, value + min)
            }
            10, 11 -> {
                val start = owner.getTimeInMillis()
                val old = owner.internalGet(field)
                val max = owner.getMaximum(field)
                var value = rem(old + amount, max + 1)
                if (value < 0) value += max + 1
                owner.setTimeInMillis(start + 3600000L * (value.toLong() - old))
            }
            2, 23 -> {
                val max = owner.getActualMaximum(2)
                var month = rem(owner.internalGetMonth() + amount, max + 1)
                if (month < 0) month += max + 1
                owner.set(2, month)
                owner.pinField(5)
            }
            1, 17 -> {
                val era = owner.get(0)
                val delta = if (era == 0 && owner.isEra0CountingBackward()) -amount else amount
                var year = owner.internalGet(field) + delta
                if (era > 0 || year >= 1) {
                    val max = owner.getActualMaximum(field)
                    if (max < 32768) {
                        if (year < 1) year = max - rem(-year, max)
                        else if (year > max) year = rem(year - 1, max) + 1
                    } else if (year < 1) year = 1
                } else if (era == 0 && owner.isEra0CountingBackward()) year = 1
                owner.set(field, year)
                owner.pinField(2)
                owner.pinField(5)
            }
            19 -> {
                owner.set(field, owner.internalGet(field) + amount)
                owner.pinField(2)
                owner.pinField(5)
            }
            4, 3 -> {
                var dow = owner.internalGet(7) - owner.getFirstDayOfWeek()
                if (dow < 0) dow += 7
                val dayField = if (field == 4) 5 else 6
                var first = (dow - owner.internalGet(dayField) + 1) % 7
                if (first < 0) first += 7
                val start = if (7 - first < owner.getMinimalDaysInFirstWeek()) 8 - first else 1 - first
                val length = owner.getActualMaximum(dayField)
                val last = (length - owner.internalGet(dayField) + dow) % 7
                val gap = length + 7 - last - start
                var day = rem(owner.internalGet(dayField) + amount * 7 - start, gap)
                if (day < 0) day += gap
                day += start
                if (day < 1) day = 1
                if (day > length) day = length
                owner.set(dayField, day)
                if (field == 3) { owner.clear(2); owner.clear(23) }
            }
            6 -> {
                val delta = amount * 86400000L
                val min = owner.storedTimeInMillis() - (owner.internalGet(6) - 1) * 86400000L
                val length = owner.getActualMaximum(6)
                owner.assignStoredTime(rem(owner.storedTimeInMillis() + delta - min, length * 86400000L))
                if (owner.storedTimeInMillis() < 0) owner.assignStoredTime(owner.storedTimeInMillis() + length * 86400000L)
                owner.setTimeInMillis(owner.storedTimeInMillis() + min)
            }
            7, 18 -> {
                val delta = amount * 86400000L
                var lead = owner.internalGet(field)
                lead -= if (field == 7) owner.getFirstDayOfWeek() else 1
                if (lead < 0) lead += 7
                val min = owner.storedTimeInMillis() - lead * 86400000L
                owner.assignStoredTime(rem(owner.storedTimeInMillis() + delta - min, 604800000L))
                if (owner.storedTimeInMillis() < 0) owner.assignStoredTime(owner.storedTimeInMillis() + 604800000L)
                owner.setTimeInMillis(owner.storedTimeInMillis() + min)
            }
            8 -> {
                val delta = amount * 604800000L
                val before = (owner.internalGet(5) - 1) / 7
                val after = (owner.getActualMaximum(5) - owner.internalGet(5)) / 7
                val min = owner.storedTimeInMillis() - before * 604800000L
                val gap = 604800000L * (before + after + 1)
                owner.assignStoredTime(rem(owner.storedTimeInMillis() + delta - min, gap))
                if (owner.storedTimeInMillis() < 0) owner.assignStoredTime(owner.storedTimeInMillis() + gap)
                owner.setTimeInMillis(owner.storedTimeInMillis() + min)
            }
            20 -> owner.set(field, owner.internalGet(field) + amount)
            else -> throw IllegalArgumentException("Calendar.roll(${owner.fieldName(field)}) not supported")
        }
    }

    private fun rem(value: Int, divisor: Int): Int {
        if (divisor == 0) throw ArithmeticException("/ by zero")
        return value % divisor
    }
    private fun rem(value: Long, divisor: Long): Long {
        if (divisor == 0L) throw ArithmeticException("/ by zero")
        return value % divisor
    }
}
