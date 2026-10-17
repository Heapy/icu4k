// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.DateFormat
import io.github.heapy.icu4k.util.Calendar

/** Source SimpleDateFormat's lazy pattern compiler, separate from its flag scan. */
internal object SimpleDateFormatPatternCore {
    class PatternItem(val type: Char, val length: Int) {
        val isNumeric: Boolean = isNumeric(type, length)
    }
    data class Flags(val hasMinute: Boolean, val hasSecond: Boolean, val hasHanYearChar: Boolean)
    private val cache = SimpleCache<String, Array<Any>>()

    fun isSyntaxChar(ch: Char): Boolean = ch in 'A'..'Z' || ch in 'a'..'z'
    fun isNumeric(ch: Char, count: Int): Boolean = ch in "ADdFgHhKkmrSsuWwYy" || (count <= 2 && ch in "ceLMQq")

    fun scan(pattern: String): Flags {
        var minute = false
        var second = false
        var hanYear = false
        var inQuote = false
        for (ch in pattern) {
            if (ch == '\'') inQuote = !inQuote
            if (ch == '年') hanYear = true
            if (!inQuote) {
                if (ch == 'm') minute = true
                if (ch == 's') second = true
            }
        }
        return Flags(minute, second, hanYear)
    }

    // Items and arrays are shared between formatter instances, as in ICU4J.
    fun compile(pattern: String): Array<Any> {
        cache.get(pattern)?.let { return it }
        var isPrevQuote = false
        var inQuote = false
        val text = StringBuilder()
        var itemType = '\u0000'
        var itemLength = 1
        val items = ArrayList<Any>()
        for (ch in pattern) {
            if (ch == '\'') {
                if (isPrevQuote) {
                    text.append('\'')
                    isPrevQuote = false
                } else {
                    isPrevQuote = true
                    if (itemType != '\u0000') {
                        items.add(PatternItem(itemType, itemLength))
                        itemType = '\u0000'
                    }
                }
                inQuote = !inQuote
            } else {
                isPrevQuote = false
                if (inQuote) text.append(ch)
                else if (isSyntaxChar(ch)) {
                    if (ch == itemType) itemLength++
                    else {
                        if (itemType == '\u0000') {
                            if (text.isNotEmpty()) {
                                items.add(text.toString())
                                text.setLength(0)
                            }
                        } else items.add(PatternItem(itemType, itemLength))
                        itemType = ch
                        itemLength = 1
                    }
                } else {
                    if (itemType != '\u0000') {
                        items.add(PatternItem(itemType, itemLength))
                        itemType = '\u0000'
                    }
                    text.append(ch)
                }
            }
        }
        if (itemType == '\u0000') {
            if (text.isNotEmpty()) items.add(text.toString())
        } else items.add(PatternItem(itemType, itemLength))
        return items.toTypedArray().also { cache.put(pattern, it) }
    }

    fun index(ch: Char): Int = if (ch.code < patternCharToIndex.size) patternCharToIndex[ch.code] else -1
    fun calendarField(index: Int): Int = patternIndexToCalendarField[index]
    fun dateFormatField(index: Int): Int = patternIndexToDateFormatField[index]
    fun attribute(ch: Char): DateFormat.Field? = index(ch).let { if (it == -1) null else patternIndexToDateFormatAttribute[it] }

    fun translate(pattern: String, from: String, to: String): String {
        val result = StringBuilder()
        var inQuote = false
        for (original in pattern) {
            var ch = original
            if (inQuote) {
                if (ch == '\'') inQuote = false
            } else if (ch == '\'') inQuote = true
            else if (isSyntaxChar(ch)) {
                val index = from.indexOf(ch)
                if (index != -1) ch = to[index]
            }
            result.append(ch)
        }
        if (inQuote) throw IllegalArgumentException("Unfinished quote in pattern")
        return result.toString()
    }

