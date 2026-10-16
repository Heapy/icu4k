// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

/** Calendar's date-field selection, Julian-day framework, validation and arithmetic helpers. */
internal class CalendarDateCore(private val fields: IntArray, private val stamps: ByteArray, private val owner: Owner) {
    interface Owner {
        fun newestStamp(first: Int, last: Int, best: Int): Int
        fun resolveFields(table: Array<Array<IntArray>>?): Int
        fun getFieldResolutionTable(): Array<Array<IntArray>>?
        fun newerField(defaultField: Int, alternateField: Int): Int
        fun internalGet(field: Int): Int
        fun internalGet(field: Int, defaultValue: Int): Int
        fun internalGetMonth(): Int
        fun internalGetMonth(defaultValue: Int): Int
        fun internalSet(field: Int, value: Int)
        fun handleGetExtendedYear(): Int
        fun handleComputeJulianDay(bestField: Int): Int
        fun handleComputeMonthStart(year: Int, month: Int, useMonth: Boolean): Int
        fun getDefaultMonthInYear(year: Int): Int
        fun getDefaultDayInMonth(year: Int, month: Int): Int
        fun isSet(field: Int): Boolean
        fun getFirstDayOfWeek(): Int
        fun getMinimalDaysInFirstWeek(): Int
        fun handleGetMonthLength(year: Int, month: Int): Int
        fun handleGetYearLength(year: Int): Int
        fun validateField(field: Int)
        fun getMinimum(field: Int): Int
        fun getMaximum(field: Int): Int
        fun fieldName(field: Int): String?
        fun getInternalSetMask(): Int
        /** The source's default month-length implementation writes to System.out.printf. */
        fun reportDefaultMonthLength(year: Int, month: Int)
    }

    fun computeJulianDay(): Int {
        if (stamp(JULIAN_DAY) >= 2) {
            var best = owner.newestStamp(0,8,0)
            best = owner.newestStamp(17,19,best)
            best = owner.newestStamp(23,23,best)
            if (best <= stamp(JULIAN_DAY)) return owner.internalGet(JULIAN_DAY)
        }
        var bestField = owner.resolveFields(owner.getFieldResolutionTable())
        if (bestField < 0) bestField = DAY_OF_MONTH
        return owner.handleComputeJulianDay(bestField)
    }

    fun handleComputeJulianDay(bestField: Int): Int {
        val useMonth = bestField == DAY_OF_MONTH || bestField == WEEK_OF_MONTH || bestField == DAY_OF_WEEK_IN_MONTH
        val year = if (bestField == WEEK_OF_YEAR && owner.newerField(YEAR_WOY,YEAR) == YEAR_WOY) owner.internalGet(YEAR_WOY)
            else owner.handleGetExtendedYear()
        owner.internalSet(EXTENDED_YEAR,year)
        if (year.toLong() > Long.MAX_VALUE / 400) throw IllegalArgumentException("year is too large")
        val month = if (useMonth) owner.internalGetMonth(owner.getDefaultMonthInYear(year)) else 0
        val day = owner.handleComputeMonthStart(year,month,useMonth)
        if (bestField == DAY_OF_MONTH) {
            return day + if (owner.isSet(DAY_OF_MONTH)) owner.internalGet(DAY_OF_MONTH,owner.getDefaultDayInMonth(year,month))
                else owner.getDefaultDayInMonth(year,month)
        }
        if (bestField == DAY_OF_YEAR) return day + owner.internalGet(DAY_OF_YEAR)
        val firstDow = owner.getFirstDayOfWeek()
        var first = CalendarFieldsCore.julianDayToDayOfWeek(day + 1) - firstDow
        if (first < 0) first += 7
        var localDow = when (owner.resolveFields(CalendarFieldsCore.DOW_PRECEDENCE)) {
            DAY_OF_WEEK -> owner.internalGet(DAY_OF_WEEK) - firstDow
            DOW_LOCAL -> owner.internalGet(DOW_LOCAL) - 1
            else -> 0
        }
        localDow %= 7
        if (localDow < 0) localDow += 7
        var date = 1 - first + localDow
        if (bestField == DAY_OF_WEEK_IN_MONTH) {
            if (date < 1) date += 7
            val ordinal = owner.internalGet(DAY_OF_WEEK_IN_MONTH,1)
            if (ordinal >= 0) date += 7 * (ordinal - 1)
            else {
                val m = owner.internalGetMonth(0)
                val length = owner.handleGetMonthLength(year,m)
                date += ((length - date) / 7 + ordinal + 1) * 7
            }
        } else {
            if (7 - first < owner.getMinimalDaysInFirstWeek()) date += 7
            date += 7 * (owner.internalGet(bestField) - 1)
        }
        return day + date
    }

