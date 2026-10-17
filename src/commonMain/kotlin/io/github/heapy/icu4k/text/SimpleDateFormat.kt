// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.impl.calendar.*
import io.github.heapy.icu4k.impl.number.LocalizedNumberFormatterAsFormat
import io.github.heapy.icu4k.util.*
import kotlin.time.Clock

/** Locale-sensitive date patterns, formatting, parsing and calendar-specific symbols. */
open class SimpleDateFormat : DateFormat {
    @kotlin.jvm.Transient internal var state = newState()
    private fun newState() = SimpleDateFormatStateCore(object : SimpleDateFormatStateCore.Owner {
        override var calendar: Calendar?
            get() = this@SimpleDateFormat.calendar
            set(value) { this@SimpleDateFormat.calendar = value }
        override var numberFormat: NumberFormat?
            get() = this@SimpleDateFormat.numberFormat
            set(value) { this@SimpleDateFormat.numberFormat = value }
        override fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
        override fun setLocale(valid: ULocale?, actual: ULocale?) { this@SimpleDateFormat.setLocale(valid, actual) }
        override fun setNumberFormatVirtual(format: NumberFormat?) = this@SimpleDateFormat.setNumberFormat(format)
    })
    @kotlin.jvm.Transient private var engines = Engines()
    private inner class Engines {
        val formatter: SimpleDateFormatFormatCore = SimpleDateFormatFormatCore(object : SimpleDateFormatFormatCore.Owner {
            override val calendar get() = this@SimpleDateFormat.calendar
            override val pattern get() = state.pattern
            override val locale get() = state.locale
            override val symbols get() = state.symbols
            override val numberFormat get() = this@SimpleDateFormat.numberFormat
            override val numberingOverride get() = state.numberingOverride
            override val hasMinute get() = state.flags.hasMinute
            override val hasSecond get() = state.flags.hasSecond
            override val useFastFormat get() = state.useFastFormat
            override fun compiledPatternItems(): Array<Any> = state.compiledItems()
            override fun capitalizationContext(): DisplayContext = getContext(DisplayContext.Type.CAPITALIZATION)
            override fun numberFormat(ch: Char): NumberFormat? = this@SimpleDateFormat.getNumberFormat(ch)
            override fun timeZoneFormat(): TimeZoneFormat = state.timeZoneFormat()
            override fun zeroPaddingNumber(nf: NumberFormat?, buf: StringBuilder?, value: Int, min: Int, max: Int) = this@SimpleDateFormat.zeroPaddingNumber(nf,buf,value,min,max)
            override fun subFormat(buf: StringBuilder?, ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?) = this@SimpleDateFormat.subFormat(buf,ch,count,begin,fieldNum,context,pos,outputChar,cal)
            override fun subFormat(ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?): String? = this@SimpleDateFormat.subFormat(ch,count,begin,fieldNum,context,pos,outputChar,cal)
            override fun patternCharToDateFormatField(ch: Char): DateFormat.Field? = this@SimpleDateFormat.patternCharToDateFormatField(ch)
        })
        val fields: SimpleDateFormatFieldParseCore = SimpleDateFormatFieldParseCore(object : SimpleDateFormatFieldParseCore.Owner {
            override val symbols get() = state.symbols
            override val numberingOverride get() = state.numberingOverride
            override fun numberFormat(ch: Char): NumberFormat? = getNumberFormat(ch)
            override fun timeZoneFormat(): TimeZoneFormat = state.timeZoneFormat()
            override fun booleanAttribute(key: BooleanAttribute): Boolean = getBooleanAttribute(key)
            override fun isLenient(): Boolean = this@SimpleDateFormat.isLenient()
            override fun defaultCenturyStartYear(): Int = state.centuryStartYear()
            override fun subParse(text: String, start: Int, ch: Char, count: Int, obeyCount: Boolean, allowNegative: Boolean, ambiguousYear: BooleanArray, cal: Calendar, numericLeapMonth: SimpleDateFormatParseCore.NumericLeapMonth?, timeType: Output<TimeZoneFormat.TimeType>?, dayPeriod: Output<DayPeriodRules.DayPeriod>?): Int = fields.subParse(text,start,ch,count,obeyCount,allowNegative,ambiguousYear,cal,numericLeapMonth,timeType,dayPeriod)
            override fun matchQuarterString(text: String, start: Int, field: Int, data: Array<String?>, cal: Calendar): Int = this@SimpleDateFormat.matchQuarterString(text,start,field,data,cal)
        })
        val parser = SimpleDateFormatParseCore(object : SimpleDateFormatParseCore.Owner {
            override val calendar get() = this@SimpleDateFormat.calendar
            override val pattern get() = state.pattern
            override val locale get() = state.locale
            override val symbols get() = state.symbols
            override val assertionsEnabled get() = simpleDateFormatAssertionsEnabled()
            override fun compiledPatternItems(): Array<Any> = state.compiledItems()
            override fun booleanAttribute(key: BooleanAttribute): Boolean = getBooleanAttribute(key)
            override fun defaultCenturyStart(): Long = state.centuryStart()!!.getTime()
            override fun defaultCenturyStartYear(): Int = state.centuryStartYear()
            override fun subParse(text: String, start: Int, ch: Char, count: Int, obeyCount: Boolean, allowNegative: Boolean, ambiguousYear: BooleanArray, cal: Calendar, numericLeapMonth: SimpleDateFormatParseCore.NumericLeapMonth?, timeType: Output<TimeZoneFormat.TimeType>?, dayPeriod: Output<DayPeriodRules.DayPeriod>?): Int = fields.subParse(text,start,ch,count,obeyCount,allowNegative,ambiguousYear,cal,numericLeapMonth,timeType,dayPeriod)
            override fun numericLeapMonthParser(pattern: String): SimpleDateFormatParseCore.NumericLeapMonth = this@SimpleDateFormat.numericLeapMonthParser(pattern)
        })
        val interval = SimpleDateFormatIntervalCore(object : SimpleDateFormatIntervalCore.Owner {
            override val pattern get() = state.pattern
            override val useFastFormat get() = state.useFastFormat
            override fun compiledPatternItems(): Array<Any> = state.compiledItems()
            override fun capitalizationContext(): DisplayContext = getContext(DisplayContext.Type.CAPITALIZATION)
            override fun format(cal: Calendar?, output: StringBuilder?, pos: FormatFieldPosition?): StringBuilder = this@SimpleDateFormat.format(cal,output,pos)
            override fun subFormat(buf: StringBuilder?, ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?) = this@SimpleDateFormat.subFormat(buf,ch,count,begin,fieldNum,context,pos,outputChar,cal)
            override fun subFormat(ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?): String? = this@SimpleDateFormat.subFormat(ch,count,begin,fieldNum,context,pos,outputChar,cal)
        })
    }
    private constructor(pattern: String?, symbols: DateFormatSymbols?, calendar: Calendar?, number: NumberFormat?, locale: ULocale?, fast: Boolean, override: String?) : super() {
        state.pattern = pattern; state.symbols = symbols; this.calendar = calendar
        numberFormat = number; state.locale = locale; state.useFastFormat = fast
        state.numberingOverride = override
        state.initialize()
    }
    constructor() : this(defaultPatterns.getPattern(), null, null, null, null, true, null)
    constructor(pattern: String?) : this(pattern, null, null, null, null, true, null)
    constructor(pattern: String?, locale: ULocale?) : this(pattern, null, null, null, locale, true, null)
    constructor(pattern: String?, override: String?, locale: ULocale?) : this(pattern, null, null, null, locale, false, override)
    constructor(pattern: String?, symbols: DateFormatSymbols?) : this(pattern, symbols!!.clone(), null, null, null, true, null)
    @Deprecated("This API is ICU internal only.")
    constructor(pattern: String?, symbols: DateFormatSymbols?, locale: ULocale?) : this(pattern, symbols!!.clone(), null, null, locale, true, null)
    internal constructor(pattern: String?, symbols: DateFormatSymbols?, calendar: Calendar?, locale: ULocale?, fast: Boolean, override: String?) : this(pattern, symbols!!.clone(), calendar!!.clone(), null, locale, fast, override)
    protected constructor(other: SimpleDateFormat, copyMarker: Unit) : super(other, copyMarker)

