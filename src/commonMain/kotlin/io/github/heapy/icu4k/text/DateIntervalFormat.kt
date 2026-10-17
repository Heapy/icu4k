// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2008-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.impl.locale.localeReadOnlyMap
import io.github.heapy.icu4k.text.DateIntervalInfo.PatternInfo
import io.github.heapy.icu4k.util.*

/** CLDR-driven compact date intervals with endpoint spans and date field positions. */
open class DateIntervalFormat : UFormat {
    class FormattedDateInterval internal constructor(text: CharSequence, private val attributes: List<FormatFieldPosition>) : FormattedValue {
        private val string = text.toString()
        override val length: Int get() = string.length
        override fun get(index: Int): Char = dateIntervalCharAt(string, index)
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = dateIntervalSubSequence(string, startIndex, endIndex)
        override fun toString(): String = string
        override fun <A : Appendable> appendTo(appendable: A): A = Utility.appendTo(string, appendable)
        override fun nextPosition(position: ConstrainedFieldPosition): Boolean = FormattedValueFieldPositionIteratorImpl.nextPosition(attributes, position)
        override fun toCharacterIterator(): FormatAttributedCharacterIterator = FormattedValueFieldPositionIteratorImpl.toCharacterIterator(string, attributes)
    }
    class SpanField private constructor(name: String) : FormatSpanField(name) {
        protected override fun readResolve(): Any = if (getName()!!.equals(DATE_INTERVAL_SPAN.getName())) DATE_INTERVAL_SPAN else invalidFormatField()
        companion object {
            private const val serialVersionUID = -6330879259553618133L
            val DATE_INTERVAL_SPAN = SpanField("date-interval-span")
        }
    }
    private class FormatOutput {
        var firstIndex = -1
        fun register(index: Int) { if (firstIndex == -1) firstIndex = index }
    }
    private var fInfo: DateIntervalInfo? = null
    private var fDateFormat: SimpleDateFormat? = null
    private var fFromCalendar: Calendar? = null
    private var fToCalendar: Calendar? = null
    private var fSkeleton: String? = null
    private var isDateIntervalInfoDefault = false
    @kotlin.jvm.Transient private var fIntervalPatterns: Map<String, PatternInfo>? = null
    private var fDatePattern: String? = null
    private var fTimePattern: String? = null
    private var fDateTimeFormat: String? = null
    private var fCapitalizationSetting: DisplayContext? = DisplayContext.CAPITALIZATION_NONE
    @kotlin.jvm.Transient private var formatLock = createCalendarCacheLock(this)

