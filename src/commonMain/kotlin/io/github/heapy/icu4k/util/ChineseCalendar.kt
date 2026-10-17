// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.calendar.ChineseCalendarCore
import kotlin.time.Clock

/** Chinese lunisolar calendar, with separate astronomical and display time zones. */
open class ChineseCalendar : Calendar {
    private var chineseState: ChineseCalendarCore?
    private fun chinese(): ChineseCalendarCore = chineseState ?: ChineseCalendarCore(Binding()) { throw NullPointerException() }.also { chineseState = it }
    constructor() : this(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT))
    constructor(zone: TimeZone?) : this(zone, ULocale.getDefault(ULocale.Category.FORMAT))
    @Suppress("DEPRECATION")
    constructor(locale: ULocale?) : this(TimeZone.forULocaleOrDefault(locale), locale)
    constructor(zone: TimeZone?, locale: ULocale?) : this(zone, locale, CHINA_ZONE)
    constructor(date: Date?) : this() { setTime(date) }
    constructor(year: Int, month: Int, isLeapMonth: Int, date: Int) : this(year, month, isLeapMonth, date, 0, 0, 0)
    constructor(era: Int, year: Int, month: Int, isLeapMonth: Int, date: Int) : this(era, year, month, isLeapMonth, date, 0, 0, 0)
    constructor(year: Int, month: Int, isLeapMonth: Int, date: Int, hour: Int, minute: Int, second: Int) : this() {
        set(MILLISECOND, 0); set(YEAR, year); set(MONTH, month); set(IS_LEAP_MONTH, isLeapMonth)
        set(DATE, date); set(HOUR_OF_DAY, hour); set(MINUTE, minute); set(SECOND, second)
    }
    constructor(era: Int, year: Int, month: Int, isLeapMonth: Int, date: Int, hour: Int, minute: Int, second: Int) : this() {
        set(MILLISECOND, 0); set(ERA, era); set(YEAR, year); set(MONTH, month); set(IS_LEAP_MONTH, isLeapMonth)
        set(DATE, date); set(HOUR_OF_DAY, hour); set(MINUTE, minute); set(SECOND, second)
    }
    @Deprecated("This API is ICU internal only.")
    protected constructor(zone: TimeZone?, locale: ULocale?, zoneAstroCalc: TimeZone?) : this(zone, locale, { zoneAstroCalc!!.getOffset(it) })
    internal constructor(zone: TimeZone?, locale: ULocale?, astronomicalOffset: (Long) -> Int) : super(zone, locale) {
        chineseState = ChineseCalendarCore(Binding(), astronomicalOffset)
        setTimeInMillis(Clock.System.now().toEpochMilliseconds())
    }
    protected constructor(other: ChineseCalendar, copyMarker: Unit) : super(other, copyMarker) {
        chineseState = other.chinese().copyForOwner(Binding())
    }
    override fun handleGetLimit(field: Int, limitType: Int): Int = chinese().handleGetLimit(field, limitType)
    override fun handleGetExtendedYear(): Int = chinese().handleGetExtendedYear()
    override fun handleGetMonthLength(extendedYear: Int, month: Int): Int = chinese().handleGetMonthLength(extendedYear, month)
    override fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int = chinese().handleComputeMonthStart(extendedYear, month, useMonth)
    override fun handleComputeFields(julianDay: Int) = chinese().handleComputeFields(julianDay)
    override fun getFieldResolutionTable(): Array<Array<IntArray>> = chinese().getFieldResolutionTable()
    override fun internalGetMonth(): Int = chinese().internalGetMonth()
    override fun internalGetMonth(defaultValue: Int): Int = chinese().internalGetMonth(defaultValue)
    override fun add(field: Int, amount: Int) = chinese().add(field, amount)
    override fun roll(field: Int, amount: Int) = chinese().roll(field, amount)
    override fun getActualMaximum(field: Int): Int = chinese().getActualMaximum(field)
    override fun getTemporalMonthCode(): String = chinese().getTemporalMonthCode()
    override fun setTemporalMonthCode(code: String?) = chinese().setTemporalMonthCode(code)
    override fun inTemporalLeapYear(): Boolean = chinese().inTemporalLeapYear()
    override fun getType(): String = "chinese"
    @Deprecated("This API is ICU internal only.")
    override fun haveDefaultCentury(): Boolean = false
    override fun cloneNative(): Calendar = ChineseCalendar(this, Unit)
    override fun clone(): Calendar = super.clone().also { copy ->
        (copy as ChineseCalendar).chineseState = chinese().copyForOwner(copy.Binding())
    }
    private inner class Binding : ChineseCalendarCore.Owner {
        private val p get() = this@ChineseCalendar
        override fun internalGet(field: Int): Int = p.internalGet(field)
        override fun internalGet(field: Int, defaultValue: Int): Int = p.internalGet(field, defaultValue)
        override fun internalSet(field: Int, value: Int) = p.internalSet(field, value)
        override fun newestStamp(first: Int, last: Int, best: Int): Int = p.newestStamp(first, last, best)
        override fun getStamp(field: Int): Int = p.getStamp(field)
        override fun resolveFields(table: Array<Array<IntArray>>): Int = p.resolveFields(table)
        override fun computeGregorianFields(julianDay: Int) = p.computeGregorianFields(julianDay)
        override fun getGregorianYear(): Int = p.getGregorianYear()
        override fun getGregorianMonth(): Int = p.getGregorianMonth()
        override fun complete() = p.complete()
        override fun getActualMaximum(field: Int): Int = p.getActualMaximum(field)
        override fun set(field: Int, value: Int) = p.set(field, value)
        override fun get(field: Int): Int = p.get(field)
        override fun roll(field: Int, amount: Int) = p.roll(field, amount)
        override fun setLenient(value: Boolean) = p.setLenient(value)
        override fun prepareGetActual(field: Int, minimum: Boolean) = p.prepareGetActual(field, minimum)
        override fun superGetActualMaximum(field: Int): Int = super@ChineseCalendar.getActualMaximum(field)
        override fun superAdd(field: Int, amount: Int) = super@ChineseCalendar.add(field, amount)
        override fun superRoll(field: Int, amount: Int) = super@ChineseCalendar.roll(field, amount)
        override fun superGetTemporalMonthCode(): String = super@ChineseCalendar.getTemporalMonthCode()
        override fun superSetTemporalMonthCode(value: String?) = super@ChineseCalendar.setTemporalMonthCode(value)
        override fun cloneCalendar(): ChineseCalendarCore.Copy = (p.clone() as ChineseCalendar).Binding()
    }
    companion object {
        private val CHINA_ZONE = SimpleTimeZone(28800000, "CHINA_ZONE").freeze()
    }
}
