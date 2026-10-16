// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.CalType
import io.github.heapy.icu4k.impl.CalendarUtil
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Calendar's locale metadata and regional preference policies. */
internal object CalendarLocaleCore {
    fun getRegionForCalendar(locale: ULocale?): String =
        ULocale.getRegionForSupplementalData(locale, true).ifEmpty { "001" }

    fun getCalendarTypeForLocale(locale: ULocale?): CalType? {
        val type = CalendarUtil.getCalendarType(locale).lowercase()
        return CalType.entries.firstOrNull { it.getId() == type }
    }

    fun firstDayOverride(locale: ULocale): Int? = when (locale.getKeywordValue("fw")) {
        "sun" -> 1; "mon" -> 2; "tue" -> 3; "wed" -> 4; "thu" -> 5; "fri" -> 6; "sat" -> 7
        else -> null
    }

    fun calendarLocale(locale: ULocale): ULocale {
        if (locale.getVariant().isEmpty() && locale.getKeywords() == null) return locale
        val name = StringBuilder(locale.getLanguage())
        val script = locale.getScript()
        if (script.isNotEmpty()) name.append('_').append(script)
        val region = locale.getCountry()
        if (region.isNotEmpty()) name.append('_').append(region)
        locale.getKeywordValue("calendar")?.let { name.append("@calendar=").append(it) }
        return ULocale(name.toString())
    }

    @Suppress("UNUSED_PARAMETER")
    fun getKeywordValuesForLocale(key: String?, locale: ULocale?, commonlyUsed: Boolean): Array<String> {
        val region = ULocale.getRegionForSupplementalData(locale, true)
        val preferences = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "supplementalData")["calendarPreferenceData"]
        val order = try { preferences[region] } catch (_: MissingResourceException) { preferences["001"] }
        val types = order.getStringArray()
        if (commonlyUsed) return types
        val values = types.toMutableList()
        for (type in CalType.entries) if (type.getId() !in values) values.add(type.getId())
        return values.toTypedArray()
    }
}