    fun level(ch: Char): Int = if (ch.code < patternCharToLevel.size) patternCharToLevel[ch.code] else -1

    fun isFieldUnitIgnored(pattern: String, field: Int): Boolean {
        val fieldLevel = calendarFieldToLevel[field]
        var inQuote = false
        var prevCh = '\u0000'
        var count = 0
        var i = 0
        while (i < pattern.length) {
            val ch = pattern[i]
            if (ch != prevCh && count > 0) {
                if (fieldLevel <= level(prevCh)) return false
                count = 0
            }
            if (ch == '\'') {
                if (i + 1 < pattern.length && pattern[i + 1] == '\'') i++
                else inQuote = !inQuote
            } else if (!inQuote && isSyntaxChar(ch)) {
                prevCh = ch
                count++
            }
            i++
        }
        if (count > 0 && fieldLevel <= level(prevCh)) return false
        return true
    }

    private val patternCharToIndex = intArrayOf(
        -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, 22,
        36, -1, 10, 9, 11, 0,
        5, -1, -1, 16, 26, 2,
        -1, 31, -1, 27, -1, 8,
        -1, 30, 29, 13, 32, 18,
        23, -1, -1, -1, -1, -1,
        -1, 14, 35, 25, 3, 19,
        -1, 21, 15, -1, -1, 4,
        -1, 6, -1, -1, -1, 28,
        34, 7, -1, 20, 24, 12,
        33, 1, 17, -1, -1, -1,
        -1, -1,
    )

    private val patternIndexToCalendarField = intArrayOf(
        Calendar.ERA, Calendar.YEAR, Calendar.MONTH, Calendar.DATE, Calendar.HOUR_OF_DAY, Calendar.HOUR_OF_DAY,
        Calendar.MINUTE, Calendar.SECOND, Calendar.MILLISECOND, Calendar.DAY_OF_WEEK, Calendar.DAY_OF_YEAR, Calendar.DAY_OF_WEEK_IN_MONTH,
        Calendar.WEEK_OF_YEAR, Calendar.WEEK_OF_MONTH, Calendar.AM_PM, Calendar.HOUR, Calendar.HOUR, Calendar.ZONE_OFFSET,
        Calendar.YEAR_WOY, Calendar.DOW_LOCAL, Calendar.EXTENDED_YEAR, Calendar.JULIAN_DAY, Calendar.MILLISECONDS_IN_DAY, Calendar.ZONE_OFFSET,
        Calendar.ZONE_OFFSET, Calendar.DOW_LOCAL, Calendar.MONTH, Calendar.MONTH, Calendar.MONTH, Calendar.ZONE_OFFSET,
        Calendar.YEAR, Calendar.ZONE_OFFSET, Calendar.ZONE_OFFSET, Calendar.ZONE_OFFSET, Calendar.EXTENDED_YEAR, -1,
        // ICU4J omits a comma: B maps to the expression -1 - 1, not -1.
        -2,
    )

