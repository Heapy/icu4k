// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.calendar.*

/** Mutable calendar fields, field precedence and timezone-aware time conversion. */
abstract class Calendar : ICUCloneableBase, Comparable<Calendar?> {
    private var fields: IntArray? = null
    private var stamps: ByteArray? = null
    private var values = CalendarStateCore.Values<TimeZone>()
    private var internalSetMask = 0
    private var fieldPort: CalendarFieldsCore<TimeZone>? = null
    private var validLocale: ULocale? = null
    private var actualLocale: ULocale? = null
    private var weekendOnset = 0
    private var weekendOnsetMillis = 0
    private var weekendCease = 0
    private var weekendCeaseMillis = 0

    protected constructor(zone: TimeZone?, locale: ULocale?) : super() {
        values.zone = zone
        setWeekData(WeekData(CalendarWeekDataCore.cachedForRegion(CalendarLocaleCore.getRegionForCalendar(locale))))
        CalendarLocaleCore.firstDayOverride(locale!!)?.let { setFirstDayOfWeek(it) }
        val calLocale = CalendarLocaleCore.calendarLocale(locale)
        setLocale(calLocale, calLocale)
        val storage = CalendarStateCore.createStorage(handleCreateFields())
        fields = storage.fields; stamps = storage.stamps; internalSetMask = storage.internalSetMask
    }
    /** Native typed copies use this path without rerunning virtual constructor callbacks. */
    protected constructor(other: Calendar, @Suppress("UNUSED_PARAMETER") copyMarker: Unit) : super() {
        fields = other.fields; stamps = other.stamps; values = other.values
        internalSetMask = other.internalSetMask; fieldPort = other.fieldPort
        validLocale = other.validLocale; actualLocale = other.actualLocale
        weekendOnset = other.weekendOnset; weekendOnsetMillis = other.weekendOnsetMillis
        weekendCease = other.weekendCease; weekendCeaseMillis = other.weekendCeaseMillis
    }
    private fun state(): CalendarStateCore<TimeZone> = CalendarStateCore(fields!!, stamps!!, values, StateOwner(), false)
    private fun fieldCore(): CalendarFieldsCore<TimeZone> = fieldPort ?: CalendarFieldsCore(fields!!, stamps!!, FieldsOwner()).also { fieldPort = it }
    private fun date(): CalendarDateCore = CalendarDateCore(fields!!, stamps!!, DateOwner())
    private fun time(): CalendarTimeCore<TimeZone> = CalendarTimeCore(stamps!!, TimeOwner(), false)
    private fun limits(): CalendarLimitsCore = CalendarLimitsCore(fields!!, LimitsOwner())
    private fun weekend(): CalendarWeekendCore<Calendar> = CalendarWeekendCore(WeekendState(), WeekendOwner())
    private fun comparison(): CalendarComparisonCore<Calendar> = CalendarComparisonCore(ComparisonOwner())

    fun getTime(): Date = state().getTime()
    fun setTime(date: Date?) = state().setTime(date)
    open fun getTimeInMillis(): Long = state().getTimeInMillis()
    open fun setTimeInMillis(millis: Long) = state().setTimeInMillis(millis)
    fun get(field: Int): Int = state().get(field)
    fun getFieldCount(): Int = fields!!.size
    fun set(field: Int, value: Int) = state().set(field, value)
    fun set(year: Int, month: Int, date: Int) = state().set(year, month, date)
    fun set(year: Int, month: Int, date: Int, hour: Int, minute: Int) = state().set(year, month, date, hour, minute)
    fun set(year: Int, month: Int, date: Int, hour: Int, minute: Int, second: Int) = state().set(year, month, date, hour, minute, second)
    fun clear() = state().clear()
    fun clear(field: Int) = state().clear(field)
    fun isSet(field: Int): Boolean = state().isSet(field)
    protected open fun complete() = state().complete()
    protected open fun computeFields() = fieldCore().computeFields()
    protected open fun computeTime() = time().computeTime()
    open fun getTimeZone(): TimeZone? = values.zone
    open fun setTimeZone(zone: TimeZone?) { values.zone = zone; values.areFieldsSet = false }
    open fun isLenient(): Boolean = values.lenient
    open fun setLenient(value: Boolean) { values.lenient = value }
    open fun getRepeatedWallTimeOption(): Int = values.repeatedWallTime
    open fun setRepeatedWallTimeOption(option: Int) = state().setRepeatedWallTimeOption(option)
    open fun getSkippedWallTimeOption(): Int = values.skippedWallTime
    open fun setSkippedWallTimeOption(option: Int) = state().setSkippedWallTimeOption(option)
    open fun getFirstDayOfWeek(): Int = values.firstDayOfWeek
    open fun setFirstDayOfWeek(value: Int) {
        if (values.firstDayOfWeek != value) {
            require(value in 1..7) { "Invalid day of week" }
            values.firstDayOfWeek = value; values.areFieldsSet = false
        }
    }
    open fun getMinimalDaysInFirstWeek(): Int = values.minimalDaysInFirstWeek
    open fun setMinimalDaysInFirstWeek(value: Int) {
        val normalized = value.coerceIn(1, 7)
        if (values.minimalDaysInFirstWeek != normalized) { values.minimalDaysInFirstWeek = normalized; values.areFieldsSet = false }
    }
    protected open fun getRelatedYearDifference(): Int = 0
    open fun getRelatedYear(): Int = state().getRelatedYear()
    open fun setRelatedYear(year: Int) = state().setRelatedYear(year)
    open fun inTemporalLeapYear(): Boolean = state().inTemporalLeapYear()
    open fun getTemporalMonthCode(): String = state().getTemporalMonthCode()
    open fun setTemporalMonthCode(code: String?) = state().setTemporalMonthCode(code)

