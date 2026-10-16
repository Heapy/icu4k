// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.calendar.PersianCalendarCore
import kotlin.time.Clock

/** ICU's corrected 33-year arithmetic Persian calendar. */
@Deprecated("ICU internal API.")
open class PersianCalendar : Calendar {
    constructor(zone: TimeZone?, locale: ULocale?) : super(zone, locale) { setTimeInMillis(Clock.System.now().toEpochMilliseconds()) }
    constructor(zone: TimeZone?) : this(zone, ULocale.getDefault(ULocale.Category.FORMAT))
    protected constructor(other: PersianCalendar, copyMarker: Unit) : super(other, copyMarker)
    private fun persian(): PersianCalendarCore = PersianCalendarCore(Binding())
    override fun handleGetExtendedYear(): Int = persian().handleGetExtendedYear()
    override fun handleGetLimit(field: Int, limitType: Int): Int = persian().handleGetLimit(field, limitType)
    override fun handleGetMonthLength(extendedYear: Int, month: Int): Int = persian().handleGetMonthLength(extendedYear, month)
    override fun handleGetYearLength(extendedYear: Int): Int = persian().handleGetYearLength(extendedYear)
    override fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int = persian().handleComputeMonthStart(extendedYear, month, useMonth)
    override fun handleComputeFields(julianDay: Int) = persian().handleComputeFields(julianDay)
    override fun getRelatedYearDifference(): Int = persian().getRelatedYearDifference()
    override fun getType(): String = persian().getType()
    override fun cloneNative(): Calendar = PersianCalendar(this, Unit)
    private inner class Binding : PersianCalendarCore.Owner {
        override fun newerField(defaultField: Int, alternateField: Int): Int = this@PersianCalendar.newerField(defaultField, alternateField)
        override fun internalGet(field: Int, defaultValue: Int): Int = this@PersianCalendar.internalGet(field, defaultValue)
        override fun internalSet(field: Int, value: Int) = this@PersianCalendar.internalSet(field, value)
    }
}
