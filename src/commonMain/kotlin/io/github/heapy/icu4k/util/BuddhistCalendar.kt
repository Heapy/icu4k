// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2012, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.calendar.BuddhistCalendarCore

/** Gregorian dates numbered in the Buddhist era. */
open class BuddhistCalendar : GregorianCalendar {
    constructor() : super()
    constructor(locale: ULocale?) : super(locale)
    constructor(date: Date?) : this() { setTime(date) }
    constructor(year: Int, month: Int, date: Int) : super(year, month, date)
    constructor(year: Int, month: Int, date: Int, hour: Int, minute: Int, second: Int) : super(year, month, date, hour, minute, second)
    constructor(zone: TimeZone?, locale: ULocale?) : super(zone, locale)
    constructor(zone: TimeZone?) : super(zone)
    protected constructor(other: BuddhistCalendar, copyMarker: Unit) : super(other, copyMarker)
    // Stateless adapters are safe even during virtual callbacks from the superclass constructor.
    private fun buddhist(): BuddhistCalendarCore = BuddhistCalendarCore(Binding())
    override fun handleGetExtendedYear(): Int = buddhist().handleGetExtendedYear()
    override fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int = buddhist().handleComputeMonthStart(extendedYear, month, useMonth)
    override fun handleComputeFields(julianDay: Int) = buddhist().handleComputeFields(julianDay)
    override fun handleGetLimit(field: Int, limitType: Int): Int = buddhist().handleGetLimit(field, limitType)
    override fun getType(): String = buddhist().getType()
    override fun isEra0CountingBackward(): Boolean = buddhist().isEra0CountingBackward()
    override fun cloneNative(): Calendar = BuddhistCalendar(this, Unit)
    private inner class Binding : BuddhistCalendarCore.Owner {
        override fun newerField(defaultField: Int, alternateField: Int): Int = this@BuddhistCalendar.newerField(defaultField, alternateField)
        override fun internalGet(field: Int): Int = this@BuddhistCalendar.internalGet(field)
        override fun internalGet(field: Int, defaultValue: Int): Int = this@BuddhistCalendar.internalGet(field, defaultValue)
        override fun internalSet(field: Int, value: Int) = this@BuddhistCalendar.internalSet(field, value)
        override fun gregorianComputeFields(julianDay: Int) = super@BuddhistCalendar.handleComputeFields(julianDay)
        override fun gregorianComputeMonthStart(year: Int, month: Int, useMonth: Boolean): Int = super@BuddhistCalendar.handleComputeMonthStart(year, month, useMonth)
        override fun gregorianGetLimit(field: Int, limitType: Int): Int = super@BuddhistCalendar.handleGetLimit(field, limitType)
    }
    companion object { const val BE = 0 }
}
