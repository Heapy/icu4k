@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*

internal open class SimpleDateFormatParseFixture(id: String, initialPattern: String) : SimpleDateFormatParseCore.Owner, SimpleDateFormatFieldParseCore.Owner {
    override val locale = ULocale(id)
    override var assertionsEnabled = false
    override var calendar: Calendar? = Calendar.getInstance(TimeZone.getTimeZone("Etc/UTC"), locale)
    override var symbols: DateFormatSymbols? = DateFormatSymbols(calendar, locale)
    override var numberingOverride: String? = null
    override var pattern: String? = initialPattern
        set(value) { field = value; core.resetPattern() }
    val core = SimpleDateFormatParseCore(this)
    val fields = SimpleDateFormatFieldParseCore(this)
    val attributes = DateFormat.BooleanAttribute.entries.toMutableSet()
    var centuryStart = 0L
    var centuryYear = 1970
    var nf: NumberFormat? = NumberFormat.getInstance(locale).also { DateFormat.fixNumberFormatForDates(it) }
    var zoneFormatter = TimeZoneFormat.getInstance(locale)
    val trace = ArrayList<String>()
    override fun numberFormat(ch: Char): NumberFormat? { trace.add("number:$ch"); return nf }
    override fun timeZoneFormat(): TimeZoneFormat = zoneFormatter
    override fun booleanAttribute(key: DateFormat.BooleanAttribute): Boolean = key in attributes
    override fun isLenient(): Boolean = calendar!!.isLenient() && booleanAttribute(DateFormat.BooleanAttribute.PARSE_ALLOW_NUMERIC) && booleanAttribute(DateFormat.BooleanAttribute.PARSE_ALLOW_WHITESPACE)
    override fun defaultCenturyStart(): Long = centuryStart
    override fun defaultCenturyStartYear(): Int = centuryYear
    override fun subParse(text: String, start: Int, ch: Char, count: Int, obeyCount: Boolean, allowNegative: Boolean, ambiguousYear: BooleanArray, cal: Calendar, numericLeapMonth: SimpleDateFormatParseCore.NumericLeapMonth?, timeType: Output<TimeZoneFormat.TimeType>?, dayPeriod: Output<DayPeriodRules.DayPeriod>?): Int =
        fields.subParse(text, start, ch, count, obeyCount, allowNegative, ambiguousYear, cal, numericLeapMonth, timeType, dayPeriod)
    override fun matchQuarterString(text: String, start: Int, field: Int, data: Array<String?>, cal: Calendar): Int = fields.matchQuarterString(text, start, field, data, cal)
    override fun numericLeapMonthParser(pattern: String): SimpleDateFormatParseCore.NumericLeapMonth = object : SimpleDateFormatParseCore.NumericLeapMonth, MessageFormatParseCore.Owner {
        override val messagePattern = MessagePattern(pattern)
        override val cachedParsers = HashMap<Int, MessageFormatParseCore.Parser?>()
        val parser = MessageFormatParseCore(this)
        override fun setNumberFormat(format: NumberFormat?) {
            var i = 0
            while (true) {
                i = MessageFormatPatternCore.nextTopLevelArgStart(messagePattern, i)
                if (i < 0) break
                if (messagePattern.getPart(i + 1).value == 0) cachedParsers[i] = format?.let { MessageFormatParseCore.Parser { source, pos -> it.parseObject(source, pos) } }
            }
        }
        override fun parse(text: String, position: ParsePosition): Array<Any?>? = parser.parse(text, position)
    }
    fun parse(text: String, start: Int = 0): ParsePosition {
        calendar!!.clear()
        return ParsePosition(start).also { core.parse(text, calendar, it) }
    }
}
