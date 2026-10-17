// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.text.DateFormat.BooleanAttribute
import io.github.heapy.icu4k.text.TimeZoneFormat.Style
import io.github.heapy.icu4k.text.TimeZoneFormat.TimeType
import io.github.heapy.icu4k.util.*

/** Source field parser, including its shared delayed Hebrew-month correction. */
internal class SimpleDateFormatFieldParseCore(private val owner: Owner) {
    interface Owner {
        val symbols: DateFormatSymbols?
        val numberingOverride: String?
        fun numberFormat(ch: Char): NumberFormat?
        fun timeZoneFormat(): TimeZoneFormat
        fun booleanAttribute(key: BooleanAttribute): Boolean
        fun isLenient(): Boolean
        fun defaultCenturyStartYear(): Int
        fun subParse(text: String, start: Int, ch: Char, count: Int, obeyCount: Boolean,
                     allowNegative: Boolean, ambiguousYear: BooleanArray, cal: Calendar,
                     numericLeapMonth: SimpleDateFormatParseCore.NumericLeapMonth?, timeType: Output<TimeType>?,
                     dayPeriod: Output<DayPeriodRules.DayPeriod>?): Int
        fun matchQuarterString(text: String, start: Int, field: Int, data: Array<String?>, cal: Calendar): Int
    }
    private fun data(key: String): Array<String?>? = owner.symbols!!.state.data.fields[key] as Array<String?>?
    private fun attribute(key: BooleanAttribute): Boolean = owner.booleanAttribute(key)
    private fun multiple(): Boolean = attribute(BooleanAttribute.PARSE_MULTIPLE_PATTERNS_FOR_MATCH)
    private fun numeric(): Boolean = attribute(BooleanAttribute.PARSE_ALLOW_NUMERIC)

