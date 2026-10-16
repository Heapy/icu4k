// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Resource-loading steps of DateTimePatternGenerator, in source fallback order. */
internal class DateTimePatternResourceCore(private val loader: ICUResourceLoader=defaultICUResourceLoader()) {
    fun calendarType(locale: ULocale): String = locale.getKeywordValue("calendar")
        ?: CalendarLocaleCore.getKeywordValuesForLocale("calendar",locale,true)[0]
    private fun bundle(locale: ULocale): ICUResourceBundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,locale,loader) as ICUResourceBundle
    fun addStandardPatterns(core: DateTimePatternGeneratorCore,locale: ULocale,info: DateTimePatternGeneratorCore.PatternInfo) {
        val rb=bundle(locale); val path="calendar/${calendarType(locale)}/DateTimePatterns"
        val patterns=rb.getWithFallback(path)
        fun wrong(): Nothing=throw MissingResourceException("Resource in wrong format","ICUResourceBundle",path)
        if(patterns.getType()!=UResourceBundle.ARRAY || patterns.getSize()<8) wrong()
        for(i in 0..7) {
            val resource=patterns[i]
            val pattern=when(resource.getType()) {
                UResourceBundle.STRING -> resource.getString()
                UResourceBundle.ARRAY -> resource.getString(0)
                else -> wrong()
            }
            core.addPattern(pattern,false,info)
        }
    }
    fun addCLDRData(core: DateTimePatternGeneratorCore,locale: ULocale,info: DateTimePatternGeneratorCore.PatternInfo) {
        val rb=bundle(locale); val type=calendarType(locale)
        try {
            rb.getAllChildrenWithFallback("calendar/$type/appendItems",object: UResource.Sink() {
                override fun put(key: UResource.Key,value: UResource.Value,noFallback: Boolean) {
                    val field=appendFormatNumber(key.toString())
                    if(field>=0 && core.getAppendItemFormat(field)==null) core.setAppendItemFormat(field,value.toString())
                }
            })
        } catch(_: MissingResourceException) { }
        try {
            rb.getAllChildrenWithFallback("fields",object: UResource.Sink() {
                override fun put(key: UResource.Key,value: UResource.Value,noFallback: Boolean) {
                    val number=fieldAndWidthNumber(key.toString())
                    if(number<0) return
                    val field=number/3; val width=DateTimePatternGeneratorCore.DisplayWidth.entries[number%3]
                    if(value.getTable().findValue("dn",value) && core.getFieldDisplayName(field,width)==null) core.setFieldDisplayName(field,width,value.toString())
                }
            })
        } catch(_: MissingResourceException) { }
        try {
            rb.getAllChildrenWithFallback("calendar/$type/availableFormats",object: UResource.Sink() {
                override fun put(key: UResource.Key,value: UResource.Value,noFallback: Boolean) {
                    val format=key.toString()
                    if(!core.isAvailableFormatSet(format)) {
                        core.setAvailableFormat(format)
                        core.addPatternWithSkeleton(value.toString(),format,true,info)
                    }
                }
            })
        } catch(_: MissingResourceException) { }
    }
    companion object {
        private val APPEND=arrayOf("Era","Year","Quarter","Month","Week","*","Day-Of-Week","Day","*","*","*","Hour","Minute","Second","*","Timezone")
        private val NAMES=arrayOf("era","year","quarter","month","week","weekOfMonth","weekday","day","dayOfYear","weekdayOfMonth","dayperiod","hour","minute","second","*","zone")
        private val WIDTHS=arrayOf("","-short","-narrow")
        fun appendFormatNumber(key: String?): Int=APPEND.indexOf(key)
        private fun fieldAndWidthNumber(key: String): Int {
            for(i in NAMES.indices) for(j in WIDTHS.indices) if(NAMES[i]+WIDTHS[j]==key) return i*3+j
            return -1
        }
    }
}