    fun internalGet(field: Int): Int = get(field)
    fun internalGet(field: Int, defaultValue: Int): Int = if (stamp(field) > 0) get(field) else defaultValue
    fun internalGetMonth(): Int = if (owner.resolveFields(CalendarFieldsCore.MONTH_PRECEDENCE) == MONTH) owner.internalGet(MONTH) else owner.internalGet(ORDINAL_MONTH)
    fun internalGetMonth(defaultValue: Int): Int = if (owner.resolveFields(CalendarFieldsCore.MONTH_PRECEDENCE) == MONTH) owner.internalGet(MONTH,defaultValue) else owner.internalGet(ORDINAL_MONTH,defaultValue)
    fun internalSet(field: Int, value: Int) {
        if ((1 shl field) and owner.getInternalSetMask() == 0) throw IllegalStateException("Subclass cannot set " + owner.fieldName(field))
        if (field !in fields.indices) throw IndexOutOfBoundsException()
        fields[field] = value
        if (field !in stamps.indices) throw IndexOutOfBoundsException()
        stamps[field] = 1
    }

    fun validateFields() {
        for (field in fields.indices) if (stamp(field) >= 2) owner.validateField(field)
    }
    fun validateField(field: Int) {
        when (field) {
            DAY_OF_MONTH -> {
                val year = owner.handleGetExtendedYear()
                validateField(field,1,owner.handleGetMonthLength(year,owner.internalGetMonth()))
            }
            DAY_OF_YEAR -> {
                val year = owner.handleGetExtendedYear()
                validateField(field,1,owner.handleGetYearLength(year))
            }
            DAY_OF_WEEK_IN_MONTH -> {
                if (owner.internalGet(field) == 0) throw IllegalArgumentException("DAY_OF_WEEK_IN_MONTH cannot be zero")
                validateField(field,owner.getMinimum(field),owner.getMaximum(field))
            }
            else -> validateField(field,owner.getMinimum(field),owner.getMaximum(field))
        }
    }
    fun validateField(field: Int, min: Int, max: Int) {
        val value = get(field)
        if (value < min || value > max) throw IllegalArgumentException("${owner.fieldName(field)}=$value, valid range=$min..$max")
    }
    fun fieldName(field: Int): String = if (field in names.indices) names[field] else "Field $field"
    fun handleGetMonthLength(year: Int, month: Int): Int {
        owner.reportDefaultMonthLength(year,month)
        return owner.handleComputeMonthStart(year,month + 1,true) - owner.handleComputeMonthStart(year,month,true)
    }
    fun handleGetYearLength(year: Int): Int = owner.handleComputeMonthStart(year + 1,0,false) - owner.handleComputeMonthStart(year,0,false)
    fun getDefaultMonthInYear(year: Int): Int = 0
    fun getDefaultDayInMonth(year: Int, month: Int): Int = 1

    private fun get(field: Int): Int { if (field !in fields.indices) throw IndexOutOfBoundsException(); return fields[field] }
    private fun stamp(field: Int): Int { if (field !in stamps.indices) throw IndexOutOfBoundsException(); return stamps[field].toInt() }

