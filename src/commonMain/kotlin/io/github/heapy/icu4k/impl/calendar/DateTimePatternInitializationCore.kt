// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.ICUResourceLoader
import io.github.heapy.icu4k.impl.defaultICUResourceLoader
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.ULocale

/** Generator initData order. Normal initialization requires the actual Calendar owner. */
internal class DateTimePatternInitializationCore(loader: ICUResourceLoader=defaultICUResourceLoader()) {
    fun interface CalendarPatterns { fun getDateAtTimePattern(style: Int): String? }
    private val resources=DateTimePatternResourceCore(loader)
    private val hours=DateTimePatternHourDataCore(loader)

    fun initializeNoStandardPatterns(core: DateTimePatternGeneratorCore,locale: ULocale?) {
        initialize(core,locale,true,null)
    }
    fun initialize(core: DateTimePatternGeneratorCore,locale: ULocale?,createCalendar: (ULocale)->CalendarPatterns) {
        initialize(core,locale,false,createCalendar)
    }
    private fun initialize(core: DateTimePatternGeneratorCore,locale: ULocale?,skipStandard: Boolean,createCalendar: ((ULocale)->CalendarPatterns)?) {
        val info=DateTimePatternGeneratorCore.PatternInfo()
        core.addCanonicalItems()
        val loc=locale!!
        if(!skipStandard) resources.addStandardPatterns(core,loc,info)
        resources.addCLDRData(core,loc,info)
        if(!skipStandard) {
            val calendar=createCalendar!!(loc)
            for(style in 0..3) core.setDateTimeFormat(style,calendar.getDateAtTimePattern(style))
        } else core.setDateTimeFormat("{1} {0}")
        core.setDecimal(DecimalFormatSymbols(loc).getDecimalSeparator().toString())
        core.initializeHourFormats(loc,hours)
        core.fillInMissing()
    }
    fun noStandardPatterns(locale: ULocale?,assertionsEnabled: Boolean=false): DateTimePatternGeneratorCore =
        DateTimePatternGeneratorCore(assertionsEnabled).also { initializeNoStandardPatterns(it,locale) }
}