    fun subParse(text: String, initial: Int, ch: Char, count: Int, obeyCount: Boolean,
                 allowNegative: Boolean, ambiguousYear: BooleanArray, cal: Calendar,
                 leap: SimpleDateFormatParseCore.NumericLeapMonth?, timeType: Output<TimeType>?,
                 dayPeriod: Output<DayPeriodRules.DayPeriod>?): Int {
        var start = initial
        var number: Number? = null
        var value = 0
        val pos = ParsePosition(0)
        val index = SimpleDateFormatPatternCore.index(ch)
        if (index == -1) return start.inv()
        val nf = owner.numberFormat(ch)
        val field = SimpleDateFormatPatternCore.calendarField(index)
        leap?.setNumberFormat(nf)
        val chinese = cal.getType() == "chinese" || cal.getType() == "dangi"
        while (true) {
            if (start >= text.length) return start.inv()
            val cp = UTF16.charAt(text, start)
            if (!SimpleDateFormatParseCore.whitespace(cp)) break
            start += UTF16.getCharCount(cp)
        }
        pos.index = start
        if (index == 4 || index == 15 || (index == 2 && count <= 2) || index in intArrayOf(26, 19, 25, 1, 18, 30, 27, 28, 8) || (index == 0 && chinese)) {
            var parsedLeap = false
            if (leap != null && (index == 2 || index == 26)) {
                val args = leap.parse(text, pos)
                if (args != null && pos.index > start && args.isEmpty()) throw IndexOutOfBoundsException()
                if (args != null && pos.index > start && args[0] is Number) {
                    parsedLeap = true
                    number = args[0] as Number
                    cal.set(Calendar.IS_LEAP_MONTH, 1)
                } else {
                    pos.index = start
                    cal.set(Calendar.IS_LEAP_MONTH, 0)
                }
            }
            if (!parsedLeap) {
                if (obeyCount && start + count > text.length) return start.inv()
                number = parseInt(text, if (obeyCount) count else -1, pos, allowNegative, nf)
                if (number == null && index !in intArrayOf(26, 19, 25, 30, 27, 28)) return start.inv()
            }
            if (number != null) value = number.toInt()
        }
        when (index) {
            0 -> {
                if (chinese) { cal.set(Calendar.ERA, value); return pos.index }
                val eras = data(if (count == 5) "narrowEras" else if (count == 4) "eraNames" else "eras")!!
                val matched = matchString(text, start, Calendar.ERA, eras, null, cal)
                return if (matched == start.inv()) SimpleDateFormatParseCore.ISO_SPECIAL_ERA else matched
            }
            1, 18 -> {
                val override = owner.numberingOverride
                if (override != null && (override == "hebr" || override.contains("y=hebr")) && value < 1000) value += 5000
                else if (count == 2 && countDigits(text, start, pos.index) == 2 && cal.haveDefaultCentury()) {
                    val ambiguous = owner.defaultCenturyStartYear() % 100
                    if (ambiguousYear.isEmpty()) throw IndexOutOfBoundsException()
                    ambiguousYear[0] = value == ambiguous
                    value += owner.defaultCenturyStartYear() / 100 * 100 + if (value < ambiguous) 100 else 0
                }
                cal.set(field, value)
                if (delayedHebrewMonthCheck) {
                    if (!HebrewCalendar.isLeapYear(value)) cal.add(Calendar.MONTH, 1)
                    delayedHebrewMonthCheck = false
                }
                return pos.index
            }
            30 -> {
                val names = data("shortYearNames")
                if (names != null) {
                    val matched = matchString(text, start, Calendar.YEAR, names, null, cal)
                    if (matched > 0) return matched
                }
                if (number != null && (numeric() || names == null || value > names.size)) {
                    cal.set(Calendar.YEAR, value); return pos.index
                }
                return start.inv()
            }
            2, 26 -> {
                if (count <= 2 || (number != null && numeric())) {
                    cal.set(Calendar.MONTH, value - 1)
                    if (cal.getType() == "hebrew" && value >= 6) {
                        if (cal.isSet(Calendar.YEAR)) {
                            if (!HebrewCalendar.isLeapYear(cal.get(Calendar.YEAR))) cal.set(Calendar.MONTH, value)
                        } else delayedHebrewMonthCheck = true
                    }
                    return pos.index
                }
                val leapPatterns = data("leapMonthPatterns")
                val haveLeap = leapPatterns != null && leapPatterns.size >= DateFormatSymbols.DT_MONTH_PATTERN_COUNT
                val wide = data(if (index == 2) "months" else "standaloneMonths")
                val short = data(if (index == 2) "shortMonths" else "standaloneShortMonths")
                var matched = 0
                if (multiple() && count in 3..4 && !haveLeap) {
                    matched = matchAlphaMonthStrings(text, start, wide!!, short!!, cal)
                    if (matched > 0) return matched
                }
                if (multiple() || count == 4) {
                    matched = matchString(text, start, Calendar.MONTH, wide!!,
                        if (haveLeap) leapPatterns!![if (index == 2) 0 else 3] else null, cal)
                    if (matched > 0) return matched
                }
                if (multiple() || count == 3) return matchString(text, start, Calendar.MONTH, short!!,
                    if (haveLeap) leapPatterns!![if (index == 2) 1 else 4] else null, cal)
                return matched
            }
            4 -> { if (value == cal.getMaximum(Calendar.HOUR_OF_DAY) + 1) value = 0; cal.set(Calendar.HOUR_OF_DAY, value); return pos.index }
            8 -> {
                var digits = countDigits(text, start, pos.index)
                if (digits < 3) while (digits < 3) { value *= 10; digits++ }
                else {
                    var divisor = 1
                    while (digits > 3) { divisor *= 10; digits-- }
                    value /= divisor
                }
                cal.set(Calendar.MILLISECOND, value); return pos.index
            }
            19, 9 -> {
                if (index == 19 && (count <= 2 || (number != null && numeric()))) { cal.set(field, value); return pos.index }
                var matched = 0
                for ((width, key) in listOf(4 to "weekdays", 3 to "shortWeekdays", 6 to "shorterWeekdays", 5 to "narrowWeekdays")) {
                    if (multiple() || count == width) {
                        val names = data(key)
                        if (names != null || width == 4 || width == 3) {
                            matched = matchString(text, start, Calendar.DAY_OF_WEEK, names!!, null, cal)
                            if (matched > 0) return matched
                        }
                    }
                }
                return matched
            }
            25 -> {
                if (count == 1 || (number != null && numeric())) { cal.set(field, value); return pos.index }
                var matched = 0
                if (multiple() || count == 4) {
                    matched = matchString(text, start, Calendar.DAY_OF_WEEK, data("standaloneWeekdays")!!, null, cal)
                    if (matched > 0) return matched
                }
                if (multiple() || count == 3) {
                    matched = matchString(text, start, Calendar.DAY_OF_WEEK, data("standaloneShortWeekdays")!!, null, cal)
                    if (matched > 0) return matched
                }
                if (multiple() || count == 6) data("standaloneShorterWeekdays")?.let {
                    return matchString(text, start, Calendar.DAY_OF_WEEK, it, null, cal)
                }
                return matched
            }
            14 -> {
                val narrow = data("ampmsNarrow")
                if (narrow == null || count < 5 || multiple()) {
                    val matched = matchString(text, start, Calendar.AM_PM, data("ampms")!!, null, cal)
                    if (matched > 0) return matched
                }
                if (narrow != null && (count >= 5 || multiple())) {
                    val matched = matchString(text, start, Calendar.AM_PM, narrow, null, cal)
                    if (matched > 0) return matched
                }
                return start.inv()
            }
            15 -> { if (value == cal.getLeastMaximum(Calendar.HOUR) + 1) value = 0; cal.set(Calendar.HOUR, value); return pos.index }
            17, 23, 24, 29, 31, 32, 33 -> {
                val style = when (index) {
                    17 -> if (count < 4) Style.SPECIFIC_SHORT else Style.SPECIFIC_LONG
                    23 -> if (count < 4) Style.ISO_BASIC_LOCAL_FULL else if (count == 5) Style.ISO_EXTENDED_FULL else Style.LOCALIZED_GMT
                    24 -> if (count < 4) Style.GENERIC_SHORT else Style.GENERIC_LONG
                    29 -> when (count) { 1 -> Style.ZONE_ID_SHORT; 2 -> Style.ZONE_ID; 3 -> Style.EXEMPLAR_LOCATION; else -> Style.GENERIC_LOCATION }
                    31 -> if (count < 4) Style.LOCALIZED_GMT_SHORT else Style.LOCALIZED_GMT
                    32 -> when (count) { 1 -> Style.ISO_BASIC_SHORT; 2 -> Style.ISO_BASIC_FIXED; 3 -> Style.ISO_EXTENDED_FIXED; 4 -> Style.ISO_BASIC_FULL; else -> Style.ISO_EXTENDED_FULL }
                    else -> when (count) { 1 -> Style.ISO_BASIC_LOCAL_SHORT; 2 -> Style.ISO_BASIC_LOCAL_FIXED; 3 -> Style.ISO_EXTENDED_LOCAL_FIXED; 4 -> Style.ISO_BASIC_LOCAL_FULL; else -> Style.ISO_EXTENDED_LOCAL_FULL }
                }
                val zone = owner.timeZoneFormat().parse(style, text, pos, timeType)
                if (zone != null) { cal.setTimeZone(zone); return pos.index }
                return start.inv()
            }
            27, 28 -> {
                if (count <= 2 && number != null) { cal.set(Calendar.MONTH, (value - 1) * 3); return pos.index }
                var matched = 0
                val keys = if (index == 27) arrayOf("quarters", "shortQuarters", "narrowQuarters")
                    else arrayOf("standaloneQuarters", "standaloneShortQuarters", "standaloneNarrowQuarters")
                for ((i, width) in intArrayOf(4, 3, 5).withIndex()) if (multiple() || count == width) {
                    matched = owner.matchQuarterString(text, start, Calendar.MONTH, data(keys[i])!!, cal)
                    if (matched > 0) return matched
                }
                if (numeric() && number != null) { cal.set(Calendar.MONTH, (value - 1) * 3); return pos.index }
                return matched
            }
            37 -> {
                val separator = owner.symbols!!.getTimeSeparatorString()
                val names = arrayListOf(separator)
                if (separator != DateFormatSymbols.DEFAULT_TIME_SEPARATOR) names.add(DateFormatSymbols.DEFAULT_TIME_SEPARATOR)
                if (attribute(BooleanAttribute.PARSE_PARTIAL_LITERAL_MATCH) && separator != DateFormatSymbols.ALTERNATE_TIME_SEPARATOR) names.add(DateFormatSymbols.ALTERNATE_TIME_SEPARATOR)
                return matchString(text, start, -1, names.toTypedArray(), null, cal)
            }
            35, 36 -> {
                if (index == 35) {
                    val matched = owner.subParse(text, start, 'a', count, obeyCount, allowNegative, ambiguousYear, cal, leap, timeType, dayPeriod)
                    if (matched > 0) return matched
                }
                var matched = 0
                // The source intentionally tests count == 4 for both wide and narrow.
                for ((width, key) in listOf(3 to "abbreviatedDayPeriods", 4 to "wideDayPeriods", 4 to "narrowDayPeriods")) if (multiple() || count == width) {
                    val names = data(key)!!
                    matched = matchDayPeriodString(text, start, names, if (index == 35) 2 else names.size, dayPeriod)
                    if (matched > 0) return matched
                }
                return matched
            }
            else -> {
                if (obeyCount && start + count > text.length) return -start
                number = parseInt(text, if (obeyCount) count else -1, pos, allowNegative, nf)
                if (obeyCount && !owner.isLenient() && pos.index < start + count) return -start
                if (number != null) {
                    if (index != DateFormat.RELATED_YEAR) cal.set(field, number.toInt()) else cal.setRelatedYear(number.toInt())
                    return pos.index
                }
                return start.inv()
            }
        }
    }