    protected open fun handleCreateFields(): IntArray? = IntArray(24)
    protected abstract fun handleGetLimit(field: Int, limitType: Int): Int
    protected abstract fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int
    protected abstract fun handleGetExtendedYear(): Int
    protected open fun handleGetMonthLength(extendedYear: Int, month: Int): Int = date().handleGetMonthLength(extendedYear, month)
    protected open fun handleGetYearLength(extendedYear: Int): Int = date().handleGetYearLength(extendedYear)
    protected open fun getDefaultMonthInYear(extendedYear: Int): Int = date().getDefaultMonthInYear(extendedYear)
    protected open fun getDefaultDayInMonth(extendedYear: Int, month: Int): Int = date().getDefaultDayInMonth(extendedYear, month)
    protected open fun handleComputeJulianDay(bestField: Int): Int = date().handleComputeJulianDay(bestField)
    protected open fun getFieldResolutionTable(): Array<Array<IntArray>>? = CalendarFieldsCore.DATE_PRECEDENCE
    protected open fun handleComputeFields(julianDay: Int) {
        val month = getGregorianMonth()
        internalSet(MONTH, month); internalSet(ORDINAL_MONTH, month)
        internalSet(DAY_OF_MONTH, getGregorianDayOfMonth()); internalSet(DAY_OF_YEAR, getGregorianDayOfYear())
        var year = getGregorianYear(); internalSet(EXTENDED_YEAR, year)
        var era = 1
        if (year < 1) { era = 0; year = 1 - year }
        internalSet(ERA, era); internalSet(YEAR, year)
    }
    protected fun internalGet(field: Int): Int = date().internalGet(field)
    protected fun internalGet(field: Int, defaultValue: Int): Int = date().internalGet(field, defaultValue)
    protected fun internalSet(field: Int, value: Int) = date().internalSet(field, value)
    protected open fun internalGetMonth(): Int = date().internalGetMonth()
    protected open fun internalGetMonth(defaultValue: Int): Int = date().internalGetMonth(defaultValue)
    protected open fun resolveFields(table: Array<Array<IntArray>>?): Int = fieldCore().resolveFields(table)
    protected fun newestStamp(first: Int, last: Int, best: Int): Int = fieldCore().newestStamp(first, last, best)
    protected fun getStamp(field: Int): Int = fieldCore().getStamp(field)
    protected open fun newerField(defaultField: Int, alternateField: Int): Int = fieldCore().newerField(defaultField, alternateField)
    protected open fun computeJulianDay(): Int = date().computeJulianDay()
    protected open fun computeMillisInDay(): Int = fieldCore().computeMillisInDay()
    protected open fun computeMillisInDayLong(): Long = fieldCore().computeMillisInDayLong()
    protected open fun computeZoneOffset(millis: Long, millisInDay: Int): Int = time().computeZoneOffset(millis, millisInDay)
    protected open fun computeZoneOffset(millis: Long, millisInDay: Long): Int = time().computeZoneOffset(millis, millisInDay)
    protected open fun validateFields() = date().validateFields()
    protected open fun validateField(field: Int) = date().validateField(field)
    protected fun validateField(field: Int, minimum: Int, maximum: Int) = date().validateField(field, minimum, maximum)
    protected open fun fieldName(field: Int): String? = date().fieldName(field)
    protected fun getGregorianYear(): Int = fieldCore().gregorianYear
    protected fun getGregorianMonth(): Int = fieldCore().gregorianMonth
    protected fun getGregorianDayOfMonth(): Int = fieldCore().gregorianDayOfMonth
    protected fun getGregorianDayOfYear(): Int = fieldCore().gregorianDayOfYear
    protected fun computeGregorianFields(julianDay: Int) = fieldCore().computeGregorianFields(julianDay)
    protected open fun weekNumber(desiredDay: Int, dayOfPeriod: Int, dayOfWeek: Int): Int = fieldCore().weekNumber(desiredDay, dayOfPeriod, dayOfWeek)
    protected fun weekNumber(dayOfPeriod: Int, dayOfWeek: Int): Int = fieldCore().weekNumber(dayOfPeriod, dayOfWeek)
    protected open fun isEra0CountingBackward(): Boolean = false
    open fun getType(): String = "unknown"
    open fun haveDefaultCentury(): Boolean = true
    fun getMinimum(field: Int): Int = getLimit(field, 0)
    fun getGreatestMinimum(field: Int): Int = getLimit(field, 1)
    fun getLeastMaximum(field: Int): Int = getLimit(field, 2)
    fun getMaximum(field: Int): Int = getLimit(field, 3)
    protected open fun getLimit(field: Int, limitType: Int): Int = limits().getLimit(field, limitType)
    open fun getActualMinimum(field: Int): Int = limits().getActualMinimum(field)
    open fun getActualMaximum(field: Int): Int = limits().getActualMaximum(field)
    protected open fun prepareGetActual(field: Int, minimum: Boolean) = limits().prepareGetActual(field, minimum)
    protected open fun pinField(field: Int) = limits().pinField(field)
    open fun add(field: Int, amount: Int) = CalendarAddCore(AddOwner()).add(field, amount)
    open fun roll(field: Int, amount: Int) = CalendarRollCore(RollOwner()).roll(field, amount)
    open fun roll(field: Int, up: Boolean) = roll(field, if (up) 1 else -1)
    open fun fieldDifference(date: Date?, field: Int): Int = CalendarDifferenceCore(DifferenceOwner()).fieldDifference(date, field)
    open fun isEquivalentTo(other: Calendar?): Boolean = comparison().isEquivalentTo(other)
    override fun equals(other: Any?): Boolean = comparison().calendarEquals(other)
    override fun hashCode(): Int = comparison().calendarHashCode()
    override fun compareTo(other: Calendar?): Int = comparison().compareTo(other)
    open fun before(other: Any?): Boolean = comparison().before(other)
    open fun after(other: Any?): Boolean = comparison().after(other)

