// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.calendar.CalendarPatternDataCore
import io.github.heapy.icu4k.text.DateFormat
import io.github.heapy.icu4k.util.*

/** Source's single-entry default-pattern cache, including cache updates before failures. */
internal class SimpleDateFormatDefaultPatternCore(
    private val createCalendar: (ULocale) -> Calendar = { Calendar.getInstance(it) },
    private val patterns: CalendarPatternDataCore = CalendarPatternDataCore.withCommonGenerator(),
    private val loader: ICUResourceLoader = defaultICUResourceLoader(),
    private val atTimePattern: (Calendar, ULocale, Int) -> String? = { cal, locale, style -> patterns.make(cal.getType(),locale).getDateAtTimePattern(style) },
) {
    private var cachedLocale: ULocale? = null
    private var cachedPattern: String? = null
    private val lock = createAvailabilityLock()
    fun getPattern(): String? = lock.withLock { resolve(ULocale.getDefault(ULocale.Category.FORMAT)) }
    fun getPattern(locale: ULocale): String? = lock.withLock { resolve(locale) }
    private fun resolve(locale: ULocale): String? {
        if (locale != cachedLocale) {
            cachedLocale = locale
            // Calendar construction is deliberately outside the missing-resource catch.
            val cal = createCalendar(cachedLocale!!)
            try {
                val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,cachedLocale!!,loader) as ICUResourceBundle
                val resource = bundle.findWithFallback("calendar/${cal.getType()}/DateTimePatterns")
                    ?: bundle.findWithFallback("calendar/gregorian/DateTimePatterns")
                cachedPattern = if (resource == null || resource.getSize() < 9) FALLBACK else {
                    val glue = atTimePattern(cal,cachedLocale!!,DateFormat.SHORT)
                    // Do not normalize ARRAY entries: source getString throws on them.
                    SimpleFormatterImpl.formatRawPattern(glue!!,2,2,resource.getString(DateFormat.SHORT),resource.getString(DateFormat.SHORT + 4))
                }
            } catch (_: MissingResourceException) { cachedPattern = FALLBACK }
        }
        return cachedPattern
    }
    companion object { private const val FALLBACK = "yy/MM/dd HH:mm" }
}