    open fun set2DigitYearStart(date: Date?) = state.setCenturyStart(date)
    open fun get2DigitYearStart(): Date? = state.centuryStart()
    override fun format(calendar: Calendar?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = engines.formatter.format(calendar, output, position)!!
    override fun parse(text: String?, calendar: Calendar?, position: ParsePosition?) = engines.parser.parse(text, calendar, position)
    protected open fun patternCharToDateFormatField(ch: Char): DateFormat.Field? = SimpleDateFormatPatternCore.attribute(ch)
    protected open fun subFormat(ch: Char, count: Int, beginOffset: Int, pos: FormatFieldPosition?, @Suppress("UNUSED_PARAMETER") symbols: DateFormatSymbols?, cal: Calendar?): String? =
        subFormat(ch, count, beginOffset, 0, DisplayContext.CAPITALIZATION_NONE, pos, ch, cal)
    @Deprecated("This API is ICU internal only.")
    protected open fun subFormat(ch: Char, count: Int, beginOffset: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?): String? =
        engines.formatter.formatFieldString(ch, count, beginOffset, fieldNum, context, pos, outputChar, cal)
    @Deprecated("This API is ICU internal only.")
    protected open fun subFormat(buf: StringBuilder?, ch: Char, count: Int, beginOffset: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?) =
        engines.formatter.formatField(buf, ch, count, beginOffset, fieldNum, context, pos, outputChar, cal)
    @Deprecated("This API is ICU internal only.")
    protected open fun zeroPaddingNumber(nf: NumberFormat?, buf: StringBuilder?, value: Int, minDigits: Int, maxDigits: Int) = state.numbers.format(nf, buf, value, minDigits, maxDigits)
    protected open fun zeroPaddingNumber(value: Long, minDigits: Int, maxDigits: Int): String {
        numberFormat!!.setMinimumIntegerDigits(minDigits)
        numberFormat!!.setMaximumIntegerDigits(maxDigits)
        return numberFormat!!.format(value)
    }
    protected open fun matchString(text: String, start: Int, field: Int, data: Array<String?>, cal: Calendar): Int = engines.fields.matchString(text, start, field, data, null, cal)
    protected open fun matchQuarterString(text: String, start: Int, field: Int, data: Array<String?>, cal: Calendar): Int = engines.fields.matchQuarterString(text, start, field, data, cal)
    protected open fun subParse(text: String, start: Int, ch: Char, count: Int, obeyCount: Boolean, allowNegative: Boolean, ambiguousYear: BooleanArray, cal: Calendar): Int =
        engines.fields.subParse(text, start, ch, count, obeyCount, allowNegative, ambiguousYear, cal, null, null, null)
    open fun toPattern(): String? = state.pattern
    open fun toLocalizedPattern(): String = state.toLocalizedPattern()
    open fun applyPattern(pattern: String?) = state.applyPattern(pattern)
    open fun applyLocalizedPattern(pattern: String?) = state.applyLocalizedPattern(pattern)
    open fun getDateFormatSymbols(): DateFormatSymbols = state.getSymbolsCopy()
    open fun setDateFormatSymbols(symbols: DateFormatSymbols?) = state.replaceSymbols(symbols)
    protected open fun getSymbols(): DateFormatSymbols? = state.symbols
    open fun getTimeZoneFormat(): TimeZoneFormat = state.getFrozenTimeZoneFormat()
    open fun setTimeZoneFormat(format: TimeZoneFormat?) = state.setTimeZoneFormat(format)
    override fun setNumberFormat(value: NumberFormat?) { super.setNumberFormat(value); state.afterNumberFormatSet() }
    open fun setNumberFormat(fields: String?, value: NumberFormat?) = state.setFieldsNumberFormat(fields, value)
    open fun getNumberFormat(field: Char): NumberFormat? = state.numberFormat(field)
    override fun setContext(context: DisplayContext?) {
        super.setContext(context)
        if (engines.formatter.capitalizationBrkIter == null && context in listOf(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE, DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU, DisplayContext.CAPITALIZATION_FOR_STANDALONE)) engines.formatter.capitalizationBrkIter = BreakIterator.getSentenceInstance(state.locale)
    }
    public override fun clone(): SimpleDateFormat {
        val copy = super.clone() as SimpleDateFormat
        copy.state = copy.newState()
        copy.state.copyStateFrom(state)
        copy.engines = copy.Engines()
        copy.engines.formatter.copyStateFrom(engines.formatter)
        copy.engines.parser.copyStateFrom(engines.parser)
        return copy
    }
    protected override fun cloneNative(): DateFormat = SimpleDateFormat(this, Unit)
    override fun hashCode(): Int = state.pattern!!.hashCode()
    override fun equals(other: Any?): Boolean = super.equals(other) && state.pattern!!.equals((other as SimpleDateFormat).state.pattern) && state.symbols!!.equals(other.state.symbols)
    override fun formatToCharacterIterator(input: Any?): FormatAttributedCharacterIterator {
        var cal = calendar
        when (input) {
            is Calendar -> cal = input
            is Date -> calendar!!.setTime(input)
            is Number -> calendar!!.setTimeInMillis(input.toLong())
            else -> {
                val converted = input?.let { dateFormatCalendarInput(it) }
                    ?: throw IllegalArgumentException("Cannot format given Object as a Date")
                // Source replaces the stored Java calendar but formats the previous local one.
                if (simpleDateFormatJavaCalendar(input)) calendar = converted else cal = converted
            }
        }
        val positions = ArrayList<FormatFieldPosition>()
        val output = StringBuilder()
        engines.formatter.format(cal, getContext(DisplayContext.Type.CAPITALIZATION), output, FormatFieldPosition(0), positions)
        return attributedCharacterIterator(output.toString(), positions.map {
            val field = it.getFieldAttribute()!!
            FormatAttributeRange(field, field, it.getBeginIndex(), it.getEndIndex())
        })
    }
    @Deprecated("This API is ICU internal only.")
    fun intervalFormatByAlgorithm(from: Calendar?, to: Calendar?, output: StringBuilder?, pos: FormatFieldPosition?): StringBuilder = engines.interval.format(from, to, output, pos)!!
    internal fun getLocale(): ULocale? = state.locale
    internal fun isFieldUnitIgnored(field: Int): Boolean = SimpleDateFormatPatternCore.isFieldUnitIgnored(state.pattern!!, field)

    private fun numericLeapMonthParser(pattern: String): SimpleDateFormatParseCore.NumericLeapMonth {
        val styles = MessageFormatStyleCore(object : MessageFormatStyleCore.Owner {
            override val locale get() = state.locale
            override fun dateStyle(date: Boolean, style: Int, locale: ULocale?): Any = normalDateStyle(date, style, locale)
            override fun datePattern(pattern: String, locale: ULocale?): Any = SimpleDateFormat(pattern, locale)
            override fun dateSkeleton(skeleton: String, locale: ULocale?): Any = SimpleDateFormat(bestPattern(skeleton, locale), locale)
        })
        return SimpleDateFormatNumericLeapMonthCore(pattern) { type, style ->
            when (val format = styles.create(type, style)) {
                null -> null
                is UFormatBase -> MessageFormatParseCore.Parser { text, pos -> format.parseObject(text, pos) }
                is LocalizedNumberFormatterAsFormat -> MessageFormatParseCore.Parser { text, pos -> format.parseObject(text, pos) }
                else -> error("Unexpected stock format: $format")
            }
        }
    }
    private fun writeObject(output: PluralObjectOutputStream) {
        state.centuryStart()
        state.initializeTimeZoneFormat(false)
        simpleDateFormatWrite(output, this)
    }
    private fun readObject(input: PluralObjectInputStream) {
        state = newState()
        engines = Engines()
        simpleDateFormatRead(input, this)
    }
    internal fun restoreAfterRead(capitalization: Int) {
        if (state.serialVersionOnStream < 1) state.defaultCenturyBase = Clock.System.now().toEpochMilliseconds()
        else state.setCenturyStart(state.defaultCenturyStart)
        state.serialVersionOnStream = 2
        state.locale = getLocale(ULocale.VALID_LOCALE) ?: ULocale.getDefault(ULocale.Category.FORMAT)
        state.numbers.initialize(numberFormat)
        setContext(DisplayContext.CAPITALIZATION_NONE)
        if (capitalization >= 0) DisplayContext.entries.firstOrNull { it.value() == capitalization }?.let { setContext(it) }
        if (!getBooleanAttribute(BooleanAttribute.PARSE_PARTIAL_MATCH)) setBooleanAttribute(BooleanAttribute.PARSE_PARTIAL_LITERAL_MATCH, false)
        state.parsePattern()
    }
    private enum class ContextValue { UNKNOWN, CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE, CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE, CAPITALIZATION_FOR_UI_LIST_OR_MENU, CAPITALIZATION_FOR_STANDALONE }
    companion object {
        private const val serialVersionUID = 4774881970558875024L
        private val serialPersistentFields: Array<RbnfObjectStreamField> = simpleDateFormatSerialFields()
        private val defaultPatterns = SimpleDateFormatDefaultPatternCore()
        @Deprecated("This API is ICU internal only.")
        fun getInstance(configuration: Calendar.FormatConfiguration?): SimpleDateFormat {
            val value = configuration!!
            val override = value.getOverrideString()
            return SimpleDateFormat(value.getPatternString(), value.getDateFormatSymbols(), value.getCalendar(), null, value.getLocale(), !override.isNullOrEmpty(), override)
        }
        internal fun normalDateStyle(date: Boolean, style: Int, locale: ULocale?): DateFormat = normalDateTimeStyle(if (date) style else -1, if (date) -1 else style, locale)
        internal fun normalDateTimeStyle(dateStyle: Int, timeStyle: Int, locale: ULocale?): DateFormat = DateFormat.createStyle(dateStyle, timeStyle, locale, null)
        internal fun bestPattern(skeleton: String?, locale: ULocale?): String = DateTimePatternGeneratorCore().also { core ->
            DateTimePatternInitializationCore().initialize(core, locale!!) { loc ->
                val cal = Calendar.getInstance(loc)
                object : DateTimePatternInitializationCore.CalendarPatterns {
                    override fun getDateAtTimePattern(style: Int): String? = Calendar.getDateAtTimePattern(cal, loc, style)
                }
            }
        }.getBestPattern(skeleton)
    }
}
internal expect fun simpleDateFormatAssertionsEnabled(): Boolean
internal expect fun simpleDateFormatJavaCalendar(value: Any?): Boolean
internal expect fun simpleDateFormatSerialFields(): Array<RbnfObjectStreamField>
internal expect fun simpleDateFormatWrite(output: PluralObjectOutputStream, owner: SimpleDateFormat)
internal expect fun simpleDateFormatRead(input: PluralObjectInputStream, owner: SimpleDateFormat)
