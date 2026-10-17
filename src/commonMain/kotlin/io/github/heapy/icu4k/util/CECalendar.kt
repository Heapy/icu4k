// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.text.RbnfObjectStreamField

import io.github.heapy.icu4k.impl.calendar.CECalendarCore
import kotlin.time.Clock

/** Shared thirteen-month calendar base. Constructors are internal to retain ICU's package-only extensibility. */
abstract class CECalendar : Calendar {
    internal constructor() : this(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT))
    @Suppress("DEPRECATION")
    internal constructor(locale: ULocale?) : this(TimeZone.forULocaleOrDefault(locale), locale)
    internal constructor(zone: TimeZone?) : this(zone, ULocale.getDefault(ULocale.Category.FORMAT))
    internal constructor(date: Date?) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) { setTime(date) }
    internal constructor(year: Int, month: Int, date: Int) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) {
        set(YEAR, year); set(MONTH, month); set(DATE, date)
    }
    internal constructor(year: Int, month: Int, date: Int, hour: Int, minute: Int, second: Int) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) {
        set(YEAR, year); set(MONTH, month); set(DATE, date)
        set(HOUR_OF_DAY, hour); set(MINUTE, minute); set(SECOND, second)
    }
    internal constructor(zone: TimeZone?, locale: ULocale?) : super(zone, locale) {
        setTimeInMillis(Clock.System.now().toEpochMilliseconds())
    }
    internal constructor(other: CECalendar, copyMarker: Unit) : super(other, copyMarker)
    protected abstract fun getJDEpochOffset(): Int
    protected abstract fun extendedYearToEra(year: Int): Int
    protected abstract fun extendedYearToYear(year: Int): Int
    private fun ce() = CECalendarCore(Binding())
    override fun handleComputeFields(julianDay: Int) = ce().handleComputeFields(julianDay)
    override fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int = ce().handleComputeMonthStart(extendedYear, month, useMonth)
    override fun handleGetLimit(field: Int, limitType: Int): Int = ce().handleGetLimit(field, limitType)
    override fun handleGetMonthLength(extendedYear: Int, month: Int): Int = ce().handleGetMonthLength(extendedYear, month)
    override fun getTemporalMonthCode(): String = ce().getTemporalMonthCode()
    override fun setTemporalMonthCode(code: String?) = ce().setTemporalMonthCode(code)
    private inner class Binding : CECalendarCore.Owner {
        override fun getJDEpochOffset(): Int = this@CECalendar.getJDEpochOffset()
        override fun extendedYearToEra(year: Int): Int = this@CECalendar.extendedYearToEra(year)
        override fun extendedYearToYear(year: Int): Int = this@CECalendar.extendedYearToYear(year)
        override fun internalSet(field: Int, value: Int) = this@CECalendar.internalSet(field, value)
        override fun get(field: Int): Int = this@CECalendar.get(field)
        override fun set(field: Int, value: Int) = this@CECalendar.set(field, value)
        override fun baseGetTemporalMonthCode(): String = super@CECalendar.getTemporalMonthCode()
        override fun baseSetTemporalMonthCode(code: String?) = super@CECalendar.setTemporalMonthCode(code)
    }
    companion object {
        private const val serialVersionUID = -999547623066414271L
        private val serialPersistentFields: Array<RbnfObjectStreamField> = timeStateSerialFields("CECalendar")
        fun ceToJD(year: Long, month: Int, day: Int, epoch: Int): Int = CECalendarCore.ceToJD(year, month, day, epoch)
        fun jdToCE(julianDay: Int, epoch: Int, fields: IntArray?) = CECalendarCore.jdToCE(julianDay, epoch, fields)
    }
}