    private val patternIndexToDateFormatField = intArrayOf(
        DateFormat.ERA_FIELD, DateFormat.YEAR_FIELD, DateFormat.MONTH_FIELD, DateFormat.DATE_FIELD, DateFormat.HOUR_OF_DAY1_FIELD, DateFormat.HOUR_OF_DAY0_FIELD,
        DateFormat.MINUTE_FIELD, DateFormat.SECOND_FIELD, DateFormat.FRACTIONAL_SECOND_FIELD, DateFormat.DAY_OF_WEEK_FIELD, DateFormat.DAY_OF_YEAR_FIELD, DateFormat.DAY_OF_WEEK_IN_MONTH_FIELD,
        DateFormat.WEEK_OF_YEAR_FIELD, DateFormat.WEEK_OF_MONTH_FIELD, DateFormat.AM_PM_FIELD, DateFormat.HOUR1_FIELD, DateFormat.HOUR0_FIELD, DateFormat.TIMEZONE_FIELD,
        DateFormat.YEAR_WOY_FIELD, DateFormat.DOW_LOCAL_FIELD, DateFormat.EXTENDED_YEAR_FIELD, DateFormat.JULIAN_DAY_FIELD, DateFormat.MILLISECONDS_IN_DAY_FIELD, DateFormat.TIMEZONE_RFC_FIELD,
        DateFormat.TIMEZONE_GENERIC_FIELD, DateFormat.STANDALONE_DAY_FIELD, DateFormat.STANDALONE_MONTH_FIELD, DateFormat.QUARTER_FIELD, DateFormat.STANDALONE_QUARTER_FIELD, DateFormat.TIMEZONE_SPECIAL_FIELD,
        DateFormat.YEAR_NAME_FIELD, DateFormat.TIMEZONE_LOCALIZED_GMT_OFFSET_FIELD, DateFormat.TIMEZONE_ISO_FIELD, DateFormat.TIMEZONE_ISO_LOCAL_FIELD, DateFormat.RELATED_YEAR, DateFormat.AM_PM_MIDNIGHT_NOON_FIELD,
        DateFormat.FLEXIBLE_DAY_PERIOD_FIELD, DateFormat.TIME_SEPARATOR,
    )

    private val patternIndexToDateFormatAttribute = arrayOf(
        DateFormat.Field.ERA, DateFormat.Field.YEAR, DateFormat.Field.MONTH, DateFormat.Field.DAY_OF_MONTH, DateFormat.Field.HOUR_OF_DAY1, DateFormat.Field.HOUR_OF_DAY0,
        DateFormat.Field.MINUTE, DateFormat.Field.SECOND, DateFormat.Field.MILLISECOND, DateFormat.Field.DAY_OF_WEEK, DateFormat.Field.DAY_OF_YEAR, DateFormat.Field.DAY_OF_WEEK_IN_MONTH,
        DateFormat.Field.WEEK_OF_YEAR, DateFormat.Field.WEEK_OF_MONTH, DateFormat.Field.AM_PM, DateFormat.Field.HOUR1, DateFormat.Field.HOUR0, DateFormat.Field.TIME_ZONE,
        DateFormat.Field.YEAR_WOY, DateFormat.Field.DOW_LOCAL, DateFormat.Field.EXTENDED_YEAR, DateFormat.Field.JULIAN_DAY, DateFormat.Field.MILLISECONDS_IN_DAY, DateFormat.Field.TIME_ZONE,
        DateFormat.Field.TIME_ZONE, DateFormat.Field.DAY_OF_WEEK, DateFormat.Field.MONTH, DateFormat.Field.QUARTER, DateFormat.Field.QUARTER, DateFormat.Field.TIME_ZONE,
        DateFormat.Field.YEAR, DateFormat.Field.TIME_ZONE, DateFormat.Field.TIME_ZONE, DateFormat.Field.TIME_ZONE, DateFormat.Field.RELATED_YEAR, DateFormat.Field.AM_PM_MIDNIGHT_NOON,
        DateFormat.Field.FLEXIBLE_DAY_PERIOD, DateFormat.Field.TIME_SEPARATOR,
    )

    private val calendarFieldToLevel = intArrayOf(
        0, 10, 20, 20, 30, 30, 20, 30, 30, 40, 50, 50,
        60, 70, 80, 0, 0, 10, 30, 10, 0, 40, 0, 0,
    )

    private val patternCharToLevel = intArrayOf(
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, 40, -1, -1, 20, 30, 30, 0,
        50, -1, -1, 50, 20, 20, -1, 0, -1, 20, -1, 80,
        -1, 10, 0, 30, 0, 10, 0, -1, -1, -1, -1, -1,
        -1, 40, -1, 30, 30, 30, -1, 0, 50, -1, -1, 50,
        -1, 60, -1, -1, -1, 20, 10, 70, -1, 10, 0, 20,
        0, 10, 0, -1, -1, -1, -1, -1,
    )
}
