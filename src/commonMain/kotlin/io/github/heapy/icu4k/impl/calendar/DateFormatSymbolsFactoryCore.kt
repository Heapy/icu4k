// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.CacheBase
import io.github.heapy.icu4k.impl.CalendarUtil
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.util.ULocale

/** DateFormatSymbols constructor/cache policy. Template and result creation are separate owner operations. */
internal class DateFormatSymbolsFactoryCore(
    private val createTemplateState: ()->DateFormatSymbolsStateCore,
    private val createResultState: ()->DateFormatSymbolsStateCore = createTemplateState,
    private val defaultFormatLocale: ()->ULocale = { ULocale.getDefault(ULocale.Category.FORMAT) },
    cache: CacheBase<String,DateFormatSymbolsStateCore,ULocale?>?=null,
) {
    private val cache=cache?:object: SoftCache<String,DateFormatSymbolsStateCore,ULocale?>() {
        override fun createInstance(key: String,data: ULocale?): DateFormatSymbolsStateCore = createTemplate(key,data)
    }
    fun createTemplate(key: String,locale: ULocale?): DateFormatSymbolsStateCore {
        val start=key.indexOf('+')+1
        val end=key.indexOf('+',start).let { if(it<0) key.length else it }
        val type=key.substring(start,end)
        return createTemplateState().also { it.data.initialize(locale,null,type) }
    }
    fun initializeData(target: DateFormatSymbolsStateCore,locale: ULocale?,type: String?) {
        val loc=locale!!
        var key=loc.getBaseName()+"+"+type
        val numbers=loc.getKeywordValue("numbers")
        if(!numbers.isNullOrEmpty()) key+="+$numbers"
        val template=cache.getInstance(key,loc)
        target.initializeFrom(template!!)
    }
    fun getInstance(): DateFormatSymbolsStateCore = getInstance(defaultFormatLocale())
    fun getInstance(locale: ULocale?): DateFormatSymbolsStateCore = createResultState().also {
        initializeData(it,locale,CalendarUtil.getCalendarType(locale))
    }
    fun forCalendarType(locale: ULocale?,type: String?): DateFormatSymbolsStateCore = createResultState().also { initializeData(it,locale,type) }
    fun forCalendarClassName(locale: ULocale?,name: String?): DateFormatSymbolsStateCore = createResultState().also {
        val full=name!!; val simple=full.substring(full.lastIndexOf('.')+1)
        val type=CALENDAR_CLASSES[simple]?:simple.replace("Calendar","").lowercase()
        initializeData(it,locale,type)
    }
    fun fromBundle(locale: ULocale?,bundle: ICUResourceBundle?): DateFormatSymbolsStateCore = createResultState().also {
        it.data.initialize(locale,bundle,CalendarUtil.getCalendarType(locale))
    }
    fun getAvailableULocales(): Array<ULocale>? = ICUResourceBundle.getAvailableULocales()
    companion object {
        private val CALENDAR_CLASSES=mapOf("GregorianCalendar" to "gregorian","JapaneseCalendar" to "japanese","BuddhistCalendar" to "buddhist","TaiwanCalendar" to "roc","PersianCalendar" to "persian","IslamicCalendar" to "islamic","HebrewCalendar" to "hebrew","ChineseCalendar" to "chinese","IndianCalendar" to "indian","CopticCalendar" to "coptic","EthiopicCalendar" to "ethiopic")
    }
}
