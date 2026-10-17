// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream
import io.github.heapy.icu4k.text.RbnfObjectStreamField

import io.github.heapy.icu4k.impl.calendar.GregorianCalendarCore
import kotlin.time.Clock

/** Gregorian/Julian hybrid calendar with ICU's configurable historical cutover. */
open class GregorianCalendar : Calendar {
    // No property initializer: callbacks from Calendar's constructor can install the zero state.
    private var gregorianState: GregorianCalendarCore<GregorianCalendar>?
    private val gregorian: GregorianCalendarCore<GregorianCalendar>
        get() = gregorianState ?: GregorianCalendarCore.beforeFieldInitialization(Binding()).also { gregorianState = it }
    constructor(zone: TimeZone?, locale: ULocale?) : super(zone, locale) {
        gregorianState = initializeCutoverDefaults()
        setTimeInMillis(Clock.System.now().toEpochMilliseconds())
    }
    constructor() : this(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT))
    constructor(zone: TimeZone?) : this(zone, ULocale.getDefault(ULocale.Category.FORMAT))
    @Suppress("DEPRECATION")
    constructor(locale: ULocale?) : this(TimeZone.forULocaleOrDefault(locale), locale)
    constructor(year: Int, month: Int, date: Int) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) {
        gregorianState = initializeCutoverDefaults()
        set(ERA, AD); set(YEAR, year); set(MONTH, month); set(DATE, date)
    }
    constructor(year: Int, month: Int, date: Int, hour: Int, minute: Int) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) {
        gregorianState = initializeCutoverDefaults()
        set(ERA, AD); set(YEAR, year); set(MONTH, month); set(DATE, date); set(HOUR_OF_DAY, hour); set(MINUTE, minute)
    }
    constructor(year: Int, month: Int, date: Int, hour: Int, minute: Int, second: Int) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) {
        gregorianState = initializeCutoverDefaults()
        set(ERA, AD); set(YEAR, year); set(MONTH, month); set(DATE, date); set(HOUR_OF_DAY, hour); set(MINUTE, minute); set(SECOND, second)
    }
    private fun initializeCutoverDefaults(): GregorianCalendarCore<GregorianCalendar> = gregorian.also { it.initializeCutoverDefaults() }
    /** Copy path for Native subclasses; skips locale, clock and virtual initialization callbacks. */
    protected constructor(other: GregorianCalendar, copyMarker: Unit) : super(other, copyMarker) {
        gregorianState = other.gregorian.copyFor(Binding())
    }
    protected var isGregorian: Boolean
        get() = gregorian.isGregorian
        set(value) { gregorian.isGregorian = value }
    protected var invertGregorian: Boolean
        get() = gregorian.invertGregorian
        set(value) { gregorian.invertGregorian = value }
    open fun setGregorianChange(date: Date?) = gregorian.setGregorianChange(date)
    fun getGregorianChange(): Date = gregorian.getGregorianChange()
    open fun isLeapYear(year: Int): Boolean = gregorian.isLeapYear(year)
    override fun isEquivalentTo(other: Calendar?): Boolean = super.isEquivalentTo(other) && gregorian.hasSameCutover((other as GregorianCalendar).gregorian)
    override fun hashCode(): Int = gregorian.hashCode(super.hashCode())
    override fun roll(field: Int, amount: Int) = gregorian.roll(field, amount)
    override fun getActualMinimum(field: Int): Int = gregorian.getActualMinimum(field)
    override fun getActualMaximum(field: Int): Int = gregorian.getActualMaximum(field)
    internal fun inDaylightTime(): Boolean = gregorian.inDaylightTime()
    override fun handleGetLimit(field: Int, limitType: Int): Int = gregorian.handleGetLimit(field, limitType)
    override fun handleGetMonthLength(extendedYear: Int, month: Int): Int = gregorian.handleGetMonthLength(extendedYear, month)
    override fun handleGetYearLength(extendedYear: Int): Int = gregorian.handleGetYearLength(extendedYear)
    override fun handleComputeFields(julianDay: Int) = gregorian.handleComputeFields(julianDay)
    override fun handleGetExtendedYear(): Int = gregorian.handleGetExtendedYear()
    override fun handleComputeJulianDay(bestField: Int): Int = gregorian.handleComputeJulianDay(bestField)
    override fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int = gregorian.handleComputeMonthStart(extendedYear, month, useMonth)
    override fun isEra0CountingBackward(): Boolean = gregorian.isEra0CountingBackward()
    override fun getType(): String = gregorian.getType()
    public override fun clone(): GregorianCalendar {
        val copy = super.clone() as GregorianCalendar
        copy.gregorianState = gregorian.copyFor(copy.Binding())
        return copy
    }
    override fun cloneNative(): Calendar = GregorianCalendar(this, Unit)
    private inner class Binding : GregorianCalendarCore.Owner<GregorianCalendar> {
        private val p get() = this@GregorianCalendar
        override fun cutoverExtendedYear(date: Date): Int = GregorianCalendar(p.getTimeZone()).apply { setTime(date) }.get(EXTENDED_YEAR)
        override fun isLeapYear(year: Int): Boolean = p.isLeapYear(year)
        override fun getGregorianMonth(): Int = p.getGregorianMonth()
        override fun getGregorianDayOfMonth(): Int = p.getGregorianDayOfMonth()
        override fun getGregorianDayOfYear(): Int = p.getGregorianDayOfYear()
        override fun getGregorianYear(): Int = p.getGregorianYear()
        override fun internalSet(field: Int, value: Int) = p.internalSet(field, value)
        override fun internalGet(field: Int): Int = p.internalGet(field)
        override fun internalGet(field: Int, defaultValue: Int): Int = p.internalGet(field, defaultValue)
        override fun internalGetMonth(): Int = p.internalGetMonth()
        override fun newerField(defaultField: Int, alternateField: Int): Int = p.newerField(defaultField, alternateField)
        override fun baseComputeJulianDay(bestField: Int): Int = super@GregorianCalendar.handleComputeJulianDay(bestField)
        override fun get(field: Int): Int = p.get(field)
        override fun set(field: Int, value: Int) = p.set(field, value)
        override fun getYearLength(year: Int): Int = p.handleGetYearLength(year)
        override fun getFirstDayOfWeek(): Int = p.getFirstDayOfWeek()
        override fun getMinimalDaysInFirstWeek(): Int = p.getMinimalDaysInFirstWeek()
        override fun weekNumber(dayOfPeriod: Int, dayOfWeek: Int): Int = p.weekNumber(dayOfPeriod, dayOfWeek)
        override fun baseRoll(field: Int, amount: Int) = super@GregorianCalendar.roll(field, amount)
        override fun getMinimum(field: Int): Int = p.getMinimum(field)
        override fun baseGetActualMaximum(field: Int): Int = super@GregorianCalendar.getActualMaximum(field)
        override fun cloneCalendar(): GregorianCalendar = p.clone()
        override fun setLenient(calendar: GregorianCalendar, value: Boolean) = calendar.setLenient(value)
        override fun get(calendar: GregorianCalendar, field: Int): Int = calendar.get(field)
        override fun set(calendar: GregorianCalendar, field: Int, value: Int) = calendar.set(field, value)
        override fun getTime(calendar: GregorianCalendar): Date = calendar.getTime()
        override fun setTime(calendar: GregorianCalendar, date: Date) = calendar.setTime(date)
        override fun zoneUsesDaylightTime(): Boolean = p.getTimeZone()!!.useDaylightTime()
        override fun complete() = p.complete()
    }
    private fun writeObject(output: PluralObjectOutputStream) = timeStateWriteFields(output, "GregorianCalendar", mapOf("gregorianCutover" to gregorian.getGregorianChange().getTime()))
    private fun readObject(input: PluralObjectInputStream) {
        val cutover = timeStateReadFields(input, "GregorianCalendar")["gregorianCutover"] as Long
        gregorianState = GregorianCalendarCore.beforeFieldInitialization(Binding()).also { it.restoreSerializedCutover(cutover) }
    }
    companion object {
        private const val serialVersionUID = 9199388694351062137L
        private val serialPersistentFields: Array<RbnfObjectStreamField> = timeStateSerialFields("GregorianCalendar")
        const val BC = 0
        const val AD = 1
    }
}