    fun matchString(text: String, start: Int, field: Int, names: Array<String?>, monthPattern: String?, cal: Calendar): Int {
        var bestLength = 0
        var best = -1
        var leap = 0
        for (i in (if (field == Calendar.DAY_OF_WEEK) 1 else 0) until names.size) {
            val name = names[i]!!
            if (name.length > bestLength) {
                val length = regionMatchesWithOptionalDot(text, start, name, name.length)
                if (length >= 0) { best = i; bestLength = length; leap = 0 }
            }
            if (monthPattern != null) {
                val leapName = SimpleFormatterImpl.formatRawPattern(monthPattern, 1, 1, name)
                if (leapName.length > bestLength) {
                    val length = regionMatchesWithOptionalDot(text, start, leapName, leapName.length)
                    if (length >= 0) { best = i; bestLength = length; leap = 1 }
                }
            }
        }
        if (best >= 0) {
            if (field >= 0) {
                if (field == Calendar.YEAR) best++
                cal.set(field, best)
                if (monthPattern != null) cal.set(Calendar.IS_LEAP_MONTH, leap)
            }
            return start + bestLength
        }
        return start.inv()
    }

    fun matchAlphaMonthStrings(text: String, start: Int, wide: Array<String?>, short: Array<String?>, cal: Calendar): Int {
        var bestLength = 0
        var best = -1
        for (names in arrayOf(wide, short)) for (i in names.indices) {
            val name = names[i]!!
            if (name.length > bestLength) {
                val length = regionMatchesWithOptionalDot(text, start, name, name.length)
                if (length >= 0) { best = i; bestLength = length }
            }
        }
        if (best >= 0) { cal.set(Calendar.MONTH, best); return start + bestLength }
        return start.inv()
    }

