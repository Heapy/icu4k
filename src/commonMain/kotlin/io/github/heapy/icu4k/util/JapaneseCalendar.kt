// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.CalType
import io.github.heapy.icu4k.impl.calendar.EraRulesCore
import io.github.heapy.icu4k.impl.calendar.JapaneseCalendarCore
import io.github.heapy.icu4k.impl.calendar.japaneseEraConfiguration
import kotlin.time.Clock

/** Gregorian calendar with the era boundaries supplied by the pinned ICU data. */
open class JapaneseCalendar : GregorianCalendar {
    constructor() : super(defaultZone(), ULocale.getDefault(ULocale.Category.FORMAT))
    constructor(zone: TimeZone?) : super(initializedZone(zone))
    constructor(locale: ULocale?) : super(initializedLocale(locale))
    constructor(zone: TimeZone?, locale: ULocale?) : super(initializedZone(zone), locale)
    constructor(date: Date?) : this() { setTime(date) }
    constructor(era: Int, year: Int, month: Int, date: Int) : super(initializedInt(year), month, date) { set(ERA, era) }
    constructor(year: Int, month: Int, date: Int) : super(initializedInt(year), month, date) { set(ERA, CURRENT_ERA) }
    constructor(year: Int, month: Int, date: Int, hour: Int, minute: Int, second: Int) : super(initializedInt(year), month, date, hour, minute, second) { set(ERA, CURRENT_ERA) }
    protected constructor(other: JapaneseCalendar, copyMarker: Unit) : super(other, copyMarker)
    // No derived instance state: Calendar/Gregorian construction may dispatch here.
    private fun japanese() = JapaneseCalendarCore(ERA_RULES, CURRENT_ERA, Binding())
    override fun handleGetExtendedYear(): Int = japanese().handleGetExtendedYear()
    override fun getDefaultMonthInYear(extendedYear: Int): Int = japanese().getDefaultMonthInYear(extendedYear)
    override fun getDefaultDayInMonth(extendedYear: Int, month: Int): Int = japanese().getDefaultDayInMonth(extendedYear, month)
    override fun handleComputeFields(julianDay: Int) = japanese().handleComputeFields(julianDay)
    override fun handleGetLimit(field: Int, limitType: Int): Int = japanese().handleGetLimit(field, limitType)
    override fun getActualMaximum(field: Int): Int = japanese().getActualMaximum(field)
    override fun getType(): String = japanese().getType()
    @Deprecated("This API is ICU internal only.")
    override fun haveDefaultCentury(): Boolean = japanese().haveDefaultCentury()
    @Deprecated("This API is ICU internal only.")
    override fun isEra0CountingBackward(): Boolean = japanese().isEra0CountingBackward()
    override fun cloneNative(): Calendar = JapaneseCalendar(this, Unit)
    private inner class Binding : JapaneseCalendarCore.Owner {
        private val p get() = this@JapaneseCalendar
        override fun newerField(defaultField: Int, alternateField: Int): Int = p.newerField(defaultField, alternateField)
        override fun internalGet(field: Int): Int = p.internalGet(field)
        override fun internalGet(field: Int, defaultValue: Int): Int = p.internalGet(field, defaultValue)
        override fun internalSet(field: Int, value: Int) = p.internalSet(field, value)
        override fun gregorianComputeFields(julianDay: Int) = super@JapaneseCalendar.handleComputeFields(julianDay)
        override fun baseDefaultMonth(year: Int): Int = super@JapaneseCalendar.getDefaultMonthInYear(year)
        override fun baseDefaultDay(year: Int, month: Int): Int = super@JapaneseCalendar.getDefaultDayInMonth(year, month)
        override fun baseGetLimit(field: Int, type: Int): Int = super@JapaneseCalendar.handleGetLimit(field, type)
        override fun baseGetActualMaximum(field: Int): Int = super@JapaneseCalendar.getActualMaximum(field)
        override fun get(field: Int): Int = p.get(field)
        override fun handleGetLimit(field: Int, type: Int): Int = p.handleGetLimit(field, type)
    }
    companion object {
        private val ERA_RULES = EraRulesCore.getInstance(CalType.JAPANESE, JapaneseCalendarCore.enableTentativeEra(japaneseEraConfiguration()), object : EraRulesCore.Owner {
            override fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
            override fun defaultZoneOffset(time: Long): Int = TimeZone.getDefault().getOffset(time)
        })
        // Runtime fields, not const vals: ICU intentionally prevents client inlining.
        @kotlin.jvm.JvmField val MEIJI = initializedInt(232)
        @kotlin.jvm.JvmField val TAISHO = initializedInt(233)
        @kotlin.jvm.JvmField val SHOWA = initializedInt(234)
        @kotlin.jvm.JvmField val HEISEI = initializedInt(235)
        @kotlin.jvm.JvmField val REIWA = initializedInt(236)
        @kotlin.jvm.JvmField val CURRENT_ERA = ERA_RULES.getCurrentEraCode()
        // Accessing these helpers initializes the companion before Gregorian construction
        // on platforms without JVM class initialization. Integer calls also avoid ConstantValue attributes.
        private fun initializedInt(value: Int): Int = value
        private fun initializedZone(value: TimeZone?): TimeZone? = value
        private fun initializedLocale(value: ULocale?): ULocale? = value
        private fun defaultZone(): TimeZone = TimeZone.getDefault()
        @Deprecated("This API is ICU internal only.")
        fun enableTentativeEra(): Boolean = JapaneseCalendarCore.enableTentativeEra(japaneseEraConfiguration())
    }
}
