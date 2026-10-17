// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.calendar.CalendarFieldsCore
import io.github.heapy.icu4k.impl.calendar.HebrewCalendarCore
import kotlin.time.Clock

/** Hebrew lunisolar calendar, including the intercalary Adar I month. */
open class HebrewCalendar : Calendar {
    constructor() : this(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT))
    @Suppress("DEPRECATION")
    constructor(locale: ULocale?) : this(TimeZone.forULocaleOrDefault(locale), locale)
    constructor(date: Date?) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) { setTime(date) }
    constructor(year: Int, month: Int, date: Int) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) {
        set(YEAR, year); set(MONTH, month); set(DATE, date)
    }
    constructor(year: Int, month: Int, date: Int, hour: Int, minute: Int, second: Int) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) {
        set(YEAR, year); set(MONTH, month); set(DATE, date)
        set(HOUR_OF_DAY, hour); set(MINUTE, minute); set(SECOND, second)
    }
    constructor(zone: TimeZone?, locale: ULocale?) : super(zone, locale) {
        setTimeInMillis(Clock.System.now().toEpochMilliseconds())
    }
    constructor(zone: TimeZone?) : this(zone, ULocale.getDefault(ULocale.Category.FORMAT))
    protected constructor(other: HebrewCalendar, copyMarker: Unit) : super(other, copyMarker)
    private fun hebrew() = HebrewCalendarCore(Binding())
    override fun add(field: Int, amount: Int) = hebrew().add(field, amount)
    override fun roll(field: Int, amount: Int) = hebrew().roll(field, amount)
    override fun handleGetLimit(field: Int, limitType: Int): Int = hebrew().handleGetLimit(field, limitType)
    override fun handleGetMonthLength(extendedYear: Int, month: Int): Int = hebrew().handleGetMonthLength(extendedYear, month)
    override fun handleGetYearLength(extendedYear: Int): Int = hebrew().handleGetYearLength(extendedYear)
    override fun validateField(field: Int) = hebrew().validateField(field)
    override fun handleComputeFields(julianDay: Int) = hebrew().handleComputeFields(julianDay)
    override fun handleGetExtendedYear(): Int = hebrew().handleGetExtendedYear()
    override fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int = hebrew().handleComputeMonthStart(extendedYear, month, useMonth)
    override fun internalGetMonth(): Int = hebrew().internalGetMonth()
    override fun getType(): String = hebrew().getType()
    final override fun getRelatedYearDifference(): Int = hebrew().getRelatedYearDifference()
    override fun inTemporalLeapYear(): Boolean = hebrew().inTemporalLeapYear()
    override fun getTemporalMonthCode(): String = hebrew().getTemporalMonthCode()
    override fun setTemporalMonthCode(code: String?) = hebrew().setTemporalMonthCode(code)
    override fun cloneNative(): Calendar = HebrewCalendar(this, Unit)
    private inner class Binding : HebrewCalendarCore.Owner {
        private val p get() = this@HebrewCalendar
        override fun get(field: Int): Int = p.get(field)
        override fun set(field: Int, value: Int) = p.set(field, value)
        override fun pinField(field: Int) = p.pinField(field)
        override fun baseAdd(field: Int, amount: Int) = super@HebrewCalendar.add(field, amount)
        override fun baseRoll(field: Int, amount: Int) = super@HebrewCalendar.roll(field, amount)
        override fun handleGetYearLength(year: Int): Int = p.handleGetYearLength(year)
        override fun handleGetExtendedYear(): Int = p.handleGetExtendedYear()
        override fun internalGetMonth(): Int = p.internalGetMonth()
        override fun baseInternalGetMonth(): Int = super@HebrewCalendar.internalGetMonth()
        override fun baseValidateField(field: Int) = super@HebrewCalendar.validateField(field)
        override fun resolveMonthField(): Int = p.resolveFields(CalendarFieldsCore.MONTH_PRECEDENCE)
        override fun internalGet(field: Int): Int = p.internalGet(field)
        override fun internalGet(field: Int, defaultValue: Int): Int = p.internalGet(field, defaultValue)
        override fun newerField(defaultField: Int, alternateField: Int): Int = p.newerField(defaultField, alternateField)
        override fun internalSet(field: Int, value: Int) = p.internalSet(field, value)
    }
    companion object {
        const val TISHRI = 0; const val HESHVAN = 1; const val KISLEV = 2; const val TEVET = 3
        const val SHEVAT = 4; const val ADAR_1 = 5; const val ADAR = 6; const val NISAN = 7
        const val IYAR = 8; const val SIVAN = 9; const val TAMUZ = 10; const val AV = 11; const val ELUL = 12
        @Deprecated("This API is ICU internal only.")
        fun isLeapYear(year: Int): Boolean = HebrewCalendarCore.isLeapYear(year)
    }
}
