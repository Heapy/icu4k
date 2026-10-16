// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.util.ULocale

/** DateFormatSymbols mutable access, shallow state copying and value comparison. */
internal open class DateFormatSymbolsStateCore(val data: DateFormatSymbolsDataCore) {
    var storedZoneStrings: Array<Array<String?>?>?=null
    private fun copy(name: String): Array<String?> =data.fields[name]!!.copyOf()
    private fun set(name: String,value: Array<String?>?) { data.fields[name]=value!!.copyOf() }
    open fun getEras(): Array<String?> =copy("eras")
    open fun setEras(value: Array<String?>?)=set("eras",value)
    open fun getEraNames(): Array<String?> =copy("eraNames")
    open fun setEraNames(value: Array<String?>?)=set("eraNames",value)
    open fun getNarrowEras(): Array<String?> =copy("narrowEras")
    open fun setNarrowEras(value: Array<String?>?)=set("narrowEras",value)
    open fun getMonths(): Array<String?> =copy("months")
    open fun setMonths(value: Array<String?>?)=set("months",value)
    open fun getShortMonths(): Array<String?> =copy("shortMonths")
    open fun setShortMonths(value: Array<String?>?)=set("shortMonths",value)
    open fun getWeekdays(): Array<String?> =copy("weekdays")
    open fun setWeekdays(value: Array<String?>?)=set("weekdays",value)
    open fun getShortWeekdays(): Array<String?> =copy("shortWeekdays")
    open fun setShortWeekdays(value: Array<String?>?)=set("shortWeekdays",value)
    private fun field(context: Int,width: Int,format: Array<String>,standalone: Array<String>,short: Boolean): String? {
        val names=when(context) { FORMAT -> format; STANDALONE -> standalone; else -> return null }
        val index=if(width==SHORT && short) ABBREVIATED else width
        return names.getOrNull(index)
    }
    private fun contextual(context: Int,width: Int,format: Array<String>,standalone: Array<String>,short: Boolean,weekday: Boolean=false): Array<String?> {
        val name=field(context,width,format,standalone,short)
        var value=if(name==null) null else data.fields[name]
        if(weekday && width==SHORT && value==null) value=field(context,ABBREVIATED,format,standalone,false)?.let { data.fields[it] }
        if(value==null) throw IllegalArgumentException("Bad context or width argument")
        return value.copyOf()
    }
    open fun getMonths(context: Int,width: Int): Array<String?> =contextual(context,width,MONTHS,STAND_MONTHS,true)
    open fun setMonths(value: Array<String?>?,context: Int,width: Int) { field(context,width,MONTHS,STAND_MONTHS,false)?.let { set(it,value) } }
    open fun getWeekdays(context: Int,width: Int): Array<String?> =contextual(context,width,DAYS,STAND_DAYS,false,true)
    open fun setWeekdays(value: Array<String?>?,context: Int,width: Int) { field(context,width,DAYS,STAND_DAYS,false)?.let { set(it,value) } }
    open fun getQuarters(context: Int,width: Int): Array<String?> =contextual(context,width,QUARTERS,STAND_QUARTERS,true)
    open fun setQuarters(value: Array<String?>?,context: Int,width: Int) { field(context,width,QUARTERS,STAND_QUARTERS,false)?.let { set(it,value) } }
    @Suppress("UNUSED_PARAMETER")
    open fun getYearNames(context: Int,width: Int): Array<String?>?=data.fields["shortYearNames"]?.copyOf()
    open fun setYearNames(value: Array<String?>?,context: Int,width: Int) { if(context==FORMAT && width==ABBREVIATED) set("shortYearNames",value) }
    @Suppress("UNUSED_PARAMETER")
    open fun getZodiacNames(context: Int,width: Int): Array<String?>?=data.fields["shortZodiacNames"]?.copyOf()
    open fun setZodiacNames(value: Array<String?>?,context: Int,width: Int) { if(context==FORMAT && width==ABBREVIATED) set("shortZodiacNames",value) }
    private fun leapIndex(context: Int,width: Int,getter: Boolean): Int {
        if(context==NUMERIC) return 6
        if(context!=FORMAT && context!=STANDALONE) return -1
        return when(width) {
            WIDE -> if(context==FORMAT) 0 else 3
            ABBREVIATED -> 1 // Source uses format's slot for standalone abbreviated too.
            NARROW -> if(context==FORMAT) 2 else 5
            SHORT -> if(getter) 1 else -1
            else -> -1
        }
    }
    open fun getLeapMonthPattern(context: Int,width: Int): String? {
        val values=data.fields["leapMonthPatterns"]?:return null
        val index=leapIndex(context,width,true)
        if(index<0) throw IllegalArgumentException("Bad context or width argument")
        if(index !in values.indices) throw IndexOutOfBoundsException()
        return values[index]
    }
    open fun setLeapMonthPattern(value: String?,context: Int,width: Int) {
        val values=data.fields["leapMonthPatterns"]?:return
        val index=leapIndex(context,width,false)
        if(index>=0) { if(index !in values.indices) throw IndexOutOfBoundsException(); values[index]=value }
    }
    open fun getAmPmStrings(): Array<String?> =getAmPmStrings(FORMAT,ABBREVIATED)
    open fun setAmPmStrings(value: Array<String?>?)=setAmPmStrings(value,FORMAT,ABBREVIATED)
    @Suppress("UNUSED_PARAMETER")
    open fun getAmPmStrings(context: Int,width: Int): Array<String?> =copy(ampmField(width))
    @Suppress("UNUSED_PARAMETER")
    open fun setAmPmStrings(value: Array<String?>?,context: Int,width: Int)=set(ampmField(width),value)
    private fun ampmField(width: Int): String=when(width) { WIDE -> "ampmsWide"; NARROW -> "ampmsNarrow"; else -> "ampms" }
    open fun getTimeSeparatorString(): String?=data.timeSeparator
    open fun setTimeSeparatorString(value: String?) { data.timeSeparator=value }
    open fun getLocalPatternChars(): String?=data.localPatternChars
    open fun setLocalPatternChars(value: String?) { data.localPatternChars=value }
    open fun setZoneStrings(value: Array<Array<String?>?>?) { storedZoneStrings=duplicate(value) }
    fun copyZoneStrings(): Array<Array<String?>?> =duplicate(storedZoneStrings)
    private fun duplicate(value: Array<Array<String?>?>?): Array<Array<String?>?> = Array(value!!.size) { value[it]!!.copyOf() }
    /** initializeData(dfs) / clone state: references are shared, field slots remain independent. */
    fun initializeFrom(other: DateFormatSymbolsStateCore) {
        for(name in COPY_FIELDS) data.fields[name]=other.data.fields[name]
        data.timeSeparator=other.data.timeSeparator; storedZoneStrings=other.storedZoneStrings; data.localPatternChars=other.data.localPatternChars
        data.capitalization=other.data.capitalization; data.actualLocale=other.data.actualLocale; data.validLocale=other.data.validLocale; data.requestedLocale=other.data.requestedLocale
    }
    fun hashCodeState(): Int=data.requestedLocale!!.toString().hashCode()
    fun equalsObject(self: Any,other: Any?,sameClass: (Any)->Boolean,stateOf: (Any)->DateFormatSymbolsStateCore,localeDisplayName: (ULocale)->String): Boolean {
        if(self===other) return true
        if(other==null || !sameClass(other)) return false
        return equalsState(stateOf(other),localeDisplayName)
    }
    /** Caller checks public-object identity and runtime class before requesting other state. */
    fun equalsState(other: DateFormatSymbolsStateCore,localeDisplayName: (ULocale)->String): Boolean {
        for(name in EQUAL_FIELDS) if(!data.fields[name].contentEquals(other.data.fields[name])) return false
        if(data.timeSeparator!=other.data.timeSeparator || !storedZoneStrings.contentDeepEquals(other.storedZoneStrings)) return false
        if(localeDisplayName(data.requestedLocale!!)!=localeDisplayName(other.data.requestedLocale!!)) return false
        return data.localPatternChars==other.data.localPatternChars
    }
    companion object {
        const val FORMAT=0; const val STANDALONE=1; const val NUMERIC=2
        const val ABBREVIATED=0; const val WIDE=1; const val NARROW=2; const val SHORT=3
        private val MONTHS=arrayOf("shortMonths","months","narrowMonths")
        private val STAND_MONTHS=arrayOf("standaloneShortMonths","standaloneMonths","standaloneNarrowMonths")
        private val DAYS=arrayOf("shortWeekdays","weekdays","narrowWeekdays","shorterWeekdays")
        private val STAND_DAYS=arrayOf("standaloneShortWeekdays","standaloneWeekdays","standaloneNarrowWeekdays","standaloneShorterWeekdays")
        private val QUARTERS=arrayOf("shortQuarters","quarters","narrowQuarters")
        private val STAND_QUARTERS=arrayOf("standaloneShortQuarters","standaloneQuarters","standaloneNarrowQuarters")
        private val EQUAL_FIELDS=arrayOf("eras","eraNames","narrowEras","months","shortMonths","narrowMonths","standaloneMonths","standaloneShortMonths","standaloneNarrowMonths","weekdays","shortWeekdays","shorterWeekdays","narrowWeekdays","standaloneWeekdays","standaloneShortWeekdays","standaloneShorterWeekdays","standaloneNarrowWeekdays","ampms","ampmsWide","ampmsNarrow","abbreviatedDayPeriods","wideDayPeriods","narrowDayPeriods","standaloneAbbreviatedDayPeriods","standaloneWideDayPeriods","standaloneNarrowDayPeriods")
        private val COPY_FIELDS=EQUAL_FIELDS+arrayOf("shortQuarters","narrowQuarters","quarters","standaloneShortQuarters","standaloneNarrowQuarters","standaloneQuarters","leapMonthPatterns","shortYearNames","shortZodiacNames")
    }
}