    open fun getDayOfWeekType(day: Int): Int = weekend().getDayOfWeekType(day)
    open fun getWeekendTransition(day: Int): Int = weekend().getWeekendTransition(day)
    open fun isWeekend(): Boolean = weekend().isWeekend()
    open fun isWeekend(date: Date?): Boolean = weekend().isWeekend(date)
    open fun getWeekData(): WeekData = WeekData(weekend().getWeekData())
    open fun setWeekData(data: WeekData?): Calendar = weekend().setWeekData(data?.core())
    fun getLocale(type: ULocale.Type?): ULocale? = if (type == ULocale.ACTUAL_LOCALE) actualLocale else validLocale
    internal fun setLocale(valid: ULocale?, actual: ULocale?) {
        if ((valid == null) != (actual == null)) throw IllegalArgumentException()
        validLocale = valid; actualLocale = actual
    }
    public override fun clone(): Calendar {
        val copy = cloneHost() as? Calendar ?: cloneNative()
        copy.fields = fields!!.copyOf(); copy.stamps = stamps!!.copyOf()
        copy.values = values.copy(zone = values.zone!!.clone())
        copy.fieldPort = fieldPort?.copyFor(copy.fields!!, copy.stamps!!, copy.FieldsOwner())
        return copy
    }
    protected open fun cloneNative(): Calendar = throw ICUCloneNotSupportedException()

