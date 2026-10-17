// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.util.Calendar
import io.github.heapy.icu4k.util.ICUException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Locale-sensitive numeric and qualitative relative dates, with attributed results. */
class RelativeDateTimeFormatter private constructor(
    private val qualitativeUnitMap: Map<Style, Map<AbsoluteUnit, Map<Direction, String>>>,
    private val patternMap: Map<Style, Map<RelativeUnit, Array<Array<String?>>>>,
    private val combinedDateAndTime: String,
    private val pluralRules: PluralRules,
    private val numberFormat: NumberFormat,
    private val style: Style?,
    private val capitalizationContext: DisplayContext?,
    private val breakIterator: BreakIterator?,
    private val locale: ULocale,
) {
    enum class Style { LONG, SHORT, NARROW }
    enum class RelativeUnit { SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS, QUARTERS,
        SUNDAYS, MONDAYS, TUESDAYS, WEDNESDAYS, THURSDAYS, FRIDAYS, SATURDAYS }
    enum class AbsoluteUnit { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY,
        DAY, WEEK, MONTH, YEAR, NOW, QUARTER, HOUR, MINUTE }
    enum class Direction { LAST_2, LAST, THIS, NEXT, NEXT_2, PLAIN }
    enum class RelativeDateTimeUnit { YEAR, QUARTER, MONTH, WEEK, DAY, HOUR, MINUTE, SECOND,
        SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY }

    class Field private constructor(name: String?) : FormatField(name) {
        @Deprecated("This API is ICU internal only.")
        protected override fun readResolve(): Any = when (getName()!!) {
            LITERAL.getName() -> LITERAL
            NUMERIC.getName() -> NUMERIC
            else -> invalidFormatField()
        }
        companion object {
            private const val serialVersionUID = -5327685528663492325L
            @JvmField val LITERAL = Field("literal")
            @JvmField val NUMERIC = Field("numeric")
        }
    }
    class FormattedRelativeDateTime internal constructor(private val string: FormattedStringBuilder) : FormattedValue {
        override val length: Int get() = string.length
        override fun get(index: Int): Char = string[index]
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = string.subString(startIndex, endIndex)
        override fun toString(): String = string.toString()
        override fun <A : Appendable> appendTo(appendable: A): A = Utility.appendTo(string, appendable)
        override fun nextPosition(position: ConstrainedFieldPosition): Boolean =
            FormattedValueStringBuilderImpl.nextPosition(string, position, Field.NUMERIC)
        override fun toCharacterIterator(): FormatAttributedCharacterIterator =
            FormattedValueStringBuilderImpl.toCharacterIterator(string, Field.NUMERIC)
    }

    private val dateFormatSymbols: DateFormatSymbols
    private val numberLock = createCalendarCacheLock(numberFormat)
    private val breakLock = breakIterator?.let { createCalendarCacheLock(it) }
    init {
        if (capitalizationContext!!.type() != DisplayContext.Type.CAPITALIZATION)
            throw IllegalArgumentException(capitalizationContext.toString())
        dateFormatSymbols = DateFormatSymbols(locale)
    }

    fun format(quantity: Double, direction: Direction?, unit: RelativeUnit?): String =
        adjustForContext(formatImpl(quantity, direction, unit).toString())
    fun formatToValue(quantity: Double, direction: Direction?, unit: RelativeUnit?): FormattedRelativeDateTime {
        checkNoAdjustForContext()
        return FormattedRelativeDateTime(formatImpl(quantity, direction, unit))
    }
    private fun formatImpl(quantity: Double, direction: Direction?, unit: RelativeUnit?): FormattedStringBuilder {
        if (direction != Direction.LAST && direction != Direction.NEXT) throw IllegalArgumentException("direction must be NEXT or LAST")
        val pastFuture = if (direction == Direction.NEXT) 1 else 0
        val output = FormattedStringBuilder()
        val keyword = if (numberFormat is DecimalFormat) {
            val dq = DecimalQuantity_DualStorageBCD(quantity)
            numberFormat.toNumberFormatter().formatImpl(dq, output)
            pluralRules.select(dq)
        } else {
            output.append(numberFormat.format(quantity), null)
            pluralRules.select(quantity)
        }
        val plural = StandardPlural.orOtherFromString(keyword)
        val pattern = getRelativeUnitPluralPattern(style, unit, pastFuture, plural)
        SimpleFormatterImpl.formatPrefixSuffix(pattern!!, Field.LITERAL, 0, output.length, output)
        return output
    }

    fun formatNumeric(offset: Double, unit: RelativeDateTimeUnit?): String = adjustForContext(formatNumericImpl(offset, unit).toString())
    fun formatNumericToValue(offset: Double, unit: RelativeDateTimeUnit?): FormattedRelativeDateTime {
        checkNoAdjustForContext()
        return FormattedRelativeDateTime(formatNumericImpl(offset, unit))
    }
    private fun relativeUnit(unit: RelativeDateTimeUnit?): RelativeUnit = when (unit!!) {
        RelativeDateTimeUnit.YEAR -> RelativeUnit.YEARS
        RelativeDateTimeUnit.QUARTER -> RelativeUnit.QUARTERS
        RelativeDateTimeUnit.MONTH -> RelativeUnit.MONTHS
        RelativeDateTimeUnit.WEEK -> RelativeUnit.WEEKS
        RelativeDateTimeUnit.DAY -> RelativeUnit.DAYS
        RelativeDateTimeUnit.HOUR -> RelativeUnit.HOURS
        RelativeDateTimeUnit.MINUTE -> RelativeUnit.MINUTES
        RelativeDateTimeUnit.SECOND -> RelativeUnit.SECONDS
        RelativeDateTimeUnit.SUNDAY -> RelativeUnit.SUNDAYS
        RelativeDateTimeUnit.MONDAY -> RelativeUnit.MONDAYS
        RelativeDateTimeUnit.TUESDAY -> RelativeUnit.TUESDAYS
        RelativeDateTimeUnit.WEDNESDAY -> RelativeUnit.WEDNESDAYS
        RelativeDateTimeUnit.THURSDAY -> RelativeUnit.THURSDAYS
        RelativeDateTimeUnit.FRIDAY -> RelativeUnit.FRIDAYS
        RelativeDateTimeUnit.SATURDAY -> RelativeUnit.SATURDAYS
    }
    private fun formatNumericImpl(offset: Double, unit: RelativeDateTimeUnit?): FormattedStringBuilder {
        val relUnit = relativeUnit(unit)
        // compareTo, unlike '<', preserves the source distinction between -0.0 and +0.0.
        val past = offset.compareTo(0.0) < 0
        return formatImpl(if (past) -offset else offset, if (past) Direction.LAST else Direction.NEXT, relUnit)
    }

    fun format(direction: Direction?, unit: AbsoluteUnit?): String? = formatAbsoluteImpl(direction, unit)?.let(::adjustForContext)
    fun formatToValue(direction: Direction?, unit: AbsoluteUnit?): FormattedRelativeDateTime? {
        checkNoAdjustForContext()
        val text = formatAbsoluteImpl(direction, unit) ?: return null
        return FormattedRelativeDateTime(FormattedStringBuilder().also { it.append(text, Field.LITERAL) })
    }
    private fun formatAbsoluteImpl(direction: Direction?, unit: AbsoluteUnit?): String? {
        if (unit == AbsoluteUnit.NOW && direction != Direction.PLAIN) throw IllegalArgumentException("NOW can only accept direction PLAIN.")
        if (direction == Direction.PLAIN && unit!!.ordinal <= AbsoluteUnit.SATURDAY.ordinal) {
            val day = unit.ordinal - AbsoluteUnit.SUNDAY.ordinal + Calendar.SUNDAY
            val width = intArrayOf(DateFormatSymbols.WIDE, DateFormatSymbols.SHORT, DateFormatSymbols.NARROW)[style!!.ordinal]
            return dateFormatSymbols.getWeekdays(DateFormatSymbols.STANDALONE, width)[day]
        }
        return getAbsoluteUnitString(style, unit, direction)
    }

    fun format(offset: Double, unit: RelativeDateTimeUnit?): String = adjustForContext(formatRelativeImpl(offset, unit).toString())
    fun formatToValue(offset: Double, unit: RelativeDateTimeUnit?): FormattedRelativeDateTime {
        checkNoAdjustForContext()
        val result = formatRelativeImpl(offset, unit)
        return FormattedRelativeDateTime(if (result is FormattedStringBuilder) result
            else FormattedStringBuilder().also { it.append(result, Field.LITERAL) })
    }
    private fun formatRelativeImpl(offset: Double, unit: RelativeDateTimeUnit?): CharSequence {
        var useNumeric = true
        var direction = Direction.THIS
        if (offset > -2.1 && offset < 2.1) {
            val scaled = offset * 100.0
            val rounded = (if (scaled < 0) scaled - 0.5 else scaled + 0.5).toInt()
            when (rounded) {
                -200 -> { direction = Direction.LAST_2; useNumeric = false }
                -100 -> { direction = Direction.LAST; useNumeric = false }
                0 -> useNumeric = false
                100 -> { direction = Direction.NEXT; useNumeric = false }
                200 -> { direction = Direction.NEXT_2; useNumeric = false }
            }
        }
        val absolute = when (unit!!) {
            RelativeDateTimeUnit.SECOND -> {
                if (direction == Direction.THIS) direction = Direction.PLAIN else useNumeric = true
                AbsoluteUnit.NOW
            }
            else -> AbsoluteUnit.valueOf(unit.name)
        }
        if (!useNumeric) {
            val text = formatAbsoluteImpl(direction, absolute)
            if (!text.isNullOrEmpty()) return text
        }
        return formatNumericImpl(offset, unit)
    }
    private fun getAbsoluteUnitString(initialStyle: Style?, unit: AbsoluteUnit?, direction: Direction?): String? {
        var current = initialStyle
        do {
            qualitativeUnitMap[current]?.get(unit)?.get(direction)?.let { return it }
            current = fallbackCache[current!!.ordinal]
        } while (current != null)
        return null
    }
    private fun getRelativeUnitPluralPattern(style: Style?, unit: RelativeUnit?, index: Int, plural: StandardPlural): String? {
        if (plural != StandardPlural.OTHER) getRelativeUnitPattern(style, unit, index, plural)?.let { return it }
        return getRelativeUnitPattern(style, unit, index, StandardPlural.OTHER)
    }
    private fun getRelativeUnitPattern(initialStyle: Style?, unit: RelativeUnit?, index: Int, plural: StandardPlural): String? {
        var current = initialStyle
        do {
            patternMap[current]?.get(unit)?.get(index)?.get(plural.ordinal)?.let { return it }
            current = fallbackCache[current!!.ordinal]
        } while (current != null)
        return null
    }
    fun combineDateAndTime(relativeDateString: String?, timeString: String?): String =
        SimpleFormatterImpl.formatCompiledPattern(combinedDateAndTime, timeString, relativeDateString)
    fun getNumberFormat(): NumberFormat = numberLock.withLock { numberFormat.clone() as NumberFormat }
    fun getCapitalizationContext(): DisplayContext = capitalizationContext!!
    fun getFormatStyle(): Style? = style
    private fun adjustForContext(text: String): String {
        if (breakIterator == null || text.isEmpty() || !UCharacter.isLowerCase(UTF16.charAt(text, 0))) return text
        return breakLock!!.withLock {
            UCharacter.toTitleCase(locale, text, breakIterator, UCharacter.TITLECASE_NO_LOWERCASE or UCharacter.TITLECASE_NO_BREAK_ADJUSTMENT)
        }
    }
    private fun checkNoAdjustForContext() {
        if (breakIterator != null) throw UnsupportedOperationException("Capitalization context is not supported in formatV")
    }

    private class Data(
        val qualitative: Map<Style, Map<AbsoluteUnit, Map<Direction, String>>>,
        val patterns: Map<Style, Map<RelativeUnit, Array<Array<String?>>>>,
        val dateTimePattern: String?,
    )
    private enum class DateTimeUnit(val rel: RelativeUnit, val abs: AbsoluteUnit?) {
        SECOND(RelativeUnit.SECONDS, null), MINUTE(RelativeUnit.MINUTES, AbsoluteUnit.MINUTE),
        HOUR(RelativeUnit.HOURS, AbsoluteUnit.HOUR), DAY(RelativeUnit.DAYS, AbsoluteUnit.DAY),
        WEEK(RelativeUnit.WEEKS, AbsoluteUnit.WEEK), MONTH(RelativeUnit.MONTHS, AbsoluteUnit.MONTH),
        QUARTER(RelativeUnit.QUARTERS, AbsoluteUnit.QUARTER), YEAR(RelativeUnit.YEARS, AbsoluteUnit.YEAR),
        SUNDAY(RelativeUnit.SUNDAYS, AbsoluteUnit.SUNDAY), MONDAY(RelativeUnit.MONDAYS, AbsoluteUnit.MONDAY),
        TUESDAY(RelativeUnit.TUESDAYS, AbsoluteUnit.TUESDAY), WEDNESDAY(RelativeUnit.WEDNESDAYS, AbsoluteUnit.WEDNESDAY),
        THURSDAY(RelativeUnit.THURSDAYS, AbsoluteUnit.THURSDAY), FRIDAY(RelativeUnit.FRIDAYS, AbsoluteUnit.FRIDAY),
        SATURDAY(RelativeUnit.SATURDAYS, AbsoluteUnit.SATURDAY);
        companion object {
            fun fromKey(key: String): DateTimeUnit? = when (key) {
                "second" -> SECOND; "minute" -> MINUTE; "hour" -> HOUR; "day" -> DAY
                "week" -> WEEK; "month" -> MONTH; "quarter" -> QUARTER; "year" -> YEAR
                "sun" -> SUNDAY; "mon" -> MONDAY; "tue" -> TUESDAY; "wed" -> WEDNESDAY
                "thu" -> THURSDAY; "fri" -> FRIDAY; "sat" -> SATURDAY; else -> null
            }
        }
    }
    private class RelDateTimeDataSink : UResource.Sink() {
        val qualitative = HashMap<Style, MutableMap<AbsoluteUnit, MutableMap<Direction, String>>>()
        val patterns = HashMap<Style, MutableMap<RelativeUnit, Array<Array<String?>>>>()
        private val sb = StringBuilder()
        private var pastFuture = 0
        private lateinit var style: Style
        private lateinit var unit: DateTimeUnit
        private fun styleFromKey(key: CharSequence): Style = when {
            key.endsWith("-short") -> Style.SHORT
            key.endsWith("-narrow") -> Style.NARROW
            else -> Style.LONG
        }
        private fun suffixLength(style: Style) = when (style) { Style.SHORT -> 6; Style.NARROW -> 7; else -> 0 }
        private fun consumeRelative(key: UResource.Key, value: UResource.Value) {
            val table = value.getTable()
            var i = 0
            while (table.getKeyAndValue(i++, key, value)) {
                if (value.getType() != UResourceBundle.STRING) continue
                val text = value.getString()
                var absMap = qualitative[style]
                if (unit.rel == RelativeUnit.SECONDS && key.contentEquals("0")) {
                    val strings = absMap!!.getOrPut(AbsoluteUnit.NOW) { HashMap() }
                    if (strings[Direction.PLAIN] == null) strings[Direction.PLAIN] = text
                    continue
                }
                val direction = when (key.toString()) {
                    "-2" -> Direction.LAST_2; "-1" -> Direction.LAST; "0" -> Direction.THIS
                    "1" -> Direction.NEXT; "2" -> Direction.NEXT_2; else -> null
                } ?: continue
                val absolute = unit.abs ?: continue
                if (absMap == null) absMap = HashMap<AbsoluteUnit, MutableMap<Direction, String>>().also { qualitative[style] = it }
                val strings = absMap.getOrPut(absolute) { HashMap() }
                if (strings[direction] == null) strings[direction] = value.getString()
            }
        }
        private fun consumeRelativeTime(key: UResource.Key, value: UResource.Value) {
            val table = value.getTable()
            var i = 0
            while (table.getKeyAndValue(i++, key, value)) {
                pastFuture = when (key.toString()) { "past" -> 0; "future" -> 1; else -> continue }
                val detail = value.getTable()
                val unitPatterns = patterns.getOrPut(style) { HashMap() }
                val compiled = unitPatterns.getOrPut(unit.rel) { Array(2) { arrayOfNulls(StandardPlural.COUNT) } }
                var j = 0
                while (detail.getKeyAndValue(j++, key, value)) {
                    if (value.getType() == UResourceBundle.STRING) {
                        val pluralIndex = StandardPlural.indexFromString(key.toString())
                        if (compiled[pastFuture][pluralIndex] == null)
                            compiled[pastFuture][pluralIndex] = SimpleFormatterImpl.compileToStringMinMaxArguments(value.getString(), sb, 0, 1)
                    }
                }
            }
        }
        private fun consumeTimeUnit(key: UResource.Key, value: UResource.Value) {
            val table = value.getTable()
            var i = 0
            while (table.getKeyAndValue(i++, key, value)) {
                if (key.contentEquals("dn") && value.getType() == UResourceBundle.STRING) {
                    val absolute = unit.abs
                    if (absolute != null) {
                        val strings = qualitative.getOrPut(style) { HashMap() }.getOrPut(absolute) { HashMap() }
                        if (strings[Direction.PLAIN] == null) strings[Direction.PLAIN] = value.toString()
                    }
                }
                if (value.getType() == UResourceBundle.TABLE) {
                    if (key.contentEquals("relative")) consumeRelative(key, value)
                    else if (key.contentEquals("relativeTime")) consumeRelativeTime(key, value)
                }
            }
        }
        private fun handleAlias(key: UResource.Key, value: UResource.Value) {
            val source = styleFromKey(key)
            if (DateTimeUnit.fromKey(key.substring(0, key.length - suffixLength(source))) == null) return
            val target = styleFromKey(value.getAliasString())
            if (source == target) throw ICUException("Invalid style fallback from $source to itself")
            if (fallbackCache[source.ordinal] == null) fallbackCache[source.ordinal] = target
            else if (fallbackCache[source.ordinal] != target) throw ICUException("Inconsistent style fallback for style $source to $target")
        }
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            if (value.getType() == ICUResourceBundle.ALIAS) return
            val table = value.getTable()
            var i = 0
            while (table.getKeyAndValue(i++, key, value)) {
                if (value.getType() == ICUResourceBundle.ALIAS) handleAlias(key, value)
                else {
                    style = styleFromKey(key)
                    unit = DateTimeUnit.fromKey(key.substring(0, key.length - suffixLength(style))) ?: continue
                    consumeTimeUnit(key, value)
                }
            }
        }
    }
    companion object {
        private val fallbackCache = arrayOfNulls<Style>(3)
        private val cache = object : SoftCache<String, Data, ULocale>() {
            override fun createInstance(key: String, data: ULocale): Data = loadData(data)
        }
        private fun data(locale: ULocale): Data = cache.getInstance(locale.toString(), locale)!!
        private fun loadData(locale: ULocale): Data {
            val sink = RelDateTimeDataSink()
            val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
            bundle.getAllItemsWithFallback("fields", sink)
            for (style in Style.entries) {
                val next = fallbackCache[style.ordinal]
                if (next != null) {
                    val last = fallbackCache[next.ordinal]
                    if (last != null && fallbackCache[last.ordinal] != null) throw IllegalStateException("Style fallback too deep")
                }
            }
            return Data(sink.qualitative, sink.patterns, Calendar.getDateAtTimePattern(Calendar.getInstance(locale), locale, DateFormat.MEDIUM))
        }
        @JvmStatic fun getInstance(): RelativeDateTimeFormatter = getInstance(ULocale.getDefault(), null, Style.LONG, DisplayContext.CAPITALIZATION_NONE)
        @JvmStatic fun getInstance(locale: ULocale?): RelativeDateTimeFormatter = getInstance(locale, null, Style.LONG, DisplayContext.CAPITALIZATION_NONE)
        @JvmStatic fun getInstance(locale: ULocale?, nf: NumberFormat?): RelativeDateTimeFormatter = getInstance(locale, nf, Style.LONG, DisplayContext.CAPITALIZATION_NONE)
        @JvmStatic fun getInstance(locale: ULocale?, nf: NumberFormat?, style: Style?, capitalizationContext: DisplayContext?): RelativeDateTimeFormatter {
            val data = data(locale!!)
            val format = if (nf == null) NumberFormat.getInstance(locale) else nf.clone() as NumberFormat
            return RelativeDateTimeFormatter(data.qualitative, data.patterns,
                SimpleFormatterImpl.compileToStringMinMaxArguments(data.dateTimePattern, StringBuilder(), 2, 2),
                PluralRules.forLocale(locale), format, style, capitalizationContext,
                if (capitalizationContext == DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE) BreakIterator.getSentenceInstance(locale) else null, locale)
        }
    }
}