    companion object {
        private const val YEAR = 1; private const val MONTH = 2; private const val WEEK_OF_YEAR = 3
        private const val WEEK_OF_MONTH = 4; private const val DAY_OF_MONTH = 5; private const val DAY_OF_YEAR = 6
        private const val DAY_OF_WEEK = 7; private const val DAY_OF_WEEK_IN_MONTH = 8; private const val YEAR_WOY = 17
        private const val DOW_LOCAL = 18; private const val EXTENDED_YEAR = 19; private const val JULIAN_DAY = 20; private const val ORDINAL_MONTH = 23
        private val names = arrayOf("ERA","YEAR","MONTH","WEEK_OF_YEAR","WEEK_OF_MONTH","DAY_OF_MONTH","DAY_OF_YEAR","DAY_OF_WEEK","DAY_OF_WEEK_IN_MONTH","AM_PM","HOUR","HOUR_OF_DAY","MINUTE","SECOND","MILLISECOND","ZONE_OFFSET","DST_OFFSET","YEAR_WOY","DOW_LOCAL","EXTENDED_YEAR","JULIAN_DAY","MILLISECONDS_IN_DAY","IS_LEAP_MONTH","ORDINAL_MONTH")
        private val monthCounts = arrayOf(
            intArrayOf(31,31,0,0),intArrayOf(28,29,31,31),intArrayOf(31,31,59,60),intArrayOf(30,30,90,91),
            intArrayOf(31,31,120,121),intArrayOf(30,30,151,152),intArrayOf(31,31,181,182),intArrayOf(31,31,212,213),
            intArrayOf(30,30,243,244),intArrayOf(31,31,273,274),intArrayOf(30,30,304,305),intArrayOf(31,31,334,335),
        )
        fun isGregorianLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        fun gregorianMonthLength(year: Int, month: Int): Int { if (month !in monthCounts.indices) throw IndexOutOfBoundsException(); return monthCounts[month][if (isGregorianLeapYear(year)) 1 else 0] }
        fun gregorianPreviousMonthLength(year: Int, month: Int): Int = if (month > 0) gregorianMonthLength(year,month - 1) else 31
        fun computeGregorianMonthStart(extendedYear: Int, month: Int): Int {
            var year = extendedYear; var m = month
            if (m < 0 || m > 11) { val q = floorDivide(m,12); year += q; m -= q * 12 }
            val y = year - 1
            var day = 365 * y + floorDivide(y,4) - floorDivide(y,100) + floorDivide(y,400) + 1721426 - 1
            if (m != 0) day += monthCounts[m][if (isGregorianLeapYear(year)) 3 else 2]
            return day
        }
        fun floorDivide(n: Int, d: Int): Int { if (d == 0) throw ArithmeticException("/ by zero"); return if (n >= 0) n / d else (n + 1) / d - 1 }
        fun floorDivide(n: Long, d: Long): Long { if (d == 0L) throw ArithmeticException("/ by zero"); return if (n >= 0) n / d else (n + 1) / d - 1 }
        fun floorDivide(n: Int, d: Int, remainder: IntArray?): Int {
            if (d == 0) throw ArithmeticException("/ by zero")
            if (n >= 0) { putRemainder(remainder,n % d); return n / d }
            val q = (n + 1) / d - 1
            putRemainder(remainder,n - q * d)
            return q
        }
        fun floorDivide(n: Long, d: Int, remainder: IntArray?): Int {
            if (d == 0) throw ArithmeticException("/ by zero")
            if (n >= 0) { putRemainder(remainder,(n % d).toInt()); return (n / d).toInt() }
            val q = ((n + 1) / d - 1).toInt()
            putRemainder(remainder,(n - q.toLong() * d).toInt())
            return q
        }
        private fun putRemainder(values: IntArray?, value: Int) { if (values == null) throw NullPointerException(); if (values.isEmpty()) throw IndexOutOfBoundsException(); values[0] = value }
        fun millisToJulianDay(millis: Long): Int = (2440588L + floorDivide(millis,86400000)).toInt()
        fun julianDayToMillis(day: Int): Long = (day - 2440588).toLong() * 86400000
    }
}