    class WeekData(val firstDayOfWeek: Int, val minimalDaysInFirstWeek: Int, val weekendOnset: Int,
        val weekendOnsetMillis: Int, val weekendCease: Int, val weekendCeaseMillis: Int) {
        internal constructor(value: CalendarWeekData) : this(value.firstDayOfWeek, value.minimalDaysInFirstWeek,
            value.weekendOnset, value.weekendOnsetMillis, value.weekendCease, value.weekendCeaseMillis)
        internal fun core() = CalendarWeekData(firstDayOfWeek, minimalDaysInFirstWeek, weekendOnset, weekendOnsetMillis, weekendCease, weekendCeaseMillis)
        override fun equals(other: Any?): Boolean = this === other || other is WeekData && core() == other.core()
        override fun hashCode(): Int = core().hashCode()
        override fun toString(): String = core().toString()
    }
    companion object {
        const val ERA = 0; const val YEAR = 1; const val MONTH = 2; const val WEEK_OF_YEAR = 3; const val WEEK_OF_MONTH = 4
        const val DATE = 5; const val DAY_OF_MONTH = 5; const val DAY_OF_YEAR = 6; const val DAY_OF_WEEK = 7; const val DAY_OF_WEEK_IN_MONTH = 8
        const val AM_PM = 9; const val HOUR = 10; const val HOUR_OF_DAY = 11; const val MINUTE = 12; const val SECOND = 13
        const val MILLISECOND = 14; const val ZONE_OFFSET = 15; const val DST_OFFSET = 16; const val YEAR_WOY = 17; const val DOW_LOCAL = 18
        const val EXTENDED_YEAR = 19; const val JULIAN_DAY = 20; const val MILLISECONDS_IN_DAY = 21; const val IS_LEAP_MONTH = 22; const val ORDINAL_MONTH = 23
        const val SUNDAY = 1; const val MONDAY = 2; const val TUESDAY = 3; const val WEDNESDAY = 4; const val THURSDAY = 5; const val FRIDAY = 6; const val SATURDAY = 7
        const val JANUARY = 0; const val FEBRUARY = 1; const val MARCH = 2; const val APRIL = 3; const val MAY = 4; const val JUNE = 5
        const val JULY = 6; const val AUGUST = 7; const val SEPTEMBER = 8; const val OCTOBER = 9; const val NOVEMBER = 10; const val DECEMBER = 11; const val UNDECIMBER = 12
        const val AM = 0; const val PM = 1; const val WALLTIME_LAST = 0; const val WALLTIME_FIRST = 1; const val WALLTIME_NEXT_VALID = 2
        const val WEEKDAY = 0; const val WEEKEND = 1; const val WEEKEND_ONSET = 2; const val WEEKEND_CEASE = 3
        fun getWeekDataForRegion(region: String?): WeekData = WeekData(CalendarWeekDataCore.getWeekDataForRegion(region))
        fun getKeywordValuesForLocale(key: String?, locale: ULocale?, commonlyUsed: Boolean): Array<String> = CalendarLocaleCore.getKeywordValuesForLocale(key, locale, commonlyUsed)
    }
    private inner class StateOwner : CalendarStateCore.Owner {
        private val p get() = this@Calendar
        override fun computeFields() = p.computeFields()
        override fun computeTime() = p.computeTime()
        override fun complete() = p.complete()
        override fun isLenient(): Boolean = p.isLenient()
        override fun getTimeInMillis(): Long = p.getTimeInMillis()
        override fun setTimeInMillis(millis: Long) = p.setTimeInMillis(millis)
        override fun getRelatedYearDifference(): Int = p.getRelatedYearDifference()
        override fun getActualMaximum(field: Int): Int = p.getActualMaximum(field)
    }