    @Deprecated("This API is ICU internal only.")
    constructor(skeleton: String?, info: DateIntervalInfo?, dateFormat: SimpleDateFormat?) : super() {
        fDateFormat = dateFormat
        info!!.freeze()
        fSkeleton = skeleton; fInfo = info
        fFromCalendar = fDateFormat!!.getCalendar()!!.clone()
        fToCalendar = fDateFormat!!.getCalendar()!!.clone()
        initializePattern(null)
    }
    private constructor(skeleton: String?, locale: ULocale?, dateFormat: SimpleDateFormat) : super() {
        fDateFormat = dateFormat; fSkeleton = skeleton
        fInfo = DateIntervalInfo(locale).freeze(); isDateIntervalInfoDefault = true
        fFromCalendar = fDateFormat!!.getCalendar()!!.clone()
        fToCalendar = fDateFormat!!.getCalendar()!!.clone()
        initializePattern(LOCAL_PATTERN_CACHE)
    }
    protected constructor(other: DateIntervalFormat, @Suppress("UNUSED_PARAMETER") copyMarker: Unit) : super() {
        other.copyLocaleTo(this)
        fInfo = other.fInfo; fDateFormat = other.fDateFormat
        fFromCalendar = other.fFromCalendar; fToCalendar = other.fToCalendar
        fSkeleton = other.fSkeleton; isDateIntervalInfoDefault = other.isDateIntervalInfoDefault
        fIntervalPatterns = other.fIntervalPatterns
        fDatePattern = other.fDatePattern; fTimePattern = other.fTimePattern; fDateTimeFormat = other.fDateTimeFormat
        fCapitalizationSetting = other.fCapitalizationSetting
    }
    public override fun clone(): DateIntervalFormat = formatLock.withLock {
        val copy = (cloneHost() ?: cloneNative()) as DateIntervalFormat
        copy.formatLock = createCalendarCacheLock(copy)
        copy.fDateFormat = fDateFormat!!.clone()
        copy.fInfo = fInfo!!.clone()
        copy.fFromCalendar = fFromCalendar!!.clone()
        copy.fToCalendar = fToCalendar!!.clone()
        copy.fDatePattern = fDatePattern; copy.fTimePattern = fTimePattern; copy.fDateTimeFormat = fDateTimeFormat
        copy.fCapitalizationSetting = fCapitalizationSetting
        copy
    }
    protected open fun cloneNative(): DateIntervalFormat = DateIntervalFormat(this, Unit)
    final override fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        if (input is DateInterval) return format(input, output, position)
        throw IllegalArgumentException("Cannot format given Object (" + dateFormatClassName(input!!) + ") as a DateInterval")
    }
    fun format(interval: DateInterval?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = formatLock.withLock {
        fFromCalendar!!.setTimeInMillis(interval!!.getFromDate())
        fToCalendar!!.setTimeInMillis(interval.getToDate())
        formatImpl(fFromCalendar, fToCalendar, output, position, null, null)
    }
    open fun formatToValue(interval: DateInterval?): FormattedDateInterval = value { output, position, result, attributes -> formatLock.withLock {
        fFromCalendar!!.setTimeInMillis(interval!!.getFromDate())
        fToCalendar!!.setTimeInMillis(interval.getToDate())
        formatImpl(fFromCalendar, fToCalendar, output, position, result, attributes)
    } }
    fun format(from: Calendar?, to: Calendar?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = formatImpl(from, to, output, position, null, null)
    open fun formatToValue(from: Calendar?, to: Calendar?): FormattedDateInterval = value { output, position, result, attributes -> formatImpl(from, to, output, position, result, attributes) }
    private inline fun value(format: (StringBuilder, FormatFieldPosition, FormatOutput, MutableList<FormatFieldPosition>) -> Unit): FormattedDateInterval {
        val output = StringBuilder(); val position = FormatFieldPosition(0)
        val result = FormatOutput(); val attributes = ArrayList<FormatFieldPosition>()
        format(output, position, result, attributes)
        if (result.firstIndex != -1) {
            FormattedValueFieldPositionIteratorImpl.addOverlapSpans(attributes, SpanField.DATE_INTERVAL_SPAN, result.firstIndex)
            FormattedValueFieldPositionIteratorImpl.sort(attributes)
        }
        return FormattedDateInterval(output, attributes)
    }
    @Deprecated("This API is ICU internal only.")
    open fun getPatterns(from: Calendar?, to: Calendar?, part2: Output<String>?): String? {
        val field = largestDifferentField(from!!, to)
        if (field == -1) return null
        val pattern = fIntervalPatterns!![letter(field)]!!
        part2!!.value = pattern.getSecondPart()
        return pattern.getFirstPart()
    }
    private fun formatImpl(from: Calendar?, to: Calendar?, output: StringBuilder?, position: FormatFieldPosition?, result: FormatOutput?, attributes: MutableList<FormatFieldPosition>?): StringBuilder = formatLock.withLock {
        if (!from!!.isEquivalentTo(to)) throw IllegalArgumentException("can not format on two different calendars")
        fDateFormat!!.setContext(fCapitalizationSetting)
        val field = largestDifferentField(from, to)
        if (field == -1) return@withLock fDateFormat!!.format(from, output, position, attributes)
        val sameDay = field == Calendar.AM_PM || field == Calendar.HOUR || field == Calendar.MINUTE || field == Calendar.SECOND || field == Calendar.MILLISECOND
        val pattern = fIntervalPatterns!![letter(field)]
        if (pattern == null) {
            if (fDateFormat!!.isFieldUnitIgnored(field)) return@withLock fDateFormat!!.format(from, output, position, attributes)
            return@withLock fallbackFormat(from, to, sameDay, output, position, result, attributes)
        }
        if (pattern.getFirstPart() == null) {
            val original = fDateFormat!!.toPattern()
            fDateFormat!!.applyPattern(pattern.getSecondPart())
            fallbackFormat(from, to, sameDay, output, position, result, attributes)
            fDateFormat!!.applyPattern(original)
            return@withLock output!!
        }
        val reversed = pattern.firstDateInPtnIsLaterDate()
        result?.register(if (reversed) 1 else 0)
        val first = if (reversed) to else from
        val second = if (reversed) from else to
        val original = fDateFormat!!.toPattern()
        fDateFormat!!.applyPattern(pattern.getFirstPart())
        fDateFormat!!.format(first, output, position, attributes)
        var pos = position!!
        if (pos.getEndIndex() > 0) pos = FormatFieldPosition(0)
        if (pattern.getSecondPart() != null) {
            fDateFormat!!.applyPattern(pattern.getSecondPart())
            fDateFormat!!.setContext(DisplayContext.CAPITALIZATION_NONE)
            fDateFormat!!.format(second, output, pos, attributes)
        }
        fDateFormat!!.applyPattern(original)
        output!!
    }
    private fun fallbackRange(from: Calendar?, to: Calendar?, output: StringBuilder?, position: FormatFieldPosition?, result: FormatOutput?, attributes: MutableList<FormatFieldPosition>?, scratch: StringBuilder) {
        val compiled = SimpleFormatterImpl.compileToStringMinMaxArguments(fInfo!!.getFallbackIntervalPattern(), scratch, 2, 2)
        var state = 0L; var pos = position
        while (true) {
            state = SimpleFormatterImpl.IterInternal.step(state, compiled, output!!)
            if (state == SimpleFormatterImpl.IterInternal.DONE) break
            if (SimpleFormatterImpl.IterInternal.getArgIndex(state) == 0) {
                result?.register(0); fDateFormat!!.format(from, output, pos, attributes)
            } else {
                result?.register(1); fDateFormat!!.format(to, output, pos, attributes)
            }
            if (pos!!.getEndIndex() > 0) pos = FormatFieldPosition(0)
            fDateFormat!!.setContext(DisplayContext.CAPITALIZATION_NONE)
        }
    }
    private fun fallbackFormat(from: Calendar?, to: Calendar?, sameDay: Boolean, output: StringBuilder?, position: FormatFieldPosition?, result: FormatOutput?, attributes: MutableList<FormatFieldPosition>?): StringBuilder {
        val scratch = StringBuilder()
        if (sameDay && fDatePattern != null && fTimePattern != null) {
            val compiled = SimpleFormatterImpl.compileToStringMinMaxArguments(fDateTimeFormat!!, scratch, 2, 2)
            val original = fDateFormat!!.toPattern()
            var state = 0L; var pos = position
            while (true) {
                state = SimpleFormatterImpl.IterInternal.step(state, compiled, output!!)
                if (state == SimpleFormatterImpl.IterInternal.DONE) break
                if (SimpleFormatterImpl.IterInternal.getArgIndex(state) == 0) {
                    fDateFormat!!.applyPattern(fTimePattern)
                    fallbackRange(from, to, output, pos, result, attributes, scratch)
                } else {
                    fDateFormat!!.applyPattern(fDatePattern)
                    fDateFormat!!.format(from, output, pos, attributes)
                }
                if (pos!!.getEndIndex() > 0) pos = FormatFieldPosition(0)
                fDateFormat!!.setContext(DisplayContext.CAPITALIZATION_NONE)
            }
            fDateFormat!!.applyPattern(original)
        } else fallbackRange(from, to, output, position, result, attributes, scratch)
        return output!!
    }
    override fun parseObject(source: String?, position: ParsePosition?): Any? = throw UnsupportedOperationException("parsing is not supported")
    open fun getDateIntervalInfo(): DateIntervalInfo = fInfo!!.clone()
    open fun setDateIntervalInfo(info: DateIntervalInfo?) {
        fInfo = info!!.clone(); isDateIntervalInfoDefault = false
        fInfo!!.freeze()
        if (fDateFormat != null) initializePattern(null)
    }
    open fun getTimeZone(): TimeZone = if (fDateFormat != null) fDateFormat!!.getTimeZone()!!.clone() else TimeZone.getDefault()
    open fun setTimeZone(zone: TimeZone?) {
        val copy = zone!!.clone()
        fDateFormat?.setTimeZone(copy); fFromCalendar?.setTimeZone(copy); fToCalendar?.setTimeZone(copy)
    }
    open fun setContext(context: DisplayContext?) {
        if (context!!.type() == DisplayContext.Type.CAPITALIZATION) fCapitalizationSetting = context
    }
    open fun getContext(type: DisplayContext.Type?): DisplayContext = if (type == DisplayContext.Type.CAPITALIZATION && fCapitalizationSetting != null) fCapitalizationSetting!! else DisplayContext.CAPITALIZATION_NONE
    open fun getDateFormat(): DateFormat = formatLock.withLock { fDateFormat!!.clone() }
    @Deprecated("This API is ICU internal only.")
    open fun getRawPatterns(): Map<String, PatternInfo>? = fIntervalPatterns
    private fun initializePattern(cache: SimpleCache<String, Map<String, PatternInfo>>?) {
        val full = fDateFormat!!.toPattern()
        val locale = fDateFormat!!.getLocale()
        val key = if (fSkeleton != null) "$locale+$full+$fSkeleton" else "$locale+$full"
        var patterns = cache?.get(key)
        if (patterns == null) {
            val owner = object : DateIntervalPatternCore.Owner {
                override val info get() = fInfo!!
                override val fullPattern get() = fDateFormat!!.toPattern()
                override val locale = locale
                override var skeleton: String?
                    get() = fSkeleton
                    set(value) { fSkeleton = value }
                override var datePattern: String?
                    get() = fDatePattern
                    set(value) { fDatePattern = value }
                override var timePattern: String?
                    get() = fTimePattern
                    set(value) { fTimePattern = value }
                override var dateTimeFormat: String?
                    get() = fDateTimeFormat
                    set(value) { fDateTimeFormat = value }
            }
            patterns = localeReadOnlyMap(DateIntervalPatternCore(owner).initialize(full))
            cache?.put(key, patterns)
        }
        fIntervalPatterns = patterns
    }
    private fun readObject(input: PluralObjectInputStream) {
        dateIntervalFormatRead(input)
        formatLock = createCalendarCacheLock(this)
        initializePattern(if (isDateIntervalInfoDefault) LOCAL_PATTERN_CACHE else null)
        if (fCapitalizationSetting == null) fCapitalizationSetting = DisplayContext.CAPITALIZATION_NONE
    }
    companion object {
        private const val serialVersionUID = 1L
        private val LOCAL_PATTERN_CACHE = SimpleCache<String, Map<String, PatternInfo>>()
        private fun letter(field: Int): String = DateIntervalInfo.CALENDAR_FIELD_TO_PATTERN_LETTER[field]
        private fun largestDifferentField(from: Calendar, to: Calendar?): Int {
            for (field in intArrayOf(Calendar.ERA, Calendar.YEAR, Calendar.MONTH, Calendar.DATE, Calendar.AM_PM, Calendar.HOUR, Calendar.MINUTE, Calendar.SECOND, Calendar.MILLISECOND)) if (from.get(field) != to!!.get(field)) return field
            return -1
        }
        fun getInstance(skeleton: String?): DateIntervalFormat = getInstance(skeleton, ULocale.getDefault(ULocale.Category.FORMAT))
        fun getInstance(skeleton: String?, locale: ULocale?): DateIntervalFormat {
            val generator = DateTimePatternGenerator.getInstance(locale)
            return DateIntervalFormat(skeleton, locale, SimpleDateFormat(generator.getBestPattern(skeleton), locale))
        }
        fun getInstance(skeleton: String?, info: DateIntervalInfo?): DateIntervalFormat = getInstance(skeleton, ULocale.getDefault(ULocale.Category.FORMAT), info)
        @Suppress("DEPRECATION")
        fun getInstance(skeleton: String?, locale: ULocale?, info: DateIntervalInfo?): DateIntervalFormat {
            val copy = info!!.clone()
            val generator = DateTimePatternGenerator.getInstance(locale)
            return DateIntervalFormat(skeleton, copy, SimpleDateFormat(generator.getBestPattern(skeleton), locale))
        }
    }
}
internal expect fun dateIntervalFormatRead(input: PluralObjectInputStream)
internal expect fun dateIntervalCharAt(text: String, index: Int): Char
internal expect fun dateIntervalSubSequence(text: String, start: Int, end: Int): CharSequence
