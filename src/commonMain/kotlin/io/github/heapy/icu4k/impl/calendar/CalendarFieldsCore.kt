// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.Grego

/** Calendar's time-to-fields, week numbering and field-stamp resolution algorithms. */
internal class CalendarFieldsCore<Z>(
    private val fieldsProvider: () -> IntArray,
    private val stampProvider: () -> ByteArray,
    private val owner: Owner<Z>,
) {
    constructor(fields: IntArray, stamp: ByteArray, owner: Owner<Z>) : this({ fields }, { stamp }, owner)
    private val fields: IntArray get() = fieldsProvider()
    private val stamp: ByteArray get() = stampProvider()
    interface Owner<Z> {
        fun getTimeZone(): Z?
        fun getOffset(zone: Z?, date: Long, local: Boolean, offsets: IntArray)
        fun getTime(): Long
        fun getInternalSetMask(): Int
        fun handleComputeFields(julianDay: Int)
        fun getFirstDayOfWeek(): Int
        fun getMinimalDaysInFirstWeek(): Int
        fun handleGetYearLength(year: Int): Int
        fun weekNumber(desiredDay: Int, dayOfPeriod: Int, dayOfWeek: Int): Int
        fun internalGet(field: Int): Int
    }

    var gregorianYear = 0; private set
    var gregorianMonth = 0; private set
    var gregorianDayOfMonth = 0; private set
    var gregorianDayOfYear = 0; private set

    fun computeFields() {
        val offsets = IntArray(2)
        val zone = owner.getTimeZone()
        owner.getOffset(zone, owner.getTime(), false, offsets)
        val localMillis = owner.getTime() + offsets[0] + offsets[1]
        var mask = owner.getInternalSetMask()
        for (i in fields.indices) {
            putStamp(i, if (mask and 1 == 0) 1 else 0)
            mask = mask shr 1
        }
        val days = Grego.floorDivide(localMillis, 86400000)
        put(JULIAN_DAY, days.toInt() + 2440588)
        computeGregorianFields(get(JULIAN_DAY))
        val dow = julianDayToDayOfWeek(get(JULIAN_DAY))
        put(DAY_OF_WEEK, dow)
        var localDow = dow - owner.getFirstDayOfWeek() + 1
        if (localDow < 1) localDow += 7
        put(DOW_LOCAL, localDow)
        owner.handleComputeFields(get(JULIAN_DAY))
        computeWeekFields()
        var millis = (localMillis - days * 86400000).toInt()
        put(MILLISECONDS_IN_DAY, millis)
        put(MILLISECOND, millis % 1000); millis /= 1000
        put(SECOND, millis % 60); millis /= 60
        put(MINUTE, millis % 60); millis /= 60
        put(HOUR_OF_DAY, millis)
        put(AM_PM, millis / 12)
        put(HOUR, millis % 12)
        put(ZONE_OFFSET, offsets[0]); put(DST_OFFSET, offsets[1])
    }

    fun computeGregorianFields(julianDay: Int) {
        // Source subtracts two ints before promoting the epoch day to long.
        val result = Grego.dayToFields((julianDay - 2440588).toLong(), null)
        gregorianYear = result[0]; gregorianMonth = result[1]
        gregorianDayOfMonth = result[2]; gregorianDayOfYear = result[4]
    }

    fun computeWeekFields() {
        val year = get(EXTENDED_YEAR)
        val dow = get(DAY_OF_WEEK)
        val doy = get(DAY_OF_YEAR)
        var weekYear = year
        val relativeDow = (dow + 7 - owner.getFirstDayOfWeek()) % 7
        val relativeJanuary1 = (dow - doy + 7001 - owner.getFirstDayOfWeek()) % 7
        var week = (doy - 1 + relativeJanuary1) / 7
        if (7 - relativeJanuary1 >= owner.getMinimalDaysInFirstWeek()) week++
        if (week == 0) {
            val previousDoy = doy + owner.handleGetYearLength(year - 1)
            week = weekNumber(previousDoy, dow)
            weekYear--
        } else {
            val lastDoy = owner.handleGetYearLength(year)
            if (doy >= lastDoy - 5) {
                var lastRelativeDow = (relativeDow + lastDoy - doy) % 7
                if (lastRelativeDow < 0) lastRelativeDow += 7
                if (6 - lastRelativeDow >= owner.getMinimalDaysInFirstWeek() && doy + 7 - relativeDow > lastDoy) {
                    week = 1; weekYear++
                }
            }
        }
        put(WEEK_OF_YEAR, week); put(YEAR_WOY, weekYear)
        val dom = get(DAY_OF_MONTH)
        put(WEEK_OF_MONTH, weekNumber(dom, dow))
        put(DAY_OF_WEEK_IN_MONTH, (dom - 1) / 7 + 1)
    }

    fun weekNumber(desiredDay: Int, dayOfPeriod: Int, dayOfWeek: Int): Int {
        var start = (dayOfWeek - owner.getFirstDayOfWeek() - dayOfPeriod + 1) % 7
        if (start < 0) start += 7
        var number = (desiredDay + start - 1) / 7
        if (7 - start >= owner.getMinimalDaysInFirstWeek()) number++
        return number
    }
    fun weekNumber(dayOfPeriod: Int, dayOfWeek: Int): Int = owner.weekNumber(dayOfPeriod, dayOfPeriod, dayOfWeek)

    fun resolveFields(table: Array<out Array<out IntArray?>?>?): Int {
        if (table == null) throw NullPointerException()
        var best = -1
        for (nullableGroup in table) {
            if (best >= 0) break
            val group = nullableGroup ?: throw NullPointerException()
            var bestStamp = 0
            lines@ for (nullableLine in group) {
                val line = nullableLine ?: throw NullPointerException()
                if (line.isEmpty()) throw IndexOutOfBoundsException()
                var lineStamp = 0
                for (i in (if (line[0] >= RESOLVE_REMAP) 1 else 0) until line.size) {
                    val value = getStamp(line[i])
                    if (value == 0) continue@lines
                    lineStamp = maxOf(lineStamp, value)
                }
                if (lineStamp > bestStamp) {
                    var candidate = line[0]
                    if (candidate >= RESOLVE_REMAP) {
                        candidate = candidate and (RESOLVE_REMAP - 1)
                        if (candidate != DAY_OF_MONTH || getStamp(WEEK_OF_MONTH) < getStamp(candidate)) best = candidate
                    } else best = candidate
                    if (best == candidate) bestStamp = lineStamp
                }
            }
        }
        return if (best >= RESOLVE_REMAP) best and (RESOLVE_REMAP - 1) else best
    }
    fun newestStamp(first: Int, last: Int, bestStampSoFar: Int): Int {
        var best = bestStampSoFar
        for (i in first..last) { val value = getStamp(i); if (value > best) best = value }
        return best
    }
    fun getStamp(field: Int): Int {
        if (field !in stamp.indices) throw IndexOutOfBoundsException()
        return stamp[field].toInt()
    }
    fun newerField(defaultField: Int, alternateField: Int): Int =
        if (getStamp(alternateField) > getStamp(defaultField)) alternateField else defaultField

    fun computeMillisInDay(): Int {
        var millis = 0
        val hourOfDayStamp = getStamp(HOUR_OF_DAY)
        val hourStamp = maxOf(getStamp(HOUR), getStamp(AM_PM))
        val bestStamp = if (hourStamp > hourOfDayStamp) hourStamp else hourOfDayStamp
        if (bestStamp != 0) {
            if (bestStamp == hourOfDayStamp) millis += owner.internalGet(HOUR_OF_DAY)
            else { millis += owner.internalGet(HOUR); millis += 12 * owner.internalGet(AM_PM) }
        }
        millis *= 60; millis += owner.internalGet(MINUTE)
        millis *= 60; millis += owner.internalGet(SECOND)
        millis *= 1000; millis += owner.internalGet(MILLISECOND)
        return millis
    }
    fun computeMillisInDayLong(): Long {
        var millis = 0L
        val hourOfDayStamp = getStamp(HOUR_OF_DAY)
        val hourStamp = maxOf(getStamp(HOUR), getStamp(AM_PM))
        val bestStamp = if (hourStamp > hourOfDayStamp) hourStamp else hourOfDayStamp
        if (bestStamp != 0) {
            if (bestStamp == hourOfDayStamp) millis += owner.internalGet(HOUR_OF_DAY)
            else { millis += owner.internalGet(HOUR); millis += 12 * owner.internalGet(AM_PM) }
        }
        millis *= 60; millis += owner.internalGet(MINUTE)
        millis *= 60; millis += owner.internalGet(SECOND)
        millis *= 1000; millis += owner.internalGet(MILLISECOND)
        return millis
    }

    fun copyFor(fields: IntArray, stamps: ByteArray, owner: Owner<Z>): CalendarFieldsCore<Z> = CalendarFieldsCore(fields, stamps, owner).also {
        it.gregorianYear = gregorianYear; it.gregorianMonth = gregorianMonth
        it.gregorianDayOfMonth = gregorianDayOfMonth; it.gregorianDayOfYear = gregorianDayOfYear
    }
    private fun get(field: Int): Int { if (field !in fields.indices) throw IndexOutOfBoundsException(); return fields[field] }
    private fun put(field: Int, value: Int) { if (field !in fields.indices) throw IndexOutOfBoundsException(); fields[field] = value }
    private fun putStamp(field: Int, value: Int) { if (field !in stamp.indices) throw IndexOutOfBoundsException(); stamp[field] = value.toByte() }

    companion object {
        const val RESOLVE_REMAP = 32
        private const val WEEK_OF_YEAR = 3; private const val WEEK_OF_MONTH = 4; private const val DAY_OF_MONTH = 5
        private const val DAY_OF_YEAR = 6; private const val DAY_OF_WEEK = 7; private const val DAY_OF_WEEK_IN_MONTH = 8
        private const val AM_PM = 9; private const val HOUR = 10; private const val HOUR_OF_DAY = 11
        private const val MINUTE = 12; private const val SECOND = 13; private const val MILLISECOND = 14
        private const val ZONE_OFFSET = 15; private const val DST_OFFSET = 16; private const val YEAR_WOY = 17
        private const val DOW_LOCAL = 18; private const val EXTENDED_YEAR = 19; private const val JULIAN_DAY = 20
        private const val MILLISECONDS_IN_DAY = 21
        val DATE_PRECEDENCE = arrayOf(
            arrayOf(intArrayOf(5),intArrayOf(3,7),intArrayOf(4,7),intArrayOf(8,7),intArrayOf(3,18),intArrayOf(4,18),intArrayOf(8,18),intArrayOf(6),intArrayOf(32 or 5,1),intArrayOf(32 or 3,17)),
            arrayOf(intArrayOf(3),intArrayOf(4),intArrayOf(8),intArrayOf(32 or 8,7),intArrayOf(32 or 8,18)),
        )
        val DOW_PRECEDENCE = arrayOf(arrayOf(intArrayOf(7),intArrayOf(18)))
        val MONTH_PRECEDENCE = arrayOf(arrayOf(intArrayOf(2),intArrayOf(23)))
        fun julianDayToDayOfWeek(day: Int): Int { var dow = (day + 2) % 7; if (dow < 1) dow += 7; return dow }
    }
}