    private inner class FieldsOwner : CalendarFieldsCore.Owner<TimeZone> {
        private val p get() = this@Calendar
        override fun getTimeZone(): TimeZone? = p.getTimeZone()
        override fun getOffset(zone: TimeZone?, date: Long, local: Boolean, offsets: IntArray) = zone!!.getOffset(date, local, offsets)
        override fun getTime(): Long = p.values.time
        override fun getInternalSetMask(): Int = p.internalSetMask
        override fun handleComputeFields(julianDay: Int) = p.handleComputeFields(julianDay)
        override fun getFirstDayOfWeek(): Int = p.getFirstDayOfWeek()
        override fun getMinimalDaysInFirstWeek(): Int = p.getMinimalDaysInFirstWeek()
        override fun handleGetYearLength(year: Int): Int = p.handleGetYearLength(year)
        override fun weekNumber(desiredDay: Int, dayOfPeriod: Int, dayOfWeek: Int): Int = p.weekNumber(desiredDay, dayOfPeriod, dayOfWeek)
        override fun internalGet(field: Int): Int = p.internalGet(field)
    }

    private inner class DateOwner : CalendarDateCore.Owner {
        private val p get() = this@Calendar
        override fun newestStamp(first: Int, last: Int, best: Int): Int = p.newestStamp(first, last, best)
        override fun resolveFields(table: Array<Array<IntArray>>?): Int = p.resolveFields(table)
        override fun getFieldResolutionTable(): Array<Array<IntArray>>? = p.getFieldResolutionTable()
        override fun newerField(defaultField: Int, alternateField: Int): Int = p.newerField(defaultField, alternateField)
        override fun internalGet(field: Int): Int = p.internalGet(field)
        override fun internalGet(field: Int, defaultValue: Int): Int = p.internalGet(field, defaultValue)
        override fun internalGetMonth(): Int = p.internalGetMonth()
        override fun internalGetMonth(defaultValue: Int): Int = p.internalGetMonth(defaultValue)
        override fun internalSet(field: Int, value: Int) = p.internalSet(field, value)
        override fun handleGetExtendedYear(): Int = p.handleGetExtendedYear()
        override fun handleComputeJulianDay(bestField: Int): Int = p.handleComputeJulianDay(bestField)
        override fun handleComputeMonthStart(year: Int, month: Int, useMonth: Boolean): Int = p.handleComputeMonthStart(year, month, useMonth)
        override fun getDefaultMonthInYear(year: Int): Int = p.getDefaultMonthInYear(year)
        override fun getDefaultDayInMonth(year: Int, month: Int): Int = p.getDefaultDayInMonth(year, month)
        override fun isSet(field: Int): Boolean = p.isSet(field)
        override fun getFirstDayOfWeek(): Int = p.getFirstDayOfWeek()
        override fun getMinimalDaysInFirstWeek(): Int = p.getMinimalDaysInFirstWeek()
        override fun handleGetMonthLength(year: Int, month: Int): Int = p.handleGetMonthLength(year, month)
        override fun handleGetYearLength(year: Int): Int = p.handleGetYearLength(year)
        override fun validateField(field: Int) = p.validateField(field)
        override fun getMinimum(field: Int): Int = p.getMinimum(field)
        override fun getMaximum(field: Int): Int = p.getMaximum(field)
        override fun fieldName(field: Int): String? = p.fieldName(field)
        override fun getInternalSetMask(): Int = p.internalSetMask
        override fun reportDefaultMonthLength(year: Int, month: Int) = print("handleGetMonthLength(ey=$year, m=$month)")
    }

    private inner class TimeOwner : CalendarTimeCore.Owner<TimeZone> {
        private val p get() = this@Calendar
        override fun isLenient(): Boolean = p.isLenient()
        override fun getLenientFlag(): Boolean = p.values.lenient
        override fun validateFields() = p.validateFields()
        override fun computeJulianDay(): Int = p.computeJulianDay()
        override fun newestStamp(first: Int, last: Int, best: Int): Int = p.newestStamp(first, last, best)
        override fun internalGet(field: Int): Int = p.internalGet(field)
        override fun computeMillisInDay(): Int = p.computeMillisInDay()
        override fun computeMillisInDayLong(): Long = p.computeMillisInDayLong()
        override fun computeZoneOffset(millis: Long, millisInDay: Long): Int = p.computeZoneOffset(millis, millisInDay)
        override fun setTime(time: Long) = run { p.values.time = time }
        override fun getZone(): TimeZone? = p.values.zone
        override fun getSkippedWallTime(): Int = p.values.skippedWallTime
        override fun getRepeatedWallTime(): Int = p.values.repeatedWallTime
        override fun isBasicTimeZone(zone: TimeZone?): Boolean = zone is BasicTimeZone
        override fun getOffsetFromLocal(zone: TimeZone?, wall: Long, nonExisting: Int, duplicated: Int, offsets: IntArray) = (zone as BasicTimeZone).getOffsetFromLocal(wall, if (nonExisting == 4) BasicTimeZone.LocalOption.FORMER else BasicTimeZone.LocalOption.LATTER, if (duplicated == 4) BasicTimeZone.LocalOption.FORMER else BasicTimeZone.LocalOption.LATTER, offsets)
        override fun getOffset(zone: TimeZone?, date: Long, local: Boolean, offsets: IntArray) = zone!!.getOffset(date, local, offsets)
        override fun getTotalOffset(zone: TimeZone?, date: Long): Int = zone!!.getOffset(date)
        override fun getPreviousTransition(zone: TimeZone?, base: Long, inclusive: Boolean): CalendarTimeCore.Transition? = (zone as BasicTimeZone).getPreviousTransition(base, inclusive)?.let { transition -> CalendarTimeCore.Transition { transition.getTime() } }
    }

