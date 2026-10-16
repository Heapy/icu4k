// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009,2016 International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Calendar keyword, legacy-variant canonicalization and regional default selection. */
class CalendarUtil private constructor() {
    companion object {
        @kotlin.jvm.JvmStatic
        fun getCalendarType(locale: ULocale?): String {
            val loc = locale!!
            loc.getKeywordValue("calendar")?.let { return it.lowercase() }
            val canonical = ULocale.createCanonical(loc.toString())
            canonical.getKeywordValue("calendar")?.let { return it }
            val region = ULocale.getRegionForSupplementalData(canonical, true)
            return CalendarPreferences.prefs[region] ?: "gregorian"
        }
    }
    private object CalendarPreferences : UResource.Sink() {
        val prefs = HashMap<String, String>()
        init {
            try {
                val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "supplementalData") as ICUResourceBundle
                bundle.getAllItemsWithFallback("calendarPreferenceData", this)
            } catch (_: MissingResourceException) { }
        }
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val table = value.getTable()
            var index = 0
            while (table.getKeyAndValue(index++, key, value)) {
                val types = value.getArray()
                if (types.getValue(0, value)) {
                    val type = value.getString()
                    if (type != "gregorian") prefs[key.toString()] = type
                }
            }
        }
    }
}
