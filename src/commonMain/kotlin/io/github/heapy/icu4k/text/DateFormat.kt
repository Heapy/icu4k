// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*

/** Calendar-based format/parse protocol. Pattern implementations supply the two abstract hooks. */
abstract class DateFormat : UFormat {
    @kotlin.jvm.JvmField protected var calendar: Calendar? = null
    @kotlin.jvm.JvmField protected var numberFormat: NumberFormat? = null
    private var booleanAttributes: MutableSet<BooleanAttribute> = BooleanAttribute.entries.toMutableSet()
    private var capitalizationSetting: DisplayContext? = DisplayContext.CAPITALIZATION_NONE
    protected constructor() : super()
    protected constructor(other: DateFormat, @Suppress("UNUSED_PARAMETER") copyMarker: Unit) : super() {
        calendar=other.calendar; numberFormat=other.numberFormat
        booleanAttributes=other.booleanAttributes; capitalizationSetting=other.capitalizationSetting
        other.copyLocaleTo(this)
    }
    enum class BooleanAttribute {
        PARSE_ALLOW_WHITESPACE, PARSE_ALLOW_NUMERIC, PARSE_MULTIPLE_PATTERNS_FOR_MATCH,
        PARSE_PARTIAL_LITERAL_MATCH,
        @Deprecated("Use PARSE_PARTIAL_LITERAL_MATCH") PARSE_PARTIAL_MATCH
    }
    enum class HourCycle { HOUR_CYCLE_11, HOUR_CYCLE_12, HOUR_CYCLE_23, HOUR_CYCLE_24 }
    final override fun format(input: Any?,output: StringBuilder?,position: FormatFieldPosition?): StringBuilder = when(input) {
        is Calendar -> format(input,output,position)
        is Date -> format(input,output,position)
        is Number -> format(Date(input.toLong()),output,position)
        else -> {
            val converted=dateFormatCalendarInput(input!!)
            if(converted!=null) format(converted,output,position)
            else throw IllegalArgumentException("Cannot format given Object ("+dateFormatClassName(input)+") as a Date")
        }
    }
    abstract fun format(calendar: Calendar?,output: StringBuilder?,position: FormatFieldPosition?): StringBuilder
    open fun format(date: Date?,output: StringBuilder?,position: FormatFieldPosition?): StringBuilder {
        calendar!!.setTime(date)
        return format(calendar,output,position)
    }
    fun format(date: Date?): String = format(date,StringBuilder(64),FormatFieldPosition(0)).toString()
    abstract fun parse(text: String?,calendar: Calendar?,position: ParsePosition?)
    open fun parse(text: String?,position: ParsePosition?): Date? {
        var result: Date?=null
        val start=position!!.index
        val savedZone=calendar!!.getTimeZone()
        calendar!!.clear()
        parse(text,calendar,position)
        if(position.index!=start) {
            try { result=calendar!!.getTime() }
            catch(_:IllegalArgumentException) { position.index=start; position.errorIndex=start }
        }
        calendar!!.setTimeZone(savedZone)
        return result
    }
    open fun parse(text: String?): Date? {
        val position=ParsePosition(0); val result=parse(text,position)
        if(position.index==0) throw ParseException("Unparseable date: \"$text\"",position.errorIndex)
        return result
    }
    override fun parseObject(source: String?,position: ParsePosition?): Any? = parse(source,position)
    open fun setCalendar(value: Calendar?) { calendar=value }
    open fun getCalendar(): Calendar? = calendar
    open fun setNumberFormat(value: NumberFormat?) { numberFormat=value!!.clone(); fixNumberFormatForDates(numberFormat!!) }
    open fun getNumberFormat(): NumberFormat? = numberFormat
    open fun setTimeZone(value: TimeZone?) { calendar!!.setTimeZone(value) }
    open fun getTimeZone(): TimeZone? = calendar!!.getTimeZone()
    open fun setLenient(value: Boolean) {
        calendar!!.setLenient(value)
        setBooleanAttribute(BooleanAttribute.PARSE_ALLOW_NUMERIC,value)
        setBooleanAttribute(BooleanAttribute.PARSE_ALLOW_WHITESPACE,value)
    }
    open fun isLenient(): Boolean = calendar!!.isLenient() && getBooleanAttribute(BooleanAttribute.PARSE_ALLOW_NUMERIC) && getBooleanAttribute(BooleanAttribute.PARSE_ALLOW_WHITESPACE)
    open fun setCalendarLenient(value: Boolean) { calendar!!.setLenient(value) }
    open fun isCalendarLenient(): Boolean = calendar!!.isLenient()
    open fun setBooleanAttribute(key: BooleanAttribute?,value: Boolean): DateFormat {
        val actual=if(key!! ==BooleanAttribute.PARSE_PARTIAL_MATCH) BooleanAttribute.PARSE_PARTIAL_LITERAL_MATCH else key
        if(value) booleanAttributes.add(actual) else booleanAttributes.remove(actual)
        return this
    }
    open fun getBooleanAttribute(key: BooleanAttribute?): Boolean = booleanAttributes.contains(if(key==BooleanAttribute.PARSE_PARTIAL_MATCH) BooleanAttribute.PARSE_PARTIAL_LITERAL_MATCH else key)
    open fun setContext(context: DisplayContext?) { if(context!!.type()==DisplayContext.Type.CAPITALIZATION) capitalizationSetting=context }
    open fun getContext(type: DisplayContext.Type?): DisplayContext = if(type==DisplayContext.Type.CAPITALIZATION && capitalizationSetting!=null) capitalizationSetting!! else DisplayContext.CAPITALIZATION_NONE
    override fun hashCode(): Int = numberFormat!!.hashCode()
    override fun equals(other: Any?): Boolean {
        if(this===other) return true
        if(other==null || other::class!=this::class) return false
        other as DateFormat
        return ((calendar==null && other.calendar==null) || (calendar!=null && other.calendar!=null && calendar!!.isEquivalentTo(other.calendar))) &&
            ((numberFormat==null && other.numberFormat==null) || (numberFormat!=null && other.numberFormat!=null && numberFormat==other.numberFormat)) && capitalizationSetting==other.capitalizationSetting
    }
    override fun clone(): DateFormat {
        val copy=cloneHost() as? DateFormat ?: cloneNative()
        copy.calendar=calendar!!.clone()
        if(numberFormat!=null) copy.numberFormat=numberFormat!!.clone()
        return copy
    }
    protected open fun cloneNative(): DateFormat = throw ICUCloneNotSupportedException()
    open class Field protected constructor(name: String?,private val calendarField: Int): FormatField(name) {
        init {
            val registry=DateFormatFieldRegistry
            if(this::class==Field::class) {
                registry.names[name]=this
                if(calendarField in registry.fields.indices) registry.fields[calendarField]=this
            }
        }
        open fun getCalendarField(): Int=calendarField
        protected override fun readResolve(): Any {
            if(this::class!=Field::class) invalidDateFormatField("A subclass of DateFormat.Field must implement readResolve.")
            return DateFormatFieldRegistry.names[getName()] ?: invalidDateFormatField("Unknown attribute name.")
        }
        companion object {
            private const val serialVersionUID = -3627456821000730829L
            // Force the source Gregorian metadata initialization before defining constants.
            private val registry=DateFormatFieldRegistry
            @kotlin.jvm.JvmField val AM_PM=Field("am pm",Calendar.AM_PM)
            @kotlin.jvm.JvmField val DAY_OF_MONTH=Field("day of month",Calendar.DAY_OF_MONTH)
            @kotlin.jvm.JvmField val DAY_OF_WEEK=Field("day of week",Calendar.DAY_OF_WEEK)
            @kotlin.jvm.JvmField val DAY_OF_WEEK_IN_MONTH=Field("day of week in month",Calendar.DAY_OF_WEEK_IN_MONTH)
            @kotlin.jvm.JvmField val DAY_OF_YEAR=Field("day of year",Calendar.DAY_OF_YEAR)
            @kotlin.jvm.JvmField val ERA=Field("era",Calendar.ERA)
            @kotlin.jvm.JvmField val HOUR_OF_DAY0=Field("hour of day",Calendar.HOUR_OF_DAY)
            @kotlin.jvm.JvmField val HOUR_OF_DAY1=Field("hour of day 1",-1)
            @kotlin.jvm.JvmField val HOUR0=Field("hour",Calendar.HOUR)
            @kotlin.jvm.JvmField val HOUR1=Field("hour 1",-1)
            @kotlin.jvm.JvmField val MILLISECOND=Field("millisecond",Calendar.MILLISECOND)
            @kotlin.jvm.JvmField val MINUTE=Field("minute",Calendar.MINUTE)
            @kotlin.jvm.JvmField val MONTH=Field("month",Calendar.MONTH)
            @kotlin.jvm.JvmField val SECOND=Field("second",Calendar.SECOND)
            @kotlin.jvm.JvmField val TIME_ZONE=Field("time zone",-1)
            @kotlin.jvm.JvmField val WEEK_OF_MONTH=Field("week of month",Calendar.WEEK_OF_MONTH)
            @kotlin.jvm.JvmField val WEEK_OF_YEAR=Field("week of year",Calendar.WEEK_OF_YEAR)
            @kotlin.jvm.JvmField val YEAR=Field("year",Calendar.YEAR)
            @kotlin.jvm.JvmField val DOW_LOCAL=Field("local day of week",Calendar.DOW_LOCAL)
            @kotlin.jvm.JvmField val EXTENDED_YEAR=Field("extended year",Calendar.EXTENDED_YEAR)
            @kotlin.jvm.JvmField val JULIAN_DAY=Field("Julian day",Calendar.JULIAN_DAY)
            @kotlin.jvm.JvmField val MILLISECONDS_IN_DAY=Field("milliseconds in day",Calendar.MILLISECONDS_IN_DAY)
            @kotlin.jvm.JvmField val YEAR_WOY=Field("year for week of year",Calendar.YEAR_WOY)
            @kotlin.jvm.JvmField val QUARTER=Field("quarter",-1)
            @Deprecated("This API is ICU internal only.")
            @kotlin.jvm.JvmField val RELATED_YEAR=Field("related year",-1)
            @kotlin.jvm.JvmField val AM_PM_MIDNIGHT_NOON=Field("am/pm/midnight/noon",-1)
            @kotlin.jvm.JvmField val FLEXIBLE_DAY_PERIOD=Field("flexible day period",-1)
            @Deprecated("This API is ICU internal only.")
            @kotlin.jvm.JvmField val TIME_SEPARATOR=Field("time separator",-1)
            fun ofCalendarField(field: Int): Field? {
                if(field !in registry.fields.indices) throw IllegalArgumentException("Calendar field number is out of range")
                return registry.fields[field]
            }
        }
    }
    companion object {
        private const val serialVersionUID=7218322306649953788L
        internal fun fixNumberFormatForDates(format: NumberFormat) {
            format.setGroupingUsed(false)
            if(format is DecimalFormat) format.setDecimalSeparatorAlwaysShown(false)
            format.setParseIntegerOnly(true); format.setMinimumFractionDigits(0)
        }
        const val ERA_FIELD = 0
        const val YEAR_FIELD = 1
        const val MONTH_FIELD = 2
        const val DATE_FIELD = 3
        const val HOUR_OF_DAY1_FIELD = 4
        const val HOUR_OF_DAY0_FIELD = 5
        const val MINUTE_FIELD = 6
        const val SECOND_FIELD = 7
        const val FRACTIONAL_SECOND_FIELD = 8
        const val MILLISECOND_FIELD = FRACTIONAL_SECOND_FIELD
        const val DAY_OF_WEEK_FIELD = 9
        const val DAY_OF_YEAR_FIELD = 10
        const val DAY_OF_WEEK_IN_MONTH_FIELD = 11
        const val WEEK_OF_YEAR_FIELD = 12
        const val WEEK_OF_MONTH_FIELD = 13
        const val AM_PM_FIELD = 14
        const val HOUR1_FIELD = 15
        const val HOUR0_FIELD = 16
        const val TIMEZONE_FIELD = 17
        const val YEAR_WOY_FIELD = 18
        const val DOW_LOCAL_FIELD = 19
        const val EXTENDED_YEAR_FIELD = 20
        const val JULIAN_DAY_FIELD = 21
        const val MILLISECONDS_IN_DAY_FIELD = 22
        const val TIMEZONE_RFC_FIELD = 23
        const val TIMEZONE_GENERIC_FIELD = 24
        const val STANDALONE_DAY_FIELD = 25
        const val STANDALONE_MONTH_FIELD = 26
        const val QUARTER_FIELD = 27
        const val STANDALONE_QUARTER_FIELD = 28
        const val TIMEZONE_SPECIAL_FIELD = 29
        const val YEAR_NAME_FIELD = 30
        const val TIMEZONE_LOCALIZED_GMT_OFFSET_FIELD = 31
        const val TIMEZONE_ISO_FIELD = 32
        const val TIMEZONE_ISO_LOCAL_FIELD = 33
        internal const val RELATED_YEAR = 34
        const val AM_PM_MIDNIGHT_NOON_FIELD = 35
        const val FLEXIBLE_DAY_PERIOD_FIELD = 36
        const val TIME_SEPARATOR = 37
        const val FIELD_COUNT = 38
        const val NONE = -1
        const val FULL = 0
        const val LONG = 1
        const val MEDIUM = 2
        const val SHORT = 3
        const val DEFAULT = MEDIUM
        const val RELATIVE = (1 shl 7)
        const val RELATIVE_FULL = RELATIVE or FULL
        const val RELATIVE_LONG = RELATIVE or LONG
        const val RELATIVE_MEDIUM = RELATIVE or MEDIUM
        const val RELATIVE_SHORT = RELATIVE or SHORT
        const val RELATIVE_DEFAULT = RELATIVE or DEFAULT
        const val YEAR = "y"
        const val QUARTER = "QQQQ"
        const val ABBR_QUARTER = "QQQ"
        const val YEAR_QUARTER = "yQQQQ"
        const val YEAR_ABBR_QUARTER = "yQQQ"
        const val MONTH = "MMMM"
        const val ABBR_MONTH = "MMM"
        const val NUM_MONTH = "M"
        const val YEAR_MONTH = "yMMMM"
        const val YEAR_ABBR_MONTH = "yMMM"
        const val YEAR_NUM_MONTH = "yM"
        const val DAY = "d"
        const val YEAR_MONTH_DAY = "yMMMMd"
        const val YEAR_ABBR_MONTH_DAY = "yMMMd"
        const val YEAR_NUM_MONTH_DAY = "yMd"
        const val WEEKDAY = "EEEE"
        const val ABBR_WEEKDAY = "E"
        const val YEAR_MONTH_WEEKDAY_DAY = "yMMMMEEEEd"
        const val YEAR_ABBR_MONTH_WEEKDAY_DAY = "yMMMEd"
        const val YEAR_NUM_MONTH_WEEKDAY_DAY = "yMEd"
        const val MONTH_DAY = "MMMMd"
        const val ABBR_MONTH_DAY = "MMMd"
        const val NUM_MONTH_DAY = "Md"
        const val MONTH_WEEKDAY_DAY = "MMMMEEEEd"
        const val ABBR_MONTH_WEEKDAY_DAY = "MMMEd"
        const val NUM_MONTH_WEEKDAY_DAY = "MEd"
        const val HOUR = "j"
        const val HOUR24 = "H"
        const val MINUTE = "m"
        const val HOUR_MINUTE = "jm"
        const val HOUR24_MINUTE = "Hm"
        const val SECOND = "s"
        const val HOUR_MINUTE_SECOND = "jms"
        const val HOUR24_MINUTE_SECOND = "Hms"
        const val MINUTE_SECOND = "ms"
        const val LOCATION_TZ = "VVVV"
        const val GENERIC_TZ = "vvvv"
        const val ABBR_GENERIC_TZ = "v"
        const val SPECIFIC_TZ = "zzzz"
        const val ABBR_SPECIFIC_TZ = "z"
        const val ABBR_UTC_TZ = "ZZZZ"
        const val STANDALONE_MONTH = "LLLL"
        const val ABBR_STANDALONE_MONTH = "LLL"
        const val HOUR_MINUTE_GENERIC_TZ = "jmv"
        const val HOUR_MINUTE_TZ = "jmz"
        const val HOUR_GENERIC_TZ = "jv"
        const val HOUR_TZ = "jz"
        @kotlin.jvm.JvmField val DATE_SKELETONS: MutableList<String?> = FixedDateSkeletonList(listOf(YEAR,
            QUARTER,
            ABBR_QUARTER,
            YEAR_QUARTER,
            YEAR_ABBR_QUARTER,
            MONTH,
            ABBR_MONTH,
            NUM_MONTH,
            YEAR_MONTH,
            YEAR_ABBR_MONTH,
            YEAR_NUM_MONTH,
            DAY,
            YEAR_MONTH_DAY,
            YEAR_ABBR_MONTH_DAY,
            YEAR_NUM_MONTH_DAY,
            WEEKDAY,
            ABBR_WEEKDAY,
            YEAR_MONTH_WEEKDAY_DAY,
            YEAR_ABBR_MONTH_WEEKDAY_DAY,
            YEAR_NUM_MONTH_WEEKDAY_DAY,
            MONTH_DAY,
            ABBR_MONTH_DAY,
            NUM_MONTH_DAY,
            MONTH_WEEKDAY_DAY,
            ABBR_MONTH_WEEKDAY_DAY,
            NUM_MONTH_WEEKDAY_DAY))
        @kotlin.jvm.JvmField val TIME_SKELETONS: MutableList<String?> = FixedDateSkeletonList(listOf(HOUR,
            HOUR24,
            MINUTE,
            HOUR_MINUTE,
            HOUR24_MINUTE,
            SECOND,
            HOUR_MINUTE_SECOND,
            HOUR24_MINUTE_SECOND,
            MINUTE_SECOND))
        @kotlin.jvm.JvmField val ZONE_SKELETONS: MutableList<String?> = FixedDateSkeletonList(listOf(LOCATION_TZ,
            GENERIC_TZ,
            ABBR_GENERIC_TZ,
            SPECIFIC_TZ,
            ABBR_SPECIFIC_TZ,
            ABBR_UTC_TZ))
        const val JP_ERA_2019_ROOT = "Reiwa"
        const val JP_ERA_2019_JA = "\u4EE4\u548C"
        const val JP_ERA_2019_NARROW = "R"
    }
}
private object DateFormatFieldRegistry {
    val fields=arrayOfNulls<DateFormat.Field>(GregorianCalendar().getFieldCount())
    val names=HashMap<String?,DateFormat.Field>()
}
internal expect fun dateFormatCalendarInput(input: Any): Calendar?
internal expect fun dateFormatClassName(input: Any): String
internal expect fun invalidDateFormatField(message: String): Nothing

private class FixedDateSkeletonList(values: List<String?>): AbstractMutableList<String?>() {
    private val elements=values.toTypedArray()
    override val size: Int get()=elements.size
    override fun get(index: Int): String?=elements[index]
    override fun set(index: Int,element: String?): String?=elements[index].also { elements[index]=element }
    override fun add(index: Int,element: String?): Unit=throw UnsupportedOperationException()
    override fun removeAt(index: Int): String?=throw UnsupportedOperationException()
}