    fun matchQuarterString(text: String, start: Int, field: Int, names: Array<String?>, cal: Calendar): Int {
        var bestLength = 0
        var best = -1
        for (i in names.indices) {
            val name = names[i]!!
            if (name.length > bestLength) {
                val length = regionMatchesWithOptionalDot(text, start, name, name.length)
                if (length >= 0) { best = i; bestLength = length }
            }
        }
        if (best >= 0) { cal.set(field, best * 3); return start + bestLength }
        return -start
    }

    fun matchDayPeriodString(text: String, start: Int, names: Array<String?>, length: Int, result: Output<DayPeriodRules.DayPeriod>?): Int {
        var bestLength = 0
        var best = -1
        for (i in 0 until length) {
            if (i !in names.indices) throw IndexOutOfBoundsException()
            val name = names[i] ?: continue
            if (name.length > bestLength) {
                val matched = regionMatchesWithOptionalDot(text, start, name, name.length)
                if (matched >= 0) { best = i; bestLength = matched }
            }
        }
        if (best >= 0) {
            if (best !in DayPeriodRules.DayPeriod.VALUES.indices) throw IndexOutOfBoundsException()
            val period = DayPeriodRules.DayPeriod.VALUES[best]
            result!!.value = period
            return start + bestLength
        }
        return -start
    }

