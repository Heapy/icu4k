// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.calendar.IslamicCalendarCore
import kotlin.time.Clock

/** Islamic calendar with astronomical, civil, tabular and Umm al-Qura calculation modes. */
open class IslamicCalendar : Calendar {
    private var islamicState: IslamicCalendarCore?
    private fun islamic(): IslamicCalendarCore = islamicState ?: IslamicCalendarCore.beforeFieldInitialization(Binding()).also { islamicState = it }
    private fun initialized(): IslamicCalendarCore = islamic().also { it.initializeDefaults() }
    constructor() : this(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT))
    constructor(zone: TimeZone?) : this(zone, ULocale.getDefault(ULocale.Category.FORMAT))
    @Suppress("DEPRECATION")
    constructor(locale: ULocale?) : this(TimeZone.forULocaleOrDefault(locale), locale)
    constructor(zone: TimeZone?, locale: ULocale?) : super(zone, locale) {
        islamicState = initialized()
        islamic().setCalcTypeForLocale(locale!!)
        setTimeInMillis(Clock.System.now().toEpochMilliseconds())
    }
    constructor(date: Date?) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) {
        islamicState = initialized()
        setTime(date)
    }
    constructor(year: Int, month: Int, date: Int) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) {
        islamicState = initialized()
        set(YEAR, year); set(MONTH, month); set(DATE, date)
    }
    constructor(year: Int, month: Int, date: Int, hour: Int, minute: Int, second: Int) : super(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT)) {
        islamicState = initialized()
        set(YEAR, year); set(MONTH, month); set(DATE, date)
        set(HOUR_OF_DAY, hour); set(MINUTE, minute); set(SECOND, second)
    }
    protected constructor(other: IslamicCalendar, copyMarker: Unit) : super(other, copyMarker) {
        islamicState = other.islamic().copyForOwner(Binding())
    }
    enum class CalculationType { ISLAMIC, ISLAMIC_CIVIL, ISLAMIC_UMALQURA, ISLAMIC_TBLA }
    open fun setCalculationType(type: CalculationType?) = islamic().setCalculationType(type?.let { IslamicCalendarCore.CalculationType.valueOf(it.name) })
    open fun getCalculationType(): CalculationType = CalculationType.valueOf(islamic().getCalculationType().name)
    open fun setCivil(value: Boolean) = islamic().setCivil(value)
    open fun isCivil(): Boolean = islamic().isCivil()
    override fun getType(): String = islamic().getType()
    override fun handleGetLimit(field: Int, limitType: Int): Int = islamic().handleGetLimit(field, limitType)
    override fun handleGetExtendedYear(): Int = islamic().handleGetExtendedYear()
    override fun handleGetMonthLength(extendedYear: Int, month: Int): Int = islamic().handleGetMonthLength(extendedYear, month)
    override fun handleGetYearLength(extendedYear: Int): Int = islamic().handleGetYearLength(extendedYear)
    override fun handleComputeMonthStart(extendedYear: Int, month: Int, useMonth: Boolean): Int = islamic().handleComputeMonthStart(extendedYear, month, useMonth)
    override fun handleComputeFields(julianDay: Int) = islamic().handleComputeFields(julianDay)
    final override fun getRelatedYear(): Int = islamic().getRelatedYear()
    final override fun setRelatedYear(year: Int) = islamic().setRelatedYear(year)
    override fun inTemporalLeapYear(): Boolean = islamic().inTemporalLeapYear()
    override fun cloneNative(): Calendar = IslamicCalendar(this, Unit)
    override fun clone(): Calendar = super.clone().also { copy ->
        (copy as IslamicCalendar).islamicState = islamic().copyForOwner(copy.Binding())
    }
    private inner class Binding : IslamicCalendarCore.Owner {
        private val p get() = this@IslamicCalendar
        override fun newerField(defaultField: Int, alternateField: Int): Int = p.newerField(defaultField, alternateField)
        override fun internalGet(field: Int, defaultValue: Int): Int = p.internalGet(field, defaultValue)
        override fun internalSet(field: Int, value: Int) = p.internalSet(field, value)
        override fun internalGetTimeInMillis(): Long = p.internalGetTimeInMillis()
        override fun getTimeInMillis(): Long = p.getTimeInMillis()
        override fun setTimeInMillis(value: Long) = p.setTimeInMillis(value)
        override fun clear() = p.clear()
        override fun get(field: Int): Int = p.get(field)
        override fun set(field: Int, value: Int) = p.set(field, value)
        override fun getActualMaximum(field: Int): Int = p.getActualMaximum(field)
    }
    companion object {
        const val MUHARRAM = 0; const val SAFAR = 1; const val RABI_1 = 2; const val RABI_2 = 3
        const val JUMADA_1 = 4; const val JUMADA_2 = 5; const val RAJAB = 6; const val SHABAN = 7
        const val RAMADAN = 8; const val SHAWWAL = 9; const val DHU_AL_QIDAH = 10; const val DHU_AL_HIJJAH = 11
    }
}
