// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.text.NumberingSystem
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** DateFormatSymbols' resource initialization. Public accessors, cache and zone strings are separate. */
internal open class DateFormatSymbolsDataCore(
    private val owner: Owner,
    private val loader: ICUResourceLoader=defaultICUResourceLoader(),
    private val assertionsEnabled: Boolean=false,
) {
    interface Owner: EraRulesCore.Owner { fun className(): String }
    val fields=HashMap<String,Array<String?>?>()
    var requestedLocale: ULocale?=null
    var validLocale: ULocale?=null
    var actualLocale: ULocale?=null
    var localPatternChars: String?=null
    var timeSeparator: String?=null
    var capitalization: MutableMap<String,BooleanArray>?=null

    open fun initialize(desiredLocale: ULocale?,bundle: ICUResourceBundle?,calendarType: String?) {
        val sink=CalendarSymbolSink(assertionsEnabled)
        val b=bundle?:bundle(desiredLocale)
        var type=calendarType
        var eraType=calendarType?:"gregorian"
        while(type!=null) {
            val data=b.findWithFallback("calendar/$type")
            if(data==null) {
                if(type!="gregorian") { type="gregorian"; sink.visitAllResources(); continue }
                throw MissingResourceException("The 'gregorian' calendar type wasn't found for the locale: ${desiredLocale!!.getBaseName()}",owner.className(),"gregorian")
            }
            sink.preEnumerate(type); data.getAllItemsWithFallback("",sink)
            if(type=="gregorian") break
            type=sink.nextCalendarType
            if(type==null) { type="gregorian"; sink.visitAllResources() }
        }
        val eras=try { EraRulesCore.getInstance(eraType,false,owner) } catch(_: MissingResourceException) {
            eraType="gregorian"; EraRulesCore.getInstance(eraType,false,owner)
        }
        val eraBundle=b.findWithFallback("calendar/$eraType")
        for((field,path) in ERA_FIELDS) fields[field]=initEras(path,sink.maps,eraBundle,eras.getMaxEraCode())
        for((field,path) in MONTH_FIELDS) fields[field]=nullable(sink.arrays[path])
        weekday("weekdays",sink.arrays["dayNames/format/wide"])
        weekday("shortWeekdays",sink.arrays["dayNames/format/abbreviated"])
        weekday("shorterWeekdays",sink.arrays["dayNames/format/short"])
        val narrow=sink.arrays["dayNames/format/narrow"]?:sink.arrays["dayNames/stand-alone/narrow"]?:sink.arrays["dayNames/format/abbreviated"]
            ?:throw MissingResourceException("Resource not found",owner.className(),"dayNames/format/abbreviated")
        weekday("narrowWeekdays",narrow)
        for((field,width) in STANDALONE_DAYS) weekday(field,sink.arrays["dayNames/stand-alone/$width"])
        fields["ampms"]=nullable(sink.arrays["AmPmMarkersAbbr"])
        fields["ampmsWide"]=nullable(sink.arrays["AmPmMarkers"])
        fields["ampmsNarrow"]=nullable(sink.arrays["AmPmMarkersNarrow"])
        for((field,path) in QUARTER_FIELDS) fields[field]=nullable(sink.arrays[path])
        fields["abbreviatedDayPeriods"]=dayPeriods(sink.maps["dayPeriod/format/abbreviated"],null)
        fields["wideDayPeriods"]=dayPeriods(sink.maps["dayPeriod/format/wide"],fields["abbreviatedDayPeriods"])
        fields["narrowDayPeriods"]=dayPeriods(sink.maps["dayPeriod/format/narrow"],fields["abbreviatedDayPeriods"])
        fields["standaloneAbbreviatedDayPeriods"]=dayPeriods(sink.maps["dayPeriod/stand-alone/abbreviated"],fields["abbreviatedDayPeriods"])
        fields["standaloneWideDayPeriods"]=dayPeriods(sink.maps["dayPeriod/stand-alone/wide"],fields["standaloneAbbreviatedDayPeriods"])
        fields["standaloneNarrowDayPeriods"]=dayPeriods(sink.maps["dayPeriod/stand-alone/narrow"],fields["standaloneAbbreviatedDayPeriods"])
        for(i in LEAP_PATHS.indices) {
            val value=sink.maps[LEAP_PATHS[i]]?.get("leap")
            if(value!=null) {
                if(fields["leapMonthPatterns"]==null) fields["leapMonthPatterns"]=arrayOfNulls(7)
                fields["leapMonthPatterns"]!![i]=value
            }
        }
        fields["shortYearNames"]=nullable(sink.arrays["cyclicNameSets/years/format/abbreviated"])
        fields["shortZodiacNames"]=nullable(sink.arrays["cyclicNameSets/zodiacs/format/abbreviated"])
        requestedLocale=desiredLocale
        val rb=bundle(desiredLocale)
        localPatternChars=PATTERN_CHARS
        validLocale=rb.getULocale(); actualLocale=validLocale
        val transforms=HashMap<String,BooleanArray>(); capitalization=transforms
        val none=BooleanArray(2); for(usage in USAGES) transforms[usage]=none
        val context=try { rb.getWithFallback("contextTransforms") } catch(_: MissingResourceException) { null }
        if(context!=null) {
            val iterator=context.getIterator()
            while(iterator.hasNext()) {
                val resource=iterator.next(); val vector=resource.getIntVector()
                if(vector.size>=2) CONTEXT_KEYS[resource.getKey()]?.let { transforms[it]=booleanArrayOf(vector[0]!=0,vector[1]!=0) }
            }
        }
        val ns=NumberingSystem.getInstance(desiredLocale).getName()
        try { setTimeSeparatorString(rb.getStringWithFallback("NumberElements/$ns/symbols/timeSeparator")) }
        catch(_: MissingResourceException) { setTimeSeparatorString(":") }
    }
    open fun setTimeSeparatorString(value: String?) { timeSeparator=value }
    private fun bundle(locale: ULocale?): ICUResourceBundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,locale,loader) as ICUResourceBundle
    @Suppress("UNCHECKED_CAST")
    private fun nullable(array: Array<String>?): Array<String?>? = array as Array<String?>?
    private fun weekday(name: String,source: Array<String>?) {
        val result=arrayOfNulls<String>(8); fields[name]=result; result[0]=""
        source!!.copyInto(result,1)
    }
    open fun initEras(path: String,maps: PatternOrderedMap<String,MutableMap<String,String>>,calendar: ICUResourceBundle?,maxEra: Int): Array<String?>? {
        return initializeEras(path,maps.asMutableMap(),calendar,maxEra)
    }
    private fun dayPeriods(map: Map<String,String>?,copy: Array<String?>?): Array<String?> {
        if(map==null && copy!=null) return copy
        return Array(PERIOD_KEYS.size) { index -> if(map==null) null else map[PERIOD_KEYS[index]]?:copy?.get(index) }
    }
    companion object {
        fun initializeEras(path: String,maps: Map<String,Map<String,String>>,calendar: ICUResourceBundle?,maxEra: Int): Array<String?>? {
            val names=maps[path]?:return null
            val fallback=calendar!!.findWithFallback(path)
            return Array(maxEra+1) { index -> val key=index.toString(); names[key]?:fallback?.findWithFallback(key)?.getString()?:"" }
        }
        const val PATTERN_CHARS="GyMdkHmsSEDFwWahKzYeugAZvcLQqVUOXxrbB"
        private val ERA_FIELDS=arrayOf("eras" to "eras/abbreviated","eraNames" to "eras/wide","narrowEras" to "eras/narrow")
        private val MONTH_FIELDS=arrayOf("months" to "monthNames/format/wide","shortMonths" to "monthNames/format/abbreviated","narrowMonths" to "monthNames/format/narrow","standaloneMonths" to "monthNames/stand-alone/wide","standaloneShortMonths" to "monthNames/stand-alone/abbreviated","standaloneNarrowMonths" to "monthNames/stand-alone/narrow")
        private val STANDALONE_DAYS=arrayOf("standaloneWeekdays" to "wide","standaloneShortWeekdays" to "abbreviated","standaloneShorterWeekdays" to "short","standaloneNarrowWeekdays" to "narrow")
        private val QUARTER_FIELDS=arrayOf("quarters" to "quarters/format/wide","shortQuarters" to "quarters/format/abbreviated","narrowQuarters" to "quarters/format/narrow","standaloneQuarters" to "quarters/stand-alone/wide","standaloneShortQuarters" to "quarters/stand-alone/abbreviated","standaloneNarrowQuarters" to "quarters/stand-alone/narrow")
        private val PERIOD_KEYS=arrayOf("midnight","noon","morning1","afternoon1","evening1","night1","morning2","afternoon2","evening2","night2")
        private val LEAP_PATHS=arrayOf("monthPatterns/format/wide","monthPatterns/format/abbreviated","monthPatterns/format/narrow","monthPatterns/stand-alone/wide","monthPatterns/stand-alone/abbreviated","monthPatterns/stand-alone/narrow","monthPatterns/numeric/all")
        private val USAGES=arrayOf("OTHER","MONTH_FORMAT","MONTH_STANDALONE","MONTH_NARROW","DAY_FORMAT","DAY_STANDALONE","DAY_NARROW","ERA_WIDE","ERA_ABBREV","ERA_NARROW","ZONE_LONG","ZONE_SHORT","METAZONE_LONG","METAZONE_SHORT")
        private val CONTEXT_KEYS=arrayOf("month-format-except-narrow","month-standalone-except-narrow","month-narrow","day-format-except-narrow","day-standalone-except-narrow","day-narrow","era-name","era-abbr","era-narrow","zone-long","zone-short","metazone-long","metazone-short").zip(USAGES.drop(1)).toMap()
    }
}