    companion object {
        // Source static state is deliberately shared between formatter instances.
        var delayedHebrewMonthCheck = false
        fun parseInt(text: String, maxDigits: Int, pos: ParsePosition, allowNegative: Boolean, format: NumberFormat?): Number? {
            val oldPos = pos.index
            var number: Number?
            if (allowNegative) number = format!!.parse(text, pos)
            else if (format is DecimalFormat) {
                val oldPrefix = format.getNegativePrefix()
                format.setNegativePrefix("\uAB00")
                number = format.parse(text, pos)
                format.setNegativePrefix(oldPrefix)
            } else {
                if (format is DateNumberFormat) format.setParsePositiveOnly(true)
                number = format!!.parse(text, pos)
                if (format is DateNumberFormat) format.setParsePositiveOnly(false)
            }
            if (maxDigits > 0) {
                var digits = pos.index - oldPos
                if (digits > maxDigits) {
                    var value = number!!.toDouble()
                    digits -= maxDigits
                    while (digits > 0) { value /= 10; digits-- }
                    pos.index = oldPos + maxDigits
                    number = value.toInt()
                }
            }
            return number
        }
        fun countDigits(text: String, start: Int, end: Int): Int {
            var count = 0
            var index = start
            while (index < end) {
                val cp = forwardCodePoint(text, index)
                if (UCharacter.isDigit(cp)) count++
                index += UTF16.getCharCount(cp)
            }
            return count
        }
        private fun codePoint(high: Char, low: Char): Int = ((high.code - 0xD800) shl 10) + low.code - 0xDC00 + 0x10000
        private fun forwardCodePoint(text: String, index: Int): Int {
            if (index !in text.indices) throw IndexOutOfBoundsException()
            val ch = text[index]
            return if (ch.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate())
                codePoint(ch, text[index + 1]) else ch.code
        }
        fun regionMatchesWithOptionalDot(text: String, start: Int, data: String, length: Int): Int {
            if (regionMatches(text, start, data, length)) return length
            if (data.isNotEmpty() && data.last() == '.' && regionMatches(text, start, data, length - 1)) return length - 1
            return -1
        }
        // java.lang.String.regionMatches(true,...) compares supplementary case pairs too.
        private fun regionMatches(text: String, start: Int, data: String, length: Int): Boolean {
            if (start < 0 || length > text.length - start || length > data.length) return false
            var offset = 0
            while (offset < length) {
                val a = text[start + offset]
                val b = data[offset]
                val ca = if (a.isHighSurrogate() && offset + 1 < length && text[start + offset + 1].isLowSurrogate()) codePoint(a, text[start + offset + 1]) else a.code
                val cb = if (b.isHighSurrogate() && offset + 1 < length && data[offset + 1].isLowSurrogate()) codePoint(b, data[offset + 1]) else b.code
                if (ca == cb) { offset += UTF16.getCharCount(ca); continue }
                val upperA = UCharacter.toUpperCase(ca)
                val upperB = UCharacter.toUpperCase(cb)
                if (upperA != upperB && UCharacter.toLowerCase(upperA) != UCharacter.toLowerCase(upperB)) return false
                offset += UTF16.getCharCount(ca)
            }
            return true
        }
    }
}
