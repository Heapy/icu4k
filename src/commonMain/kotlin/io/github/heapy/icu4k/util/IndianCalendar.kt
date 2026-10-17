// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2011, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.text.RbnfObjectStreamField

import io.github.heapy.icu4k.impl.calendar.IndianCalendarCore
import kotlin.time.Clock

/** Saka calendar with Gregorian leap-year alignment. */
open class IndianCalendar : Calendar {
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
    constructor(zone: TimeZone?, locale: ULocale?) : super(zone, locale) { setTimeInMillis(Clock.System.now().toEpochMilliseconds()) }
    constructor(zone: TimeZone?) : this(zone, ULocale.getDefault(ULocale.Category.FORMAT))
    protected constructor(other: IndianCalendar, copyMarker: Unit) : super(other, copyMarker)
    private fun indian(): IndianCalendarCore = IndianCalendarCore(Binding())
    override fun handleGetExtendedYear(): Int = indian().handleGetExtendedYear()
    override fun handleGetLimit(field: Int, limitType: Int): Int = indian().handleGetLimit(field, limitType)
    override fun handleGetMonthLength(extendedYear: Int, month: Int): Int = indian().handleGetMonthLength(extendedYear, month)
    override fun handleGetYearLength(extendedYear: Int): Int = indian().handleGetYearLength(extendedYear)
    override fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int = indian().handleComputeMonthStart(extendedYear, month, useMonth)
    override fun handleComputeFields(julianDay: Int) = indian().handleComputeFields(julianDay)
    override fun getRelatedYearDifference(): Int = indian().getRelatedYearDifference()
    override fun getType(): String = indian().getType()
    override fun cloneNative(): Calendar = IndianCalendar(this, Unit)
    private inner class Binding : IndianCalendarCore.Owner {
        override fun newerField(defaultField: Int, alternateField: Int): Int = this@IndianCalendar.newerField(defaultField, alternateField)
        override fun internalGet(field: Int, defaultValue: Int): Int = this@IndianCalendar.internalGet(field, defaultValue)
        override fun internalSet(field: Int, value: Int) = this@IndianCalendar.internalSet(field, value)
        override fun getGregorianYear(): Int = this@IndianCalendar.getGregorianYear()
        override fun computeGregorianMonthStart(year: Int, month: Int): Int = this@IndianCalendar.computeGregorianMonthStart(year, month)
        override fun baseGetYearLength(year: Int): Int = super@IndianCalendar.handleGetYearLength(year)
    }
    companion object {
        private const val serialVersionUID = 3617859668165014834L
        private val serialPersistentFields: Array<RbnfObjectStreamField> = timeStateSerialFields("IndianCalendar")
        const val CHAITRA = 0; const val VAISAKHA = 1; const val JYAISTHA = 2; const val ASADHA = 3
        const val SRAVANA = 4; const val BHADRA = 5; const val ASVINA = 6; const val KARTIKA = 7
        const val AGRAHAYANA = 8; const val PAUSA = 9; const val MAGHA = 10; const val PHALGUNA = 11
        const val IE = 0
    }
}
