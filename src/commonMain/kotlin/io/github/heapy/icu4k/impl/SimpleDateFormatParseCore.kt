// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.text.DateFormat.BooleanAttribute
import io.github.heapy.icu4k.text.TimeZoneFormat.TimeType
import io.github.heapy.icu4k.util.*

/** SimpleDateFormat's parsing traversal and calendar/time-zone resolution. */
internal class SimpleDateFormatParseCore(private val owner: Owner) {
    interface NumericLeapMonth {
        fun setNumberFormat(format: NumberFormat?)
        fun parse(text: String, position: ParsePosition): Array<Any?>?
    }
    interface Owner {
        val calendar: Calendar?
        val pattern: String?
        val locale: ULocale?
        val symbols: DateFormatSymbols?
        val assertionsEnabled: Boolean get() = false
        fun booleanAttribute(key: BooleanAttribute): Boolean
        fun defaultCenturyStart(): Long
        fun defaultCenturyStartYear(): Int
        fun subParse(text: String, start: Int, ch: Char, count: Int, obeyCount: Boolean,
                     allowNegative: Boolean, ambiguousYear: BooleanArray, cal: Calendar,
                     numericLeapMonth: NumericLeapMonth?, timeType: Output<TimeType>?,
                     dayPeriod: Output<DayPeriodRules.DayPeriod>?): Int
        fun numericLeapMonthParser(pattern: String): NumericLeapMonth
    }
    private var patternItems: Array<Any>? = null
    fun resetPattern() { patternItems = null }
    fun copyStateFrom(other: SimpleDateFormatParseCore) { patternItems = other.patternItems }

