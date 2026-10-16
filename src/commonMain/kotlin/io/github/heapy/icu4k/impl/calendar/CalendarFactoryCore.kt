// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.CalType
import io.github.heapy.icu4k.util.ULocale

/** Calendar's construction order; actual subclasses and timezone services are mandatory owner boundaries. */
internal class CalendarFactoryCore<C : Any, Z : Any>(private val owner: Owner<C, Z>) {
    interface Owner<C : Any, Z : Any> {
        fun defaultFormatLocale(): ULocale
        fun forULocaleOrDefault(locale: ULocale): Z
        /** Construct the requested source subclass; ISO8601 uses Gregorian and all Islamic types use Islamic. */
        fun construct(type: CalType, zone: Z, locale: ULocale): C
        fun setAmeteAlemEra(calendar: C, value: Boolean)
        fun setFirstDayOfWeek(calendar: C, day: Int)
        fun setMinimalDaysInFirstWeek(calendar: C, days: Int)
        fun setTimeZone(calendar: C, zone: Z)
        fun currentTimeMillis(): Long
        fun setTimeInMillis(calendar: C, time: Long)
    }
    fun getInstance(zone: Z?, locale: ULocale?): C {
        val loc = locale ?: owner.defaultFormatLocale()
        val tz = zone ?: owner.forULocaleOrDefault(loc)
        val calendar = createInstance(loc)
        owner.setTimeZone(calendar, tz)
        owner.setTimeInMillis(calendar, owner.currentTimeMillis())
        return calendar
    }
    fun createInstance(locale: ULocale?): C {
        val loc = locale!!
        val zone = owner.forULocaleOrDefault(loc)
        val type = CalendarLocaleCore.getCalendarTypeForLocale(loc) ?: CalType.GREGORIAN
        val constructorType = when (type) {
            CalType.ISO8601 -> CalType.GREGORIAN
            CalType.ETHIOPIC_AMETE_ALEM -> CalType.ETHIOPIC
            CalType.ISLAMIC_CIVIL, CalType.ISLAMIC_UMALQURA, CalType.ISLAMIC_TBLA, CalType.ISLAMIC_RGSA -> CalType.ISLAMIC
            else -> type
        }
        val calendar = owner.construct(constructorType, zone, loc)
        if (type == CalType.ISO8601) {
            if (loc.getUnicodeLocaleType("fw") == null && loc.getUnicodeLocaleType("rg") == null) owner.setFirstDayOfWeek(calendar, 2)
            owner.setMinimalDaysInFirstWeek(calendar, 4)
        } else if (type == CalType.ETHIOPIC_AMETE_ALEM) owner.setAmeteAlemEra(calendar, true)
        return calendar
    }
}
