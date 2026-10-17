// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.calendar.TaiwanCalendarCore

/** Gregorian dates numbered relative to the Minguo era. */
open class TaiwanCalendar : GregorianCalendar {
    constructor() : super()
    constructor(locale: ULocale?) : super(locale)
    constructor(date: Date?) : this() { setTime(date) }
    constructor(year: Int, month: Int, date: Int) : super(year, month, date)
    constructor(year: Int, month: Int, date: Int, hour: Int, minute: Int, second: Int) : super(year, month, date, hour, minute, second)
    constructor(zone: TimeZone?, locale: ULocale?) : super(zone, locale)
    constructor(zone: TimeZone?) : super(zone)
    protected constructor(other: TaiwanCalendar, copyMarker: Unit) : super(other, copyMarker)
    private fun taiwan(): TaiwanCalendarCore = TaiwanCalendarCore(Binding())
    override fun handleGetExtendedYear(): Int = taiwan().handleGetExtendedYear()
    override fun handleComputeFields(julianDay: Int) = taiwan().handleComputeFields(julianDay)
    override fun handleGetLimit(field: Int, limitType: Int): Int = taiwan().handleGetLimit(field, limitType)
    override fun getType(): String = taiwan().getType()
    override fun cloneNative(): Calendar = TaiwanCalendar(this, Unit)
    private inner class Binding : TaiwanCalendarCore.Owner {
        override fun newerField(defaultField: Int, alternateField: Int): Int = this@TaiwanCalendar.newerField(defaultField, alternateField)
        override fun internalGet(field: Int): Int = this@TaiwanCalendar.internalGet(field)
        override fun internalGet(field: Int, defaultValue: Int): Int = this@TaiwanCalendar.internalGet(field, defaultValue)
        override fun internalSet(field: Int, value: Int) = this@TaiwanCalendar.internalSet(field, value)
        override fun gregorianComputeFields(julianDay: Int) = super@TaiwanCalendar.handleComputeFields(julianDay)
        override fun gregorianGetLimit(field: Int, limitType: Int): Int = super@TaiwanCalendar.handleGetLimit(field, limitType)
    }
    companion object { const val BEFORE_MINGUO = 0; const val MINGUO = 1 }
}
