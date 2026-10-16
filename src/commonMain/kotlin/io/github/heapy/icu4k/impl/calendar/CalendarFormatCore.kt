// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.SimpleFormatterImpl
import io.github.heapy.icu4k.util.ULocale

/** Calendar's style resolution and format construction order, with real formatter factories required. */
internal class CalendarFormatCore<C: Any, S: Any, F: Any>(private val owner: Owner<C, S, F>, private val patterns: CalendarPatternDataCore) {
    interface Owner<C: Any, S: Any, F: Any> {
        fun getType(calendar: C): String?
        fun handleGetDateFormat(calendar: C, pattern: String?, override: String?, locale: ULocale?): F?
        fun setCalendar(format: F, calendar: C)
        fun createDateFormatSymbols(calendar: C?, locale: ULocale?): S?
        fun createSimpleDateFormat(configuration: FormatConfiguration<C, S>): F?
    }
    class FormatConfiguration<C: Any, S: Any>(
        private val pattern: String?, private val override: String?, private val formatData: S?,
        private val calendar: C?, private val locale: ULocale?,
    ) {
        fun getPatternString(): String? = pattern
        fun getOverrideString(): String? = override
        fun getDateFormatSymbols(): S? = formatData
        fun getCalendar(): C? = calendar
        fun getLocale(): ULocale? = locale
    }
    fun getDateTimePattern(calendar: C?, locale: ULocale?, dateStyle: Int): String? = patterns.make(owner.getType(calendar!!), locale).getDateTimePattern(dateStyle)
    fun getDateAtTimePattern(calendar: C?, locale: ULocale?, dateStyle: Int): String? = patterns.make(owner.getType(calendar!!), locale).getDateAtTimePattern(dateStyle)
    fun getDateTimeFormat(calendar: C?, locale: ULocale?, dateStyle: Int, timeStyle: Int): F {
        if (timeStyle < -1 || timeStyle > 3) throw IllegalArgumentException("Illegal time style $timeStyle")
        if (dateStyle < -1 || dateStyle > 3) throw IllegalArgumentException("Illegal date style $dateStyle")
        val cal = calendar!!
        val data = patterns.make(owner.getType(cal), locale)
        var override: String? = null
        val pattern: String?
        if (timeStyle >= 0 && dateStyle >= 0) {
            val glue = data.getDateAtTimePattern(dateStyle)
            val time = data.pattern(timeStyle)
            val date = data.pattern(dateStyle + 4)
            pattern = SimpleFormatterImpl.formatRawPattern(glue!!, 2, 2, time, date)
            if (data.overrides != null) {
                val dateOverride = data.overrideAt(dateStyle + 4)
                val timeOverride = data.overrideAt(timeStyle)
                override = CalendarPatternDataCore.mergeOverrideStrings(data.pattern(dateStyle + 4), data.pattern(timeStyle), dateOverride, timeOverride)
            }
        } else if (timeStyle >= 0) {
            pattern = data.pattern(timeStyle)
            if (data.overrides != null) override = data.overrideAt(timeStyle)
        } else if (dateStyle >= 0) {
            pattern = data.pattern(dateStyle + 4)
            if (data.overrides != null) override = data.overrideAt(dateStyle + 4)
        } else throw IllegalArgumentException("No date or time style specified")
        val result = owner.handleGetDateFormat(cal, pattern, override, locale)
        owner.setCalendar(result!!, cal)
        return result
    }
    fun handleGetDateFormat(calendar: C?, pattern: String?, override: String?, locale: ULocale?): F? {
        val symbols = owner.createDateFormatSymbols(calendar, locale)
        return owner.createSimpleDateFormat(FormatConfiguration(pattern, override, symbols, calendar, locale))
    }
}
