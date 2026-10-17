// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CalendarUtil
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.calendar.*
import io.github.heapy.icu4k.util.*
import kotlin.reflect.KClass
import kotlin.time.Clock

/** Locale and calendar symbol data with ICU's copy and cache semantics. */
open class DateFormatSymbols : ICUCloneableBase {
    private var symbols: DateFormatSymbolsStateCore? = null
    internal val state: DateFormatSymbolsStateCore get() = symbols ?: newState(this).also { symbols=it }
    constructor() : this(ULocale.getDefault(ULocale.Category.FORMAT))
    constructor(locale: ULocale?) : super() { initializeData(locale,CalendarUtil.getCalendarType(locale)) }
    constructor(calendar: Calendar?,locale: ULocale?) : super() { initializeData(locale,calendar!!.getType()) }
    constructor(calendarClass: KClass<out Calendar>?,locale: ULocale?) : super() {
        initializeData(locale,typeForClassName(dateSymbolsClassName(calendarClass!!)))
    }
    constructor(bundle: UResourceBundle?,locale: ULocale?) : super() { initializeData(locale,bundle as ICUResourceBundle?,CalendarUtil.getCalendarType(locale)) }
    protected constructor(other: DateFormatSymbols, @Suppress("UNUSED_PARAMETER") copyMarker: Unit) : super() {
        symbols=newState(this).also { it.initializeFrom(other.state) }
    }
    protected open fun initializeData(locale: ULocale?,type: String?) { factory.initializeData(state,locale,type) }
    @Deprecated("This API is ICU internal only.")
    protected open fun initializeData(locale: ULocale?,bundle: ICUResourceBundle?,type: String?) { state.data.initialize(locale,bundle,type) }
    @Deprecated("This API is ICU internal only.")
    protected open fun initEras(key: String,maps: MutableMap<String,MutableMap<String,String>>,bundle: ICUResourceBundle?,maxEra: Int): Array<String?>? =
        DateFormatSymbolsDataCore.initializeEras(key,maps,bundle,maxEra)
    open fun getEras(): Array<String?> = state.getEras()
    open fun setEras(value: Array<out String?>?) = state.setEras(value as Array<String?>?)
    open fun getEraNames(): Array<String?> = state.getEraNames()
    open fun setEraNames(value: Array<out String?>?) = state.setEraNames(value as Array<String?>?)
    open fun getNarrowEras(): Array<String?> = state.getNarrowEras()
    open fun setNarrowEras(value: Array<out String?>?) = state.setNarrowEras(value as Array<String?>?)
    open fun getMonths(): Array<String?> = state.getMonths()
    open fun setMonths(value: Array<out String?>?) = state.setMonths(value as Array<String?>?)
    open fun getShortMonths(): Array<String?> = state.getShortMonths()
    open fun setShortMonths(value: Array<out String?>?) = state.setShortMonths(value as Array<String?>?)
    open fun getWeekdays(): Array<String?> = state.getWeekdays()
    open fun setWeekdays(value: Array<out String?>?) = state.setWeekdays(value as Array<String?>?)
    open fun getShortWeekdays(): Array<String?> = state.getShortWeekdays()
    open fun setShortWeekdays(value: Array<out String?>?) = state.setShortWeekdays(value as Array<String?>?)
    open fun getAmPmStrings(): Array<String?> = getAmPmStrings(FORMAT,ABBREVIATED)
    open fun setAmPmStrings(value: Array<out String?>?) = setAmPmStrings(value,FORMAT,ABBREVIATED)
    open fun getMonths(context: Int,width: Int): Array<String?> = state.getMonths(context,width)
    open fun setMonths(value: Array<out String?>?,context: Int,width: Int) = state.setMonths(value as Array<String?>?,context,width)
    open fun getWeekdays(context: Int,width: Int): Array<String?> = state.getWeekdays(context,width)
    open fun setWeekdays(value: Array<out String?>?,context: Int,width: Int) = state.setWeekdays(value as Array<String?>?,context,width)
    open fun getQuarters(context: Int,width: Int): Array<String?> = state.getQuarters(context,width)
    open fun setQuarters(value: Array<out String?>?,context: Int,width: Int) = state.setQuarters(value as Array<String?>?,context,width)
    open fun getAmPmStrings(context: Int,width: Int): Array<String?> = state.getAmPmStrings(context,width)
    open fun setAmPmStrings(value: Array<out String?>?,context: Int,width: Int) = state.setAmPmStrings(value as Array<String?>?,context,width)
    open fun getYearNames(context: Int,width: Int): Array<String?>? = state.getYearNames(context,width)
    open fun setYearNames(value: Array<out String?>?,context: Int,width: Int) = state.setYearNames(value as Array<String?>?,context,width)
    open fun getZodiacNames(context: Int,width: Int): Array<String?>? = state.getZodiacNames(context,width)
    open fun setZodiacNames(value: Array<out String?>?,context: Int,width: Int) = state.setZodiacNames(value as Array<String?>?,context,width)
    open fun getLeapMonthPattern(context: Int,width: Int): String? = state.getLeapMonthPattern(context,width)
    open fun setLeapMonthPattern(value: String?,context: Int,width: Int) = state.setLeapMonthPattern(value,context,width)
    @Deprecated("This API is ICU internal only.")
    open fun getTimeSeparatorString(): String? = state.getTimeSeparatorString()
    @Deprecated("This API is ICU internal only.")
    open fun setTimeSeparatorString(value: String?) = state.setTimeSeparatorString(value)
    open fun getLocalPatternChars(): String? = state.getLocalPatternChars()
    open fun setLocalPatternChars(value: String?) = state.setLocalPatternChars(value)
    open fun getZoneStrings(): Array<Array<String?>?> = zones.getZoneStrings(state)
    open fun setZoneStrings(value: Array<out Array<out String?>?>?) = state.setZoneStrings(value as Array<Array<String?>?>?)
    fun getLocale(type: ULocale.Type?): ULocale? = if(type==ULocale.ACTUAL_LOCALE) state.data.actualLocale else state.data.validLocale
    public override fun clone(): DateFormatSymbols {
        val copy=cloneHost() as? DateFormatSymbols ?: cloneNative()
        copy.symbols=newState(copy).also { it.initializeFrom(state) }
        return copy
    }
    protected open fun cloneNative(): DateFormatSymbols = DateFormatSymbols(this,Unit)
    override fun hashCode(): Int = state.hashCodeState()
    override fun equals(other: Any?): Boolean = state.equalsObject(this,other,
        { it::class==this::class },{ (it as DateFormatSymbols).state },{ it.getDisplayName()!! })
    companion object {
        const val FORMAT=0; const val STANDALONE=1
        @Deprecated("This API is ICU internal only.") const val NUMERIC=2
        @Deprecated("This API is ICU internal only.") const val DT_CONTEXT_COUNT=3
        const val ABBREVIATED=0; const val WIDE=1; const val NARROW=2; const val SHORT=3
        @Deprecated("This API is ICU internal only.") const val DT_WIDTH_COUNT=4
        private val factory=DateFormatSymbolsFactoryCore({ newState(null) })
        private val zones=DateFormatSymbolsZoneStringsCore.withCommonNames(
            { Clock.System.now().toEpochMilliseconds() },{ TimeZone.getCanonicalID(TimeZone.getDefault().getID()) })
        private fun newState(publicOwner: DateFormatSymbols?): DateFormatSymbolsStateCore {
            val owner=object: DateFormatSymbolsDataCore.Owner {
                override fun currentTimeMillis(): Long=Clock.System.now().toEpochMilliseconds()
                override fun defaultZoneOffset(time: Long): Int=TimeZone.getDefault().getOffset(time)
                override fun className(): String=dateSymbolsClassName(publicOwner?.let { it::class } ?: DateFormatSymbols::class)
            }
            val data=if(publicOwner==null) DateFormatSymbolsDataCore(owner) else object: DateFormatSymbolsDataCore(owner) {
                override fun initEras(path: String,maps: PatternOrderedMap<String,MutableMap<String,String>>,calendar: ICUResourceBundle?,maxEra: Int): Array<String?>? =
                    publicOwner.initEras(path,maps.asMutableMap(),calendar,maxEra)
            }
            return DateFormatSymbolsStateCore(data)
        }
        internal fun typeForClassName(name: String): String {
            val simple=name.substringAfterLast('.')
            return when(simple) {
                "TaiwanCalendar" -> "roc"
                else -> simple.replace("Calendar","").lowercase()
            }
        }
        fun getInstance(): DateFormatSymbols=DateFormatSymbols()
        fun getInstance(locale: ULocale?): DateFormatSymbols=DateFormatSymbols(locale)
        fun getAvailableULocales(): Array<ULocale> = ICUResourceBundle.getAvailableULocales()!!
        @Deprecated("ICU 4.0: this method always returns null.")
        @Suppress("UNUSED_PARAMETER")
        fun getDateFormatBundle(calendar: Calendar?,locale: ULocale?): UResourceBundle?=null
        @Deprecated("ICU 4.0: this method always returns null.")
        @Suppress("UNUSED_PARAMETER")
        fun getDateFormatBundle(calendarClass: KClass<out Calendar>?,locale: ULocale?): UResourceBundle?=null
    }
}
internal expect fun dateSymbolsClassName(type: KClass<*>): String
