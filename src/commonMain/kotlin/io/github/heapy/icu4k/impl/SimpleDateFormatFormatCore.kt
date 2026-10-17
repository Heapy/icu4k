// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.text.DateFormatSymbols.CapitalizationContextUsage as Usage
import io.github.heapy.icu4k.text.TimeZoneFormat.Style
import io.github.heapy.icu4k.util.*

/** The full SimpleDateFormat formatting engine; virtual API hooks stay on its owner. */
internal class SimpleDateFormatFormatCore(private val owner: Owner) {
    interface Owner {
        val calendar: Calendar?
        val pattern: String?
        val locale: ULocale?
        val symbols: DateFormatSymbols?
        val numberFormat: NumberFormat?
        val numberingOverride: String?
        val hasMinute: Boolean
        val hasSecond: Boolean
        val useFastFormat: Boolean
        fun capitalizationContext(): DisplayContext?
        fun numberFormat(ch: Char): NumberFormat?
        fun timeZoneFormat(): TimeZoneFormat
        fun zeroPaddingNumber(nf: NumberFormat?, buf: StringBuilder?, value: Int, min: Int, max: Int)
        fun subFormat(buf: StringBuilder?, ch: Char, count: Int, begin: Int, fieldNum: Int,
                      context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?)
        fun subFormat(ch: Char, count: Int, begin: Int, fieldNum: Int,
                      context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?): String?
        fun patternCharToDateFormatField(ch: Char): DateFormat.Field?
    }
    private var patternItems: Array<Any>? = null
    var capitalizationBrkIter: BreakIterator? = null
    fun resetPattern() { patternItems = null }
    fun copyStateFrom(other: SimpleDateFormatFormatCore) {
        patternItems = other.patternItems
        capitalizationBrkIter = other.capitalizationBrkIter
    }

    fun format(input: Calendar?, output: StringBuilder?, pos: FormatFieldPosition?, attributes: MutableList<FormatFieldPosition>? = null): StringBuilder? {
        var cal = input
        var backup: TimeZone? = null
        if (cal !== owner.calendar && cal!!.getType() != owner.calendar!!.getType()) {
            owner.calendar!!.setTimeInMillis(cal.getTimeInMillis())
            backup = owner.calendar!!.getTimeZone()
            owner.calendar!!.setTimeZone(cal.getTimeZone())
            cal = owner.calendar
        }
        val result = format(cal, owner.capitalizationContext(), output, pos, attributes)
        // Source restores the zone only after successful formatting.
        if (backup != null) owner.calendar!!.setTimeZone(backup)
        return result
    }

    private fun format(cal: Calendar?, context: DisplayContext?, output: StringBuilder?, pos: FormatFieldPosition?, attributes: MutableList<FormatFieldPosition>?): StringBuilder? {
        pos!!.setBeginIndex(0)
        pos.setEndIndex(0)
        val items = patternItems ?: SimpleDateFormatPatternCore.compile(owner.pattern!!).also { patternItems = it }
        for ((i, item) in items.withIndex()) {
            if (item is String) output!!.append(item)
            else {
                item as SimpleDateFormatPatternCore.PatternItem
                val start = if (attributes != null) output!!.length else 0
                if (owner.useFastFormat) owner.subFormat(output, item.type, item.length, output!!.length, i, context, pos, item.type, cal)
                else output!!.append(owner.subFormat(item.type, item.length, output.length, i, context, pos, item.type, cal))
                if (attributes != null) {
                    val end = output!!.length
                    if (end - start > 0) attributes.add(FormatFieldPosition(owner.patternCharToDateFormatField(item.type)).also {
                        it.setBeginIndex(start); it.setEndIndex(end)
                    })
                }
            }
        }
        return output
    }

    fun formatFieldString(ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?): String {
        val buf = StringBuilder()
        owner.subFormat(buf, ch, count, begin, fieldNum, context, pos, outputChar, cal)
        return buf.toString()
    }