    fun parse(text: String?, input: Calendar?, position: ParsePosition?) {
        var cal = input!!
        var backupTZ: TimeZone? = null
        var resultCal: Calendar? = null
        if (cal !== owner.calendar && cal.getType() != owner.calendar!!.getType()) {
            owner.calendar!!.setTimeInMillis(cal.getTimeInMillis())
            backupTZ = owner.calendar!!.getTimeZone()
            owner.calendar!!.setTimeZone(cal.getTimeZone())
            resultCal = cal
            cal = owner.calendar!!
        }
        var pos = position!!.index
        if (pos < 0) { position.errorIndex = 0; return }
        val start = pos
        val dayPeriod = Output<DayPeriodRules.DayPeriod>(null)
        val timeType = Output(TimeType.UNKNOWN)
        val ambiguousYear = booleanArrayOf(false)
        var numericFieldStart = -1
        var numericFieldLength = 0
        var numericStartPos = 0
        val leapPatterns = owner.symbols!!.state.data.fields["leapMonthPatterns"] as Array<String>?
        val leapParser = if (leapPatterns != null && leapPatterns.size >= DateFormatSymbols.DT_MONTH_PATTERN_COUNT)
            owner.numericLeapMonthParser(leapPatterns[DateFormatSymbols.DT_LEAP_MONTH_PATTERN_NUMERIC]) else null
        val items = patternItems ?: SimpleDateFormatPatternCore.compile(owner.pattern!!).also { patternItems = it }
        fun fail(error: Int) {
            position.index = start
            position.errorIndex = error
            if (backupTZ != null) owner.calendar!!.setTimeZone(backupTZ)
        }
        var i = 0
        while (i < items.size) {
            val item = items[i]
            if (item is SimpleDateFormatPatternCore.PatternItem) {
                if (item.isNumeric && numericFieldStart == -1 && i + 1 < items.size &&
                    (items[i + 1] as? SimpleDateFormatPatternCore.PatternItem)?.isNumeric == true) {
                    numericFieldStart = i
                    numericFieldLength = item.length
                    numericStartPos = pos
                }
                if (numericFieldStart != -1) {
                    val length = if (numericFieldStart == i) numericFieldLength else item.length
                    // ICU4J's ten-argument overload drops the leap formatter and zone output.
                    pos = owner.subParse(text!!, pos, item.type, length, true, false, ambiguousYear, cal, null, null, null)
                    if (pos < 0) {
                        numericFieldLength--
                        if (numericFieldLength == 0) { fail(pos); return }
                        i = numericFieldStart
                        pos = numericStartPos
                        continue
                    }
                } else if (item.type != 'l') {
                    numericFieldStart = -1
                    val before = pos
                    pos = owner.subParse(text!!, pos, item.type, item.length, false, true, ambiguousYear, cal, leapParser, timeType, dayPeriod)
                    if (pos < 0) {
                        if (pos == ISO_SPECIAL_ERA) {
                            pos = before
                            if (i + 1 < items.size) {
                                val literal = items[i + 1] as? String
                                if (literal == null) { fail(before); return }
                                var index = 0
                                while (index < literal.length && whitespace(literal[index].code)) index++
                                if (index == literal.length) i++
                            }
                        } else { fail(before); return }
                    }
                }
            } else {
                numericFieldStart = -1
                val complete = booleanArrayOf(false)
                pos = matchLiteral(text!!, pos, items, i, complete)
                if (!complete[0]) { fail(pos); return }
            }
            i++
        }
        if (pos < text!!.length && text[pos] == '.' && owner.booleanAttribute(BooleanAttribute.PARSE_ALLOW_WHITESPACE) && items.isNotEmpty()) {
            val last = items.last()
            if (last is SimpleDateFormatPatternCore.PatternItem && !last.isNumeric) pos++
        }
        if (dayPeriod.value != null) {
            val rules = DayPeriodRules.getInstance(owner.locale!!)!!
            if (!cal.isSet(Calendar.HOUR) && !cal.isSet(Calendar.HOUR_OF_DAY)) {
                val midpoint = rules.getMidPointForDayPeriod(dayPeriod.value!!)
                val hour = midpoint.toInt()
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, if (midpoint - hour > 0) 30 else 0)
            } else {
                var hour = if (cal.isSet(Calendar.HOUR_OF_DAY)) cal.get(Calendar.HOUR_OF_DAY)
                    else cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
                if (owner.assertionsEnabled && hour !in 0..23) throw AssertionError()
                if (hour == 0 || hour in 13..23) cal.set(Calendar.HOUR_OF_DAY, hour)
                else {
                    if (hour == 12) hour = 0
                    val ahead = hour + cal.get(Calendar.MINUTE) / 60.0 - rules.getMidPointForDayPeriod(dayPeriod.value!!)
                    cal.set(Calendar.AM_PM, if (ahead >= -6 && ahead < 6) 0 else 1)
                }
            }
        }
        position.index = pos
        try {
            val type = timeType.value
            if (ambiguousYear[0]) {
                val copy = cal.clone() as Calendar
                if (copy.getTimeInMillis() < owner.defaultCenturyStart()) cal.set(Calendar.YEAR, owner.defaultCenturyStartYear() + 100)
            }
            if (type != TimeType.UNKNOWN) resolveTimeZone(cal, type)
        } catch (_: IllegalArgumentException) { fail(pos); return }
        if (resultCal != null) {
            resultCal.setTimeZone(cal.getTimeZone())
            resultCal.setTimeInMillis(cal.getTimeInMillis())
        }
        if (backupTZ != null) owner.calendar!!.setTimeZone(backupTZ)
    }

    private fun resolveTimeZone(cal: Calendar, type: TimeType?) {
        val copy = cal.clone() as Calendar
        val tz = copy.getTimeZone()!!
        val basic = tz as? BasicTimeZone
        copy.set(Calendar.ZONE_OFFSET, 0)
        copy.set(Calendar.DST_OFFSET, 0)
        val local = copy.getTimeInMillis()
        val offsets = IntArray(2)
        if (basic != null) {
            basic.getOffsetFromLocal(local,
                if (type == TimeType.STANDARD) BasicTimeZone.LocalOption.STANDARD_FORMER else BasicTimeZone.LocalOption.DAYLIGHT_FORMER,
                if (type == TimeType.STANDARD) BasicTimeZone.LocalOption.STANDARD_LATTER else BasicTimeZone.LocalOption.DAYLIGHT_LATTER, offsets)
        } else {
            tz.getOffset(local, true, offsets)
            if ((type == TimeType.STANDARD && offsets[1] != 0) || (type == TimeType.DAYLIGHT && offsets[1] == 0))
                tz.getOffset(local - 86_400_000L, true, offsets)
        }
        var savings = offsets[1]
        if (type == TimeType.STANDARD) {
            if (savings != 0) savings = 0
        } else if (savings == 0) {
            if (basic != null) {
                val base = local + offsets[0]
                var time = base
                var limit = base + MAX_DAYLIGHT_DETECTION_RANGE
                while (time < limit) {
                    val transition = basic.getNextTransition(time, false) ?: break
                    savings = transition.getTo()!!.getDSTSavings()
                    if (savings != 0) break
                    time = transition.getTime()
                }
                if (savings == 0) {
                    time = base
                    limit = base - MAX_DAYLIGHT_DETECTION_RANGE
                    while (time > limit) {
                        val transition = basic.getPreviousTransition(time, true) ?: break
                        savings = transition.getFrom()!!.getDSTSavings()
                        if (savings != 0) break
                        time = transition.getTime() - 1
                    }
                    if (savings == 0) savings = basic.getDSTSavings()
                }
            } else savings = tz.getDSTSavings()
            if (savings == 0) savings = 3_600_000
        }
        cal.set(Calendar.ZONE_OFFSET, offsets[0])
        cal.set(Calendar.DST_OFFSET, savings)
    }

    fun matchLiteral(text: String, initial: Int, items: Array<Any>, itemIndex: Int, complete: BooleanArray): Int {
        var pos = initial
        val literal = items[itemIndex] as String
        var index = 0
        while (index < literal.length && pos < text.length) {
            val pch = literal[index]
            val ich = text[pos]
            if (whitespace(pch.code) && whitespace(ich.code)) {
                while (index + 1 < literal.length && whitespace(literal[index + 1].code)) index++
                while (pos + 1 < text.length && whitespace(text[pos + 1].code)) pos++
            } else if (pch != ich) {
                if (ich == '.' && pos == initial && itemIndex > 0 && owner.booleanAttribute(BooleanAttribute.PARSE_ALLOW_WHITESPACE)) {
                    val before = items[itemIndex - 1]
                    if (before is SimpleDateFormatPatternCore.PatternItem && !before.isNumeric) { pos++; continue }
                } else if ((pch == ' ' || pch == '.') && owner.booleanAttribute(BooleanAttribute.PARSE_ALLOW_WHITESPACE)) {
                    index++; continue
                } else if (pos != initial && owner.booleanAttribute(BooleanAttribute.PARSE_PARTIAL_LITERAL_MATCH)) {
                    index++; continue
                }
                break
            }
            index++; pos++
        }
        complete[0] = index == literal.length
        if (!complete[0] && owner.booleanAttribute(BooleanAttribute.PARSE_ALLOW_WHITESPACE) && itemIndex > 0 && itemIndex < items.lastIndex && initial < text.length) {
            val before = items[itemIndex - 1] as? SimpleDateFormatPatternCore.PatternItem
            val after = items[itemIndex + 1] as? SimpleDateFormatPatternCore.PatternItem
            if (before != null && after != null && (before.type in DATE_PATTERN_TYPE) != (after.type in DATE_PATTERN_TYPE)) {
                var newPos = initial
                while (newPos < text.length && PatternProps.isWhiteSpace(text[newPos].code)) newPos++
                complete[0] = newPos > initial
                pos = newPos
            }
        }
        return pos
    }

    companion object {
        const val ISO_SPECIAL_ERA = -32000
        private const val MAX_DAYLIGHT_DETECTION_RANGE = 30 * 365 * 24 * 60 * 60 * 1000L
        private const val DATE_PATTERN_TYPE = "GyYuUQqMLlwWd"
        fun whitespace(cp: Int): Boolean = PatternProps.isWhiteSpace(cp) || UCharacter.isUWhiteSpace(cp)
    }
}