    private inner class LimitsOwner : CalendarLimitsCore.Owner {
        private val p get() = this@Calendar
        override fun handleGetLimit(field: Int, type: Int): Int = p.handleGetLimit(field, type)
        override fun getLimit(field: Int, type: Int): Int = p.getLimit(field, type)
        override fun getMinimalDaysInFirstWeek(): Int = p.getMinimalDaysInFirstWeek()
        override fun storedFirstDayOfWeek(): Int = p.values.firstDayOfWeek
        override fun get(field: Int): Int = p.get(field)
        override fun set(field: Int, value: Int) = p.set(field, value)
        override fun getActualMaximum(field: Int): Int = p.getActualMaximum(field)
        override fun getActualMinimum(field: Int): Int = p.getActualMinimum(field)
        override fun handleGetMonthLength(year: Int, month: Int): Int = p.handleGetMonthLength(year, month)
        override fun handleGetYearLength(year: Int): Int = p.handleGetYearLength(year)
        override fun inTemporalLeapYear(): Boolean = p.inTemporalLeapYear()
        override fun cloneCalendar(): CalendarLimitsCore.Owner = p.clone().LimitsOwner()
        override fun complete() = p.complete()
        override fun setLenient(value: Boolean) = p.setLenient(value)
        override fun prepareGetActual(field: Int, minimum: Boolean) = p.prepareGetActual(field, minimum)
        override fun add(field: Int, amount: Int) = p.add(field, amount)
    }

    private inner class AddOwner : CalendarAddCore.Owner {
        private val p get() = this@Calendar
        override fun get(field: Int): Int = p.get(field)
        override fun set(field: Int, value: Int) = p.set(field, value)
        override fun pinField(field: Int) = p.pinField(field)
        override fun isEra0CountingBackward(): Boolean = p.isEra0CountingBackward()
        override fun isLenient(): Boolean = p.isLenient()
        override fun setLenient(value: Boolean) = p.setLenient(value)
        override fun complete() = p.complete()
        override fun fieldName(field: Int): String? = p.fieldName(field)
        override fun getTimeInMillis(): Long = p.getTimeInMillis()
        override fun setTimeInMillis(value: Long) = p.setTimeInMillis(value)
        override fun storedTimeInMillis(): Long = p.values.time
        override fun storedSkippedWallTime(): Int = p.values.skippedWallTime
        override fun getImmediatePreviousZoneTransition(time: Long): Long? = p.time().getImmediatePreviousZoneTransition(time)
    }

    private inner class RollOwner : CalendarRollCore.Owner {
        private val p get() = this@Calendar
        override fun complete() = p.complete()
        override fun get(field: Int): Int = p.get(field)
        override fun internalGet(field: Int): Int = p.internalGet(field)
        override fun internalGetMonth(): Int = p.internalGetMonth()
        override fun set(field: Int, value: Int) = p.set(field, value)
        override fun clear(field: Int) = p.clear(field)
        override fun getActualMinimum(field: Int): Int = p.getActualMinimum(field)
        override fun getActualMaximum(field: Int): Int = p.getActualMaximum(field)
        override fun getMaximum(field: Int): Int = p.getMaximum(field)
        override fun pinField(field: Int) = p.pinField(field)
        override fun isEra0CountingBackward(): Boolean = p.isEra0CountingBackward()
        override fun getFirstDayOfWeek(): Int = p.getFirstDayOfWeek()
        override fun getMinimalDaysInFirstWeek(): Int = p.getMinimalDaysInFirstWeek()
        override fun getTimeInMillis(): Long = p.getTimeInMillis()
        override fun setTimeInMillis(value: Long) = p.setTimeInMillis(value)
        override fun storedTimeInMillis(): Long = p.values.time
        override fun assignStoredTime(value: Long) = run { p.values.time = value }
        override fun fieldName(field: Int): String? = p.fieldName(field)
    }

