// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2008-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.number.LocalizedNumberFormatter
import io.github.heapy.icu4k.util.*

/** Legacy time-unit formatting and longest-match parsing across both display styles. */
@Deprecated("Use MeasureFormat for formatting time units.")
open class TimeUnitFormat : MeasureFormat {
    private var format: NumberFormat?=null
    private var locale: ULocale?=null
    private var style=0
    @kotlin.jvm.Transient private var timeUnitToCountToPatterns: MutableMap<TimeUnit,MutableMap<String,Array<Any?>>>?=null
    @kotlin.jvm.Transient private var pluralRules: PluralRules?=null
    @kotlin.jvm.Transient private var isReady=false

    constructor() : this(ULocale.getDefault(),FULL_NAME)
    constructor(locale: ULocale?) : this(locale,FULL_NAME)
    constructor(locale: ULocale?,style: Int) : super(locale,if(style==FULL_NAME) FormatWidth.WIDE else FormatWidth.SHORT) {
        format=super.getNumberFormatInternal()
        if(style<FULL_NAME || style>=TOTAL_STYLES) throw IllegalArgumentException("style should be either FULL_NAME or ABBREVIATED_NAME style")
        this.style=style
    }
    private constructor(locale: ULocale?,style: Int,format: NumberFormat?) : this(locale,style) {
        if(format!=null) setNumberFormat(format.clone())
    }
    protected constructor(other: TimeUnitFormat,@Suppress("UNUSED_PARAMETER") copyMarker: Unit) : super(other,Unit) {
        format=other.format;locale=other.locale;style=other.style
        timeUnitToCountToPatterns=other.timeUnitToCountToPatterns;pluralRules=other.pluralRules;isReady=other.isReady
    }
    open fun setLocale(locale: ULocale?): TimeUnitFormat {
        setLocale(locale,locale);clearCache()
        return this
    }
    open fun setNumberFormat(format: NumberFormat?): TimeUnitFormat {
        if(format===this.format) return this
        if(format==null) {
            if(locale==null) isReady=false else this.format=NumberFormat.getNumberInstance(locale)
        } else this.format=format
        clearCache()
        return this
    }
    override fun getNumberFormat(): NumberFormat = format!!.clone()
    internal override fun getNumberFormatInternal(): NumberFormat? = format
    internal override fun getNumberFormatter(): LocalizedNumberFormatter = (format as DecimalFormat).toNumberFormatter()
    override fun parseObject(source: String?,position: ParsePosition?): TimeUnitAmount? {
        if(!isReady) setup()
        var number: Number?=null;var unit: TimeUnit?=null
        val pos=position!!;val old=pos.index
        var newPosition=-1;var longest=0;var longestCount: String?=null
        // The source private HashMap has seven keys and sixteen buckets. Keep bucket
        // traversal and insertion-order ties when equal-length parses compete.
        val units=timeUnitToCountToPatterns!!.keys.sortedBy { val hash=it.hashCode();(hash xor (hash ushr 16)) and 15 }
        for(timeUnit in units) {
            val patterns=timeUnitToCountToPatterns!![timeUnit]!!
            for((count,pair) in patterns.entries.sortedBy { it.key }) for(styl in FULL_NAME until TOTAL_STYLES) {
                val pattern=pair[styl] as MessageFormat
                pos.errorIndex=-1;pos.index=old
                val parsed=pattern.parseObject(source,pos)
                if(pos.errorIndex!=-1 || pos.index==old) continue
                var temp: Number?=null
                val values=(parsed as Array<*>?)!!
                if(values.isNotEmpty()) {
                    val value=values[0]
                    if(value is Number) temp=value
                    else try { temp=format!!.parse(value!!.toString()) } catch(_:ParseException) { continue }
                }
                val distance=pos.index-old
                if(distance>longest) { number=temp;unit=timeUnit;newPosition=pos.index;longest=distance;longestCount=count }
            }
        }
        if(number==null && longest!=0) number=when(longestCount!!) { "zero"->0;"one"->1;"two"->2;else->3 }
        return if(longest==0) { pos.index=old;pos.errorIndex=0;null }
        else { pos.index=newPosition;pos.errorIndex=-1;TimeUnitAmount(number,unit) }
    }
    private fun setup() {
        if(locale==null) {
            locale=if(format!=null) format!!.getLocale(null) else ULocale.getDefault(ULocale.Category.FORMAT)
            setLocale(locale,locale)
        }
        if(format==null) format=NumberFormat.getNumberInstance(locale)
        pluralRules=PluralRules.forLocale(locale)
        timeUnitToCountToPatterns=LinkedHashMap()
        val keywords=pluralRules!!.getKeywords()
        setup("units/duration",FULL_NAME,keywords)
        setup("unitsShort/duration",ABBREVIATED_NAME,keywords)
        isReady=true
    }
    private class SetupSink(private val patterns: MutableMap<TimeUnit,MutableMap<String,Array<Any?>>>,private val style: Int,private val keywords: Set<String>,private val locale: ULocale?) : UResource.Sink() {
        private var beenHere=false
        override fun put(key: UResource.Key,value: UResource.Value,noFallback: Boolean) {
            if(beenHere) return
            beenHere=true
            val units=value.getTable()
            var i=0
            while(units.getKeyAndValue(i++,key,value)) {
                val unit=when(key.toString()) { "year"->TimeUnit.YEAR;"month"->TimeUnit.MONTH;"day"->TimeUnit.DAY;"hour"->TimeUnit.HOUR;"minute"->TimeUnit.MINUTE;"second"->TimeUnit.SECOND;"week"->TimeUnit.WEEK;else->continue }
                val counts=patterns.getOrPut(unit) { HashMap() }
                val table=value.getTable()
                var j=0
                while(table.getKeyAndValue(j++,key,value)) {
                    val plural=key.toString()
                    if(plural !in keywords) continue
                    val pair=counts.getOrPut(plural) { arrayOfNulls(2) }
                    if(pair[style]==null) pair[style]=MessageFormat(value.getString(),locale)
                }
            }
        }
    }
    private fun setup(resourceKey: String,style: Int,keywords: Set<String>) {
        try {
            val resource=UResourceBundle.getBundleInstance(ICUData.ICU_UNIT_BASE_NAME,locale) as ICUResourceBundle
            resource.getAllItemsWithFallback(resourceKey,SetupSink(timeUnitToCountToPatterns!!,style,keywords,locale))
        } catch(_:MissingResourceException) {}
        for(unit in TimeUnit.values()) {
            val counts=timeUnitToCountToPatterns!!.getOrPut(unit) { HashMap() }
            for(count in pluralRules!!.getKeywords()) if(counts[count]==null || counts[count]!![style]==null) searchInTree(resourceKey,style,unit,count,count,counts)
        }
    }
    private fun searchInTree(resourceKey: String,style: Int,unit: TimeUnit,sourceCount: String,searchCount: String,counts: MutableMap<String,Array<Any?>>) {
        var parent=locale
        val name=unit.toString()
        while(parent!=null) {
            try {
                val resource=UResourceBundle.getBundleInstance(ICUData.ICU_UNIT_BASE_NAME,parent) as ICUResourceBundle
                val pattern=resource.getWithFallback(resourceKey).getWithFallback(name).getStringWithFallback(searchCount)
                val message=MessageFormat(pattern,locale)
                val pair=counts.getOrPut(sourceCount) { arrayOfNulls(2) };pair[style]=message
                return
            } catch(_:MissingResourceException) {}
            parent=parent.getFallback()
        }
        if(parent==null && resourceKey=="unitsShort") {
            searchInTree("units",style,unit,sourceCount,searchCount,counts)
            if(counts[sourceCount]!=null && counts[sourceCount]!![style]!=null) return
        }
        if(searchCount=="other") {
            val pattern=when(unit) { TimeUnit.SECOND->"{0} s";TimeUnit.MINUTE->"{0} min";TimeUnit.HOUR->"{0} h";TimeUnit.WEEK->"{0} w";TimeUnit.DAY->"{0} d";TimeUnit.MONTH->"{0} m";TimeUnit.YEAR->"{0} y";else->null }
            val message=pattern?.let { MessageFormat(it,locale) }
            val pair=counts.getOrPut(sourceCount) { arrayOfNulls(2) };pair[style]=message
        } else searchInTree(resourceKey,style,unit,sourceCount,"other",counts)
    }
    public override fun clone(): TimeUnitFormat {
        val result=super.clone() as TimeUnitFormat
        result.format=format!!.clone()
        return result
    }
    protected override fun cloneNative(): MeasureFormat = TimeUnitFormat(this,Unit)
    private fun writeReplace(): Any = super.toTimeUnitProxy()
    private fun readResolve(): Any = TimeUnitFormat(locale,style,format)
    companion object {
        const val FULL_NAME=0
        const val ABBREVIATED_NAME=1
        private const val TOTAL_STYLES=2
        private const val serialVersionUID = -3707773153184971529L
    }
}
