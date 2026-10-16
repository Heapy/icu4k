// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Calendar's date/time resource selection; pattern generation is a mandatory dependency. */
internal class CalendarPatternDataCore(
    private val generatorFactory: (ULocale)->Generator,
    private val cache: ICUCache<String, PatternData> = PATTERN_CACHE,
    private val resourceLoader: ICUResourceLoader = defaultICUResourceLoader(),
) {
    interface Generator { fun getBestPattern(skeleton: String): String? }
    class PatternData(val patterns: Array<String?>?, val overrides: Array<String?>?, val atTimePatterns: Array<String?>?) {
        fun pattern(index: Int): String? = element(patterns, index)
        fun overrideAt(index: Int): String? = element(overrides, index)
        fun getDateTimePattern(dateStyle: Int): String? {
            var index = 8
            if (patterns!!.size >= 13) index += dateStyle + 1
            return pattern(index)
        }
        fun getDateAtTimePattern(dateStyle: Int): String? = if (atTimePatterns != null && atTimePatterns.size >= 4) element(atTimePatterns, dateStyle) else getDateTimePattern(dateStyle)
        private fun element(values: Array<String?>?, index: Int): String? {
            val array = values!!
            if (index !in array.indices) throw IndexOutOfBoundsException()
            return array[index]
        }
    }
    fun make(calendarType: String?, locale: ULocale?): PatternData {
        val loc = locale!!
        val key = loc.getBaseName() + "+" + calendarType
        val hourKeywords = loc.getKeywordValue("rg") != null || loc.getKeywordValue("hours") != null
        var data = if (!hourKeywords) cache.get(key) else null
        if (data == null) {
            data = try { getPatternData(loc, calendarType) }
            catch (_: MissingResourceException) { PatternData(DEFAULT_PATTERNS, null, DEFAULT_ATTIME_PATTERNS) }
            if (!hourKeywords) cache.put(key, data)
        }
        return data
    }
    fun getPatternData(locale: ULocale, calendarType: String?): PatternData {
        val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale, resourceLoader) as ICUResourceBundle
        var resource = bundle.findWithFallback("calendar/$calendarType/DateTimePatterns")
            ?: bundle.getWithFallback("calendar/gregorian/DateTimePatterns")
        var size = resource.getSize()
        val patterns = arrayOfNulls<String>(size)
        val overrides = arrayOfNulls<String>(size)
        var index = 0
        var useGenerator = locale.getKeywordValue("rg") != null || locale.getKeywordValue("hours") != null
        if (!useGenerator) {
            val baseID = locale.getBaseName()
            if (baseID.isNotEmpty() && baseID != "und") {
                val base = ULocale(baseID)
                val valid = ULocale.addLikelySubtags(resource.getULocale())
                if (valid !== base) {
                    val region = base.getCountry()
                    if ((region.isNotEmpty() && region != valid.getCountry()) || base.getLanguage() != valid.getLanguage()) useGenerator = true
                }
            }
        }
        if (useGenerator) {
            val generator = generatorFactory(locale)
            for (skeleton in TIME_SKELETONS) {
                val value = generator.getBestPattern(skeleton)
                // Java evaluates the generator before the array store bounds check.
                if (index !in patterns.indices) throw IndexOutOfBoundsException()
                patterns[index++] = value
            }
        }
        while (index < size) {
            val entry = resource[index] as ICUResourceBundle
            when (entry.getType()) {
                UResourceBundle.STRING -> patterns[index] = entry.getString()
                UResourceBundle.ARRAY -> { patterns[index] = entry.getString(0); overrides[index] = entry.getString(1) }
            }
            index++
        }
        val atResource = bundle.findWithFallback("calendar/$calendarType/DateTimePatterns%atTime")
            ?: bundle.findWithFallback("calendar/gregorian/DateTimePatterns%atTime")
        var atTime: Array<String?>? = null
        if (atResource != null) {
            size = atResource.getSize()
            atTime = arrayOfNulls(size)
            if (size >= 4) for (i in 0..3) {
                val entry = atResource[i] as ICUResourceBundle
                if (entry.getType() == UResourceBundle.STRING) atTime[i] = entry.getString()
            }
        }
        return PatternData(patterns, overrides, atTime)
    }
    companion object {
        /** Bind Calendar's regenerated hour patterns to the actual common no-standard generator. */
        fun withCommonGenerator(
            resourceLoader: ICUResourceLoader=defaultICUResourceLoader(),
            cache: ICUCache<String,PatternData> = PATTERN_CACHE,
        ): CalendarPatternDataCore {
            val initialization by lazy { DateTimePatternInitializationCore(resourceLoader) }
            return CalendarPatternDataCore({ locale ->
                val generator=initialization.noStandardPatterns(locale)
                object: Generator { override fun getBestPattern(skeleton: String): String=generator.getBestPattern(skeleton) }
            },cache,resourceLoader)
        }
        private val PATTERN_CACHE = SimpleCache<String, PatternData>()
        private val DEFAULT_PATTERNS = arrayOf<String?>(
            "HH:mm:ss z","HH:mm:ss z","HH:mm:ss","HH:mm",
            "y MMMM d, EEEE","y MMMM d","y MMM d","y-MM-dd",
            "{1} {0}","{1} {0}","{1} {0}","{1} {0}","{1} {0}",
        )
        private val DEFAULT_ATTIME_PATTERNS = arrayOf<String?>("{1} {0}","{1} {0}","{1} {0}","{1} {0}")
        private val TIME_SKELETONS = arrayOf("jmmsszzzz","jmmssz","jmmss","jmm")
        fun mergeOverrideStrings(datePattern: String?, timePattern: String?, dateOverride: String?, timeOverride: String?): String? {
            if (dateOverride == null && timeOverride == null) return null
            if (dateOverride == null) return expandOverride(timePattern, timeOverride)
            if (timeOverride == null) return expandOverride(datePattern, dateOverride)
            if (dateOverride == timeOverride) return dateOverride
            return expandOverride(datePattern, dateOverride) + ";" + expandOverride(timePattern, timeOverride)
        }
        fun expandOverride(pattern: String?, override: String?): String {
            val value = override!!
            if ('=' in value) return value
            val text = pattern!!
            var quoted = false
            var previous = ' '
            val result = StringBuilder()
            for (char in text) {
                // StringCharacterIterator.DONE also terminates on an actual U+FFFF.
                if (char == '\uffff') break
                if (char == '\'') { quoted = !quoted; previous = char; continue }
                if (!quoted && char != previous) {
                    if (result.isNotEmpty()) result.append(';')
                    result.append(char).append('=').append(value)
                }
                previous = char
            }
            return result.toString()
        }
    }
}