    fun formatField(output: StringBuilder?, ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, position: FormatFieldPosition?, outputChar: Char, calendar: Calendar?) {
        val buf = output!!
        val bufStart = buf.length
        val cal = calendar!!
        val tz = cal.getTimeZone()
        val date = cal.getTimeInMillis()
        val index = SimpleDateFormatPatternCore.index(ch)
        if (index == -1) {
            if (ch == 'l') return
            throw IllegalArgumentException("Illegal pattern character '$ch' in \"${owner.pattern}\"")
        }
        val field = SimpleDateFormatPatternCore.calendarField(index)
        var value = if (field >= 0) { if (index != DateFormat.RELATED_YEAR) cal.get(field) else cal.getRelatedYear() } else 0
        val nf = owner.numberFormat(ch)
        var usage = Usage.OTHER
        fun symbols(name: String): Array<String?>? = owner.symbols!!.state.data.fields[name]
        fun number(v: Int = value, min: Int = count, max: Int = Int.MAX_VALUE, target: StringBuilder = buf) = owner.zeroPaddingNumber(nf, target, v, min, max)
        fun append(name: String, v: Int = value) = safeAppend(symbols(name), v, buf)
        fun dayPeriod(period: DayPeriodRules.DayPeriod?): String? {
            val name = if (count <= 3) "abbreviatedDayPeriods" else if (count == 4 || count > 5) "wideDayPeriods" else "narrowDayPeriods"
            val array = symbols(name)!!
            val i = period!!.ordinal
            if (i !in array.indices) throw IndexOutOfBoundsException()
            return array[i]
        }
        fun fallback(output: Char) = owner.subFormat(buf, 'a', count, begin, fieldNum, context, position, output, cal)
        when (index) {
            0 -> if (cal.getType() == "chinese" || cal.getType() == "dangi") number(min = 1, max = 9)
                 else when (count) {
                     5 -> { append("narrowEras"); usage = Usage.ERA_NARROW }
                     4 -> { append("eraNames"); usage = Usage.ERA_WIDE }
                     else -> { append("eras"); usage = Usage.ERA_ABBREV }
                 }
            1, 18, 30 -> {
                val years = if (index == 30) symbols("shortYearNames") else null
                if (years != null && value <= years.size) safeAppend(years, value - 1, buf)
                else {
                    val override = owner.numberingOverride
                    if (override != null && (override == "hebr" || "y=hebr" in override) && value > 5000 && value < 6000) value -= 5000
                    if (count == 2) number(min = 2, max = 2) else number()
                }
            }
            2, 26 -> {
                if (cal.getType() == "hebrew") {
                    val leap = HebrewCalendar.isLeapYear(cal.get(Calendar.YEAR))
                    if (leap && value == 6 && count >= 3) value = 13
                    if (!leap && value >= 6 && count < 3) value--
                }
                val patterns = symbols("leapMonthPatterns")
                val leap = if (patterns != null && patterns.size >= 7) cal.get(Calendar.IS_LEAP_MONTH) else 0
                fun month(name: String, slot: Int) = safeAppendWithMonthPattern(symbols(name), value, buf, if (leap != 0) patterns!![slot] else null)
                when (count) {
                    5 -> { if (index == 2) month("narrowMonths", 2) else month("standaloneNarrowMonths", 5); usage = Usage.MONTH_NARROW }
                    4 -> if (index == 2) { month("months", 0); usage = Usage.MONTH_FORMAT } else { month("standaloneMonths", 3); usage = Usage.MONTH_STANDALONE }
                    3 -> if (index == 2) { month("shortMonths", 1); usage = Usage.MONTH_FORMAT } else { month("standaloneShortMonths", 4); usage = Usage.MONTH_STANDALONE }
                    else -> {
                        val month = StringBuilder()
                        number(value + 1, target = month)
                        safeAppendWithMonthPattern(arrayOf(month.toString()), 0, buf, if (leap != 0) patterns!![6] else null)
                    }
                }
            }
            4 -> number(if (value == 0) cal.getMaximum(Calendar.HOUR_OF_DAY) + 1 else value)
            8 -> {
                val primary = owner.numberFormat!!
                primary.setMinimumIntegerDigits(minOf(3, count))
                primary.setMaximumIntegerDigits(Int.MAX_VALUE)
                if (count == 1) value /= 100 else if (count == 2) value /= 10
                val p = FormatFieldPosition(-1)
                primary.format(value.toLong(), buf, p)
                if (count > 3) { primary.setMinimumIntegerDigits(count - 3); primary.format(0L, buf, p) }
            }
            9, 19 -> if (index == 19 && count < 3) number() else {
                if (index == 19) value = cal.get(Calendar.DAY_OF_WEEK)
                when {
                    count == 5 -> { append("narrowWeekdays"); usage = Usage.DAY_NARROW }
                    count == 4 -> { append("weekdays"); usage = Usage.DAY_FORMAT }
                    count == 6 && symbols("shorterWeekdays") != null -> { append("shorterWeekdays"); usage = Usage.DAY_FORMAT }
                    else -> { append("shortWeekdays"); usage = Usage.DAY_FORMAT }
                }
            }
            14 -> when {
                count == 4 && symbols("ampmsWide") != null -> append("ampmsWide")
                count == 5 && symbols("ampmsNarrow") != null -> append("ampmsNarrow")
                else -> append("ampms")
            }
            15 -> number(if (value == 0) cal.getLeastMaximum(Calendar.HOUR) + 1 else value)
            17 -> {
                val style = if (count < 4) { usage = Usage.METAZONE_SHORT; Style.SPECIFIC_SHORT } else { usage = Usage.METAZONE_LONG; Style.SPECIFIC_LONG }
                buf.append(owner.timeZoneFormat().format(style, tz, date))
            }
            23 -> buf.append(owner.timeZoneFormat().format(if (count < 4) Style.ISO_BASIC_LOCAL_FULL else if (count == 5) Style.ISO_EXTENDED_FULL else Style.LOCALIZED_GMT, tz, date))
            24 -> {
                val style = when (count) { 1 -> { usage = Usage.METAZONE_SHORT; Style.GENERIC_SHORT }; 4 -> { usage = Usage.METAZONE_LONG; Style.GENERIC_LONG }; else -> null }
                buf.append(if (style == null) null else owner.timeZoneFormat().format(style, tz, date))
            }
            29 -> {
                val style = when (count) { 1 -> Style.ZONE_ID_SHORT; 2 -> Style.ZONE_ID; 3 -> Style.EXEMPLAR_LOCATION; 4 -> { usage = Usage.ZONE_LONG; Style.GENERIC_LOCATION }; else -> null }
                buf.append(if (style == null) null else owner.timeZoneFormat().format(style, tz, date))
            }
            31 -> {
                val style = when (count) { 1 -> Style.LOCALIZED_GMT_SHORT; 4 -> Style.LOCALIZED_GMT; else -> null }
                buf.append(if (style == null) null else owner.timeZoneFormat().format(style, tz, date))
            }
            32, 33 -> {
                val style = if (index == 32) when (count) { 1 -> Style.ISO_BASIC_SHORT; 2 -> Style.ISO_BASIC_FIXED; 3 -> Style.ISO_EXTENDED_FIXED; 4 -> Style.ISO_BASIC_FULL; 5 -> Style.ISO_EXTENDED_FULL; else -> null }
                else when (count) { 1 -> Style.ISO_BASIC_LOCAL_SHORT; 2 -> Style.ISO_BASIC_LOCAL_FIXED; 3 -> Style.ISO_EXTENDED_LOCAL_FIXED; 4 -> Style.ISO_BASIC_LOCAL_FULL; 5 -> Style.ISO_EXTENDED_LOCAL_FULL; else -> null }
                buf.append(if (style == null) null else owner.timeZoneFormat().format(style, tz, date))
            }
            25 -> if (count < 3) number(min = 1) else {
                value = cal.get(Calendar.DAY_OF_WEEK)
                when {
                    count == 5 -> { append("standaloneNarrowWeekdays"); usage = Usage.DAY_NARROW }
                    count == 4 -> { append("standaloneWeekdays"); usage = Usage.DAY_STANDALONE }
                    count == 6 && symbols("standaloneShorterWeekdays") != null -> { append("standaloneShorterWeekdays"); usage = Usage.DAY_STANDALONE }
                    else -> { append("standaloneShortWeekdays"); usage = Usage.DAY_STANDALONE }
                }
            }
            27, 28 -> {
                val prefix = if (index == 27) "" else "standalone"
                when {
                    count >= 5 -> append(if (prefix.isEmpty()) "narrowQuarters" else "standaloneNarrowQuarters", value / 3)
                    count == 4 -> append(if (prefix.isEmpty()) "quarters" else "standaloneQuarters", value / 3)
                    count == 3 -> append(if (prefix.isEmpty()) "shortQuarters" else "standaloneShortQuarters", value / 3)
                    else -> number(value / 3 + 1)
                }
            }
            35 -> {
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                var text: String? = null
                if (hour == 12 && (!owner.hasMinute || cal.get(Calendar.MINUTE) == 0) && (!owner.hasSecond || cal.get(Calendar.SECOND) == 0)) {
                    value = cal.get(Calendar.AM_PM)
                    val name = if (count <= 3) "abbreviatedDayPeriods" else if (count == 4 || count > 5) "wideDayPeriods" else "narrowDayPeriods"
                    val values = symbols(name)!!
                    if (value !in values.indices) throw IndexOutOfBoundsException()
                    text = values[value]
                }
                if (text == null) fallback('b') else buf.append(text)
            }
            36 -> {
                val rules = DayPeriodRules.getInstance(owner.locale)
                if (rules == null) { fallback('B'); return }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = if (owner.hasMinute) cal.get(Calendar.MINUTE) else 0
                val second = if (owner.hasSecond) cal.get(Calendar.SECOND) else 0
                var period = if (hour == 0 && minute == 0 && second == 0 && rules.hasMidnight()) DayPeriodRules.DayPeriod.MIDNIGHT
                             else if (hour == 12 && minute == 0 && second == 0 && rules.hasNoon()) DayPeriodRules.DayPeriod.NOON
                             else rules.getDayPeriodForHour(hour)
                var text: String? = null
                if (period != DayPeriodRules.DayPeriod.AM && period != DayPeriodRules.DayPeriod.PM && period != DayPeriodRules.DayPeriod.MIDNIGHT) text = dayPeriod(period)
                if (text == null && (period == DayPeriodRules.DayPeriod.MIDNIGHT || period == DayPeriodRules.DayPeriod.NOON)) {
                    period = rules.getDayPeriodForHour(hour)
                    text = dayPeriod(period)
                }
                if (period == DayPeriodRules.DayPeriod.AM || period == DayPeriodRules.DayPeriod.PM || text == null) { fallback('B'); return }
                else buf.append(text)
            }
            37 -> buf.append(owner.symbols!!.getTimeSeparatorString())
            else -> number()
        }
        if (fieldNum == 0 && context != null && buf.length > bufStart && UCharacter.isLowerCase(codePointAt(buf, bufStart))) {
            var titlecase = false
            when (context) {
                DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE -> titlecase = true
                DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU, DisplayContext.CAPITALIZATION_FOR_STANDALONE -> {
                    val transforms = owner.symbols!!.state.data.capitalization
                    if (transforms != null) {
                        val flags = transforms[usage]!!
                        val i = if (context == DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU) 0 else 1
                        if (i !in flags.indices) throw IndexOutOfBoundsException()
                        titlecase = flags[i]
                    }
                }
                else -> Unit
            }
            if (titlecase) {
                if (capitalizationBrkIter == null) capitalizationBrkIter = BreakIterator.getSentenceInstance(owner.locale)
                val mutable = capitalizationBrkIter!!.clone()
                val text = UCharacter.toTitleCase(owner.locale, buf.substring(bufStart), mutable, UCharacter.TITLECASE_NO_LOWERCASE or UCharacter.TITLECASE_NO_BREAK_ADJUSTMENT)
                buf.deleteRange(bufStart, buf.length)
                buf.append(text)
            }
        }
        val outIndex = SimpleDateFormatPatternCore.index(outputChar)
        val pos = position!!
        if (pos.getBeginIndex() == pos.getEndIndex()) {
            if (pos.getField() == SimpleDateFormatPatternCore.dateFormatField(outIndex) || pos.getFieldAttribute() === SimpleDateFormatPatternCore.attribute(outputChar)) {
                pos.setBeginIndex(begin)
                pos.setEndIndex(begin + buf.length - bufStart)
            }
        }
    }

    private fun codePointAt(text: CharSequence, index: Int): Int {
        val first = text[index]
        return if (first.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate())
            0x10000 + ((first.code - 0xd800) shl 10) + text[index + 1].code - 0xdc00
        else first.code
    }

    private fun safeAppend(array: Array<String?>?, value: Int, output: StringBuilder) {
        if (array != null && value >= 0 && value < array.size) output.append(array[value])
    }
    private fun safeAppendWithMonthPattern(array: Array<String?>?, value: Int, output: StringBuilder, pattern: String?) {
        if (array != null && value >= 0 && value < array.size) {
            if (pattern == null) output.append(array[value])
            else output.append(SimpleFormatterImpl.formatRawPattern(pattern, 1, 1, array[value]))
        }
    }
}
