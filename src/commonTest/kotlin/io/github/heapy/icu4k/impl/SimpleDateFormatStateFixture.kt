@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*

/** Test owner joining the actual format/parse/state engines; not a public SDF replacement. */
internal open class SimpleDateFormatStateFixture : DateFormat {
    val state = SimpleDateFormatStateCore(object : SimpleDateFormatStateCore.Owner {
        override var calendar: Calendar?
            get() = this@SimpleDateFormatStateFixture.calendar
            set(value) { this@SimpleDateFormatStateFixture.calendar = value }
        override var numberFormat: NumberFormat?
            get() = this@SimpleDateFormatStateFixture.numberFormat
            set(value) { this@SimpleDateFormatStateFixture.numberFormat = value }
        override fun currentTimeMillis(): Long = clock
        override fun setLocale(valid: ULocale?, actual: ULocale?) { this@SimpleDateFormatStateFixture.setLocale(valid, actual) }
        override fun setNumberFormatVirtual(format: NumberFormat?) = this@SimpleDateFormatStateFixture.setNumberFormat(format)
    })
    var clock = 0L
    var assertions = false
    val formatter: SimpleDateFormatFormatCore = SimpleDateFormatFormatCore(object : SimpleDateFormatFormatCore.Owner {
        override val calendar get() = this@SimpleDateFormatStateFixture.calendar
        override val pattern get() = state.pattern
        override val locale get() = state.locale
        override val symbols get() = state.symbols
        override val numberFormat get() = this@SimpleDateFormatStateFixture.numberFormat
        override val numberingOverride get() = state.numberingOverride
        override val hasMinute get() = state.flags.hasMinute
        override val hasSecond get() = state.flags.hasSecond
        override val useFastFormat get() = state.useFastFormat
        override fun compiledPatternItems(): Array<Any> = state.compiledItems()
        override fun capitalizationContext(): DisplayContext = getContext(DisplayContext.Type.CAPITALIZATION)
        override fun numberFormat(ch: Char): NumberFormat? = this@SimpleDateFormatStateFixture.getNumberFormat(ch)
        override fun timeZoneFormat(): TimeZoneFormat = state.timeZoneFormat()
        override fun zeroPaddingNumber(nf: NumberFormat?, buf: StringBuilder?, value: Int, min: Int, max: Int) = state.numbers.format(nf,buf,value,min,max)
        override fun subFormat(buf: StringBuilder?, ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?) = formatter.formatField(buf,ch,count,begin,fieldNum,context,pos,outputChar,cal)
        override fun subFormat(ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?): String? = formatter.formatFieldString(ch,count,begin,fieldNum,context,pos,outputChar,cal)
        override fun patternCharToDateFormatField(ch: Char): DateFormat.Field? = SimpleDateFormatPatternCore.attribute(ch)
    })
    val fields: SimpleDateFormatFieldParseCore = SimpleDateFormatFieldParseCore(object : SimpleDateFormatFieldParseCore.Owner {
        override val symbols get() = state.symbols
        override val numberingOverride get() = state.numberingOverride
        override fun numberFormat(ch: Char): NumberFormat? = getNumberFormat(ch)
        override fun timeZoneFormat(): TimeZoneFormat = state.timeZoneFormat()
        override fun booleanAttribute(key: BooleanAttribute): Boolean = getBooleanAttribute(key)
        override fun isLenient(): Boolean = this@SimpleDateFormatStateFixture.isLenient()
        override fun defaultCenturyStartYear(): Int = state.centuryStartYear()
        override fun subParse(text: String, start: Int, ch: Char, count: Int, obeyCount: Boolean, allowNegative: Boolean, ambiguousYear: BooleanArray, cal: Calendar, numericLeapMonth: SimpleDateFormatParseCore.NumericLeapMonth?, timeType: Output<TimeZoneFormat.TimeType>?, dayPeriod: Output<DayPeriodRules.DayPeriod>?): Int = fields.subParse(text,start,ch,count,obeyCount,allowNegative,ambiguousYear,cal,numericLeapMonth,timeType,dayPeriod)
        override fun matchQuarterString(text: String, start: Int, field: Int, data: Array<String?>, cal: Calendar): Int = fields.matchQuarterString(text,start,field,data,cal)
    })
    val parser = SimpleDateFormatParseCore(object : SimpleDateFormatParseCore.Owner {
        override val calendar get() = this@SimpleDateFormatStateFixture.calendar
        override val pattern get() = state.pattern
        override val locale get() = state.locale
        override val symbols get() = state.symbols
        override val assertionsEnabled get() = assertions
        override fun compiledPatternItems(): Array<Any> = state.compiledItems()
        override fun booleanAttribute(key: BooleanAttribute): Boolean = getBooleanAttribute(key)
        override fun defaultCenturyStart(): Long = state.centuryStart()!!.getTime()
        override fun defaultCenturyStartYear(): Int = state.centuryStartYear()
        override fun subParse(text: String, start: Int, ch: Char, count: Int, obeyCount: Boolean, allowNegative: Boolean, ambiguousYear: BooleanArray, cal: Calendar, numericLeapMonth: SimpleDateFormatParseCore.NumericLeapMonth?, timeType: Output<TimeZoneFormat.TimeType>?, dayPeriod: Output<DayPeriodRules.DayPeriod>?): Int = fields.subParse(text,start,ch,count,obeyCount,allowNegative,ambiguousYear,cal,numericLeapMonth,timeType,dayPeriod)
        override fun numericLeapMonthParser(pattern: String): SimpleDateFormatParseCore.NumericLeapMonth = SimpleDateFormatParseFixture(state.locale.toString(),"").numericLeapMonthParser(pattern)
    })
    val interval = SimpleDateFormatIntervalCore(object : SimpleDateFormatIntervalCore.Owner {
        override val pattern get() = state.pattern
        override val useFastFormat get() = state.useFastFormat
        override fun compiledPatternItems(): Array<Any> = state.compiledItems()
        override fun capitalizationContext(): DisplayContext = getContext(DisplayContext.Type.CAPITALIZATION)
        override fun format(cal: Calendar?, output: StringBuilder?, pos: FormatFieldPosition?): StringBuilder = this@SimpleDateFormatStateFixture.format(cal,output,pos)
        override fun subFormat(buf: StringBuilder?, ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?) = formatter.formatField(buf,ch,count,begin,fieldNum,context,pos,outputChar,cal)
        override fun subFormat(ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?): String? = formatter.formatFieldString(ch,count,begin,fieldNum,context,pos,outputChar,cal)
    })
    constructor(id: String?, pattern: String?, override: String? = null, fast: Boolean = true) : super() {
        state.locale = id?.let(::ULocale); state.pattern = pattern
        state.numberingOverride = override; state.useFastFormat = fast
        state.initialize()
    }
    private constructor(other: SimpleDateFormatStateFixture) : super(other,Unit) {
        calendar = other.calendar!!.clone(); numberFormat = other.numberFormat?.clone()
        clock = other.clock; assertions = other.assertions
        state.copyStateFrom(other.state)
        formatter.copyStateFrom(other.formatter); parser.copyStateFrom(other.parser)
    }
    override fun format(calendar: Calendar?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = formatter.format(calendar,output,position)!!
    override fun parse(text: String?, calendar: Calendar?, position: ParsePosition?) = parser.parse(text,calendar,position)
    override fun setNumberFormat(value: NumberFormat?) { super.setNumberFormat(value); state.afterNumberFormatSet() }
    fun setNumberFormat(fields: String?, value: NumberFormat?) = state.setFieldsNumberFormat(fields,value)
    open fun getNumberFormat(ch: Char): NumberFormat? = state.numberFormat(ch)
    override fun setContext(context: DisplayContext?) {
        super.setContext(context)
        if (formatter.capitalizationBrkIter == null && context in listOf(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE, DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU, DisplayContext.CAPITALIZATION_FOR_STANDALONE)) formatter.capitalizationBrkIter = BreakIterator.getSentenceInstance(state.locale)
    }
    public override fun clone(): SimpleDateFormatStateFixture = SimpleDateFormatStateFixture(this)
}