    private inner class DifferenceOwner : CalendarDifferenceCore.Owner {
        private val p get() = this@Calendar
        override fun getTimeInMillis(): Long = p.getTimeInMillis()
        override fun setTimeInMillis(value: Long) = p.setTimeInMillis(value)
        override fun add(field: Int, amount: Int) = p.add(field, amount)
    }

    private inner class ComparisonOwner : CalendarComparisonCore.Owner<Calendar> {
        private val p get() = this@Calendar
        override val self: Calendar get() = p
        override fun sameRuntimeClass(value: Any): Boolean = p::class == value::class
        override fun asCalendar(value: Any?): Calendar? = value as? Calendar
        override fun isEquivalentTo(calendar: Calendar): Boolean = p.isEquivalentTo(calendar)
        override fun getTimeInMillis(calendar: Calendar): Long = calendar.getTimeInMillis()
        override fun getTime(calendar: Calendar): Date = calendar.getTime()
        override fun isLenient(calendar: Calendar): Boolean = calendar.isLenient()
        override fun getFirstDayOfWeek(calendar: Calendar): Int = calendar.getFirstDayOfWeek()
        override fun getMinimalDaysInFirstWeek(calendar: Calendar): Int = calendar.getMinimalDaysInFirstWeek()
        override fun getTimeZone(calendar: Calendar): Any? = calendar.getTimeZone()
        override fun getRepeatedWallTimeOption(calendar: Calendar): Int = calendar.getRepeatedWallTimeOption()
        override fun getSkippedWallTimeOption(calendar: Calendar): Int = calendar.getSkippedWallTimeOption()
        override fun storedLenient(): Boolean = p.values.lenient
        override fun storedFirstDayOfWeek(): Int = p.values.firstDayOfWeek
        override fun storedMinimalDaysInFirstWeek(): Int = p.values.minimalDaysInFirstWeek
        override fun storedRepeatedWallTime(): Int = p.values.repeatedWallTime
        override fun storedSkippedWallTime(): Int = p.values.skippedWallTime
        override fun storedZoneHashCode(): Int = p.values.zone!!.hashCode()
    }

    private inner class WeekendOwner : CalendarWeekendCore.Owner<Calendar> {
        private val p get() = this@Calendar
        override val self: Calendar get() = p
        override fun setFirstDayOfWeek(value: Int) = p.setFirstDayOfWeek(value)
        override fun setMinimalDaysInFirstWeek(value: Int) = p.setMinimalDaysInFirstWeek(value)
        override fun setWeekData(value: CalendarWeekData): Calendar = p.setWeekData(WeekData(value))
        override fun get(field: Int): Int = p.get(field)
        override fun internalGet(field: Int): Int = p.internalGet(field)
        override fun getDayOfWeekType(day: Int): Int = p.getDayOfWeekType(day)
        override fun getWeekendTransition(day: Int): Int = p.getWeekendTransition(day)
        override fun setTime(date: Date?) = p.setTime(date)
        override fun isWeekend(): Boolean = p.isWeekend()
    }

    private inner class WeekendState : CalendarWeekendCore.State {
        override val firstDayOfWeek get() = values.firstDayOfWeek
        override val minimalDaysInFirstWeek get() = values.minimalDaysInFirstWeek
        override var weekendOnset: Int
            get() = this@Calendar.weekendOnset
            set(value) { this@Calendar.weekendOnset = value }
        override var weekendOnsetMillis: Int
            get() = this@Calendar.weekendOnsetMillis
            set(value) { this@Calendar.weekendOnsetMillis = value }
        override var weekendCease: Int
            get() = this@Calendar.weekendCease
            set(value) { this@Calendar.weekendCease = value }
        override var weekendCeaseMillis: Int
            get() = this@Calendar.weekendCeaseMillis
            set(value) { this@Calendar.weekendCeaseMillis = value }
    }
}
