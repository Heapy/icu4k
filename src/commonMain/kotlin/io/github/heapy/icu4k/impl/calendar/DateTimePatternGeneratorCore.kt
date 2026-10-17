// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2016, Google, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.text.DateTimePatternGenerator

import io.github.heapy.icu4k.impl.SimpleFormatterImpl
import io.github.heapy.icu4k.impl.createCalendarCacheLock
import io.github.heapy.icu4k.text.DateTimePatternGenerator.FormatParser
import io.github.heapy.icu4k.text.DateTimePatternGenerator.VariableField
import kotlin.concurrent.Volatile

/** Pattern registration, best-match search and field adjustment; locale initialization is separate. */
internal open class DateTimePatternGeneratorCore(private val assertionsEnabled: Boolean=false,monitorOwner: Any?=null) {
    enum class DisplayWidth { WIDE, ABBREVIATED, NARROW }
    enum class HourCycle { HOUR_CYCLE_12,HOUR_CYCLE_23,HOUR_CYCLE_24,HOUR_CYCLE_11 }
    private class PatternValue(val pattern: String?,val specified: Boolean)
    private class Match(var pattern: String?,var matcher: DateTimePatternMatcherCore?)
    private val monitor=createCalendarCacheLock(monitorOwner?:this)
    private var skeletons=PatternOrderedMap<DateTimePatternMatcherCore,PatternValue>()
    private var bases=PatternOrderedMap<String,PatternValue>()
    private var decimal: String?="?"
    private var dateTimeFormats=arrayOf<String?>("{1} {0}","{1} {0}","{1} {0}","{1} {0}")
    private var appendFormats=arrayOfNulls<String>(16)
    private var fieldNames=Array(16) { arrayOfNulls<String>(3) }
    private var hour='H'
    private var allowedHours: Array<String?>?=null
    private var availableKeys=HashSet<String?>()
    @Volatile private var frozen=false
    private val current=DateTimePatternMatcherCore(assertionsEnabled)
    private val parser=FormatParser()
    private val distanceInfo=DateTimePatternDistanceInfo()
    open fun isFrozen(): Boolean=frozen
    open fun freeze(): DateTimePatternGeneratorCore { frozen=true; return this }
    private fun checkFrozen() { if(isFrozen()) throw UnsupportedOperationException("Attempt to modify frozen object") }
    internal fun copyStateTo(target: DateTimePatternGeneratorCore) {
        target.skeletons=skeletons.copy(); target.bases=bases.copy(); target.decimal=decimal
        target.dateTimeFormats=dateTimeFormats.copyOf(); target.appendFormats=appendFormats.copyOf(); target.fieldNames=fieldNames.copyOf()
        target.hour=hour; target.allowedHours=allowedHours; target.availableKeys=availableKeys
    }
    internal fun clearFrozen() { frozen=false }
    open fun copy(monitorOwner: Any?=null): DateTimePatternGeneratorCore=DateTimePatternGeneratorCore(assertionsEnabled,monitorOwner).also { copyStateTo(it) }
    open fun cloneAsThawed(): DateTimePatternGeneratorCore { val result=copy(); frozen=false; return result }
    open fun getDefaultHourFormatChar(): Char=hour
    open fun setDefaultHourFormatChar(value: Char) { hour=value }
    fun setAllowedHourFormats(value: Array<String?>?) { allowedHours=value }
    fun initializeHourFormats(locale: io.github.heapy.icu4k.util.ULocale?,data: DateTimePatternHourDataCore) {
        val hours=data.get(locale); hour=hours.preferred; allowedHours=hours.allowed
    }
    open fun getDefaultHourCycle(): HourCycle=when(getDefaultHourFormatChar()) {
        'h' -> HourCycle.HOUR_CYCLE_12; 'H' -> HourCycle.HOUR_CYCLE_23; 'k' -> HourCycle.HOUR_CYCLE_24; 'K' -> HourCycle.HOUR_CYCLE_11
        else -> throw AssertionError("should be unreachable")
    }
    open fun setDecimal(value: String?) { checkFrozen(); decimal=value }
    open fun getDecimal(): String?=decimal
    open fun setDateTimeFormat(value: String?) { checkFrozen(); for(style in 0..3) setDateTimeFormat(style,value) }
    open fun setDateTimeFormat(style: Int,value: String?) { style(style); checkFrozen(); dateTimeFormats[style]=value }
    open fun getDateTimeFormat(): String?=getDateTimeFormat(2)
    open fun getDateTimeFormat(style: Int): String? { style(style); return dateTimeFormats[style] }
    private fun style(value: Int) { if(value !in 0..3) throw IllegalArgumentException("Illegal style here: $value") }
    private fun field(value: Int) { if(value !in 0..15) throw IndexOutOfBoundsException() }
    open fun setAppendItemFormat(field: Int,value: String?) { checkFrozen(); field(field); appendFormats[field]=value }
    open fun getAppendItemFormat(field: Int): String? { field(field); return appendFormats[field] }
    open fun setAppendItemName(field: Int,value: String?) { setFieldDisplayName(field,DisplayWidth.WIDE,value) }
    open fun getAppendItemName(field: Int): String?=getFieldDisplayName(field,DisplayWidth.WIDE)
    fun setFieldDisplayName(field: Int,width: DisplayWidth?,value: String?) { checkFrozen(); if(field in 0..15) fieldNames[field][width!!.ordinal]=value }
    open fun getFieldDisplayName(field: Int,width: DisplayWidth?): String?=if(field !in 0..15) "" else fieldNames[field][width!!.ordinal]
    fun setAvailableFormat(key: String?) { checkFrozen(); availableKeys.add(key) }
    fun isAvailableFormatSet(key: String?): Boolean=key in availableKeys
    fun fillInMissing() {
        for(i in 0..15) {
            if(getAppendItemFormat(i)==null) setAppendItemFormat(i,"{0} ├{2}: {1}┤")
            if(getFieldDisplayName(i,DisplayWidth.WIDE)==null) setFieldDisplayName(i,DisplayWidth.WIDE,"F$i")
            if(getFieldDisplayName(i,DisplayWidth.ABBREVIATED)==null) setFieldDisplayName(i,DisplayWidth.ABBREVIATED,getFieldDisplayName(i,DisplayWidth.WIDE))
            if(getFieldDisplayName(i,DisplayWidth.NARROW)==null) setFieldDisplayName(i,DisplayWidth.NARROW,getFieldDisplayName(i,DisplayWidth.ABBREVIATED))
        }
    }
    fun addCanonicalItems() { val info=DateTimePatternGenerator.PatternInfo(); for(item in DateTimePatternSyntaxCore.CANONICAL_ITEMS) addPattern(item,false,info) }
    open fun addPattern(pattern: String?,override: Boolean,info: DateTimePatternGenerator.PatternInfo?): DateTimePatternGeneratorCore=addPatternWithSkeleton(pattern,null,override,info)
    open fun addPatternWithSkeleton(pattern: String?,skeleton: String?,override: Boolean,info: DateTimePatternGenerator.PatternInfo?): DateTimePatternGeneratorCore {
        checkFrozen()
        val matcher=DateTimePatternMatcherCore(assertionsEnabled).set(skeleton?:pattern,parser,false)
        val base=matcher.getBasePattern()
        val previousBase=bases[base]
        if(previousBase!=null && (!previousBase.specified || (skeleton!=null && !override))) {
            info!!.status=1; info.conflictingPattern=previousBase.pattern
            if(!override) return this
        }
        val previous=skeletons[matcher]
        if(previous!=null) { info!!.status=2; info.conflictingPattern=previous.pattern; if(!override || (skeleton!=null && previous.specified)) return this }
        info!!.status=0; info.conflictingPattern=""
        val value=PatternValue(pattern,skeleton!=null)
        skeletons[matcher]=value; bases[base]=value
        return this
    }
    open fun getSkeleton(pattern: String?): String=monitor.withLock { current.set(pattern,parser,false).toString() }
    open fun getSkeletonAllowingDuplicates(pattern: String?): String=monitor.withLock { current.set(pattern,parser,true).toString() }
    open fun getCanonicalSkeletonAllowingDuplicates(pattern: String?): String=monitor.withLock { current.set(pattern,parser,true).toCanonicalString() }
    open fun getBaseSkeleton(pattern: String?): String=monitor.withLock { current.set(pattern,parser,false).getBasePattern() }
    open fun getSkeletons(result: MutableMap<String,String?>?): MutableMap<String,String?> {
        val output=result?:LinkedHashMap()
        for((key,value) in skeletons) if(value.pattern !in DateTimePatternSyntaxCore.CANONICAL_ITEMS) output[key.toString()]=value.pattern
        return output
    }
    open fun getBaseSkeletons(result: MutableSet<String>?): MutableSet<String> {
        val output=result?:HashSet(); output.addAll(bases.keys); return output
    }
    open fun replaceFieldTypes(pattern: String?,skeleton: String?): String=replaceFieldTypes(pattern,skeleton,0)
    open fun replaceFieldTypes(pattern: String?,skeleton: String?,options: Int): String=monitor.withLock { adjust(Match(pattern,null),current.set(skeleton,parser,false),0,options) }
    open fun getBestPattern(skeleton: String?): String=getBest(skeleton,null,0)
    open fun getBestPattern(skeleton: String?,options: Int): String=getBest(skeleton,null,options)
    private fun getBest(skeleton: String?,skip: DateTimePatternMatcherCore?,options: Int): String {
        val flags=intArrayOf(0)
        val mapped=mapSkeleton(skeleton,flags)
        val parts=monitor.withLock {
            current.set(mapped,parser,false)
            val best=getBestRaw(current,-1,distanceInfo,skip)
            if(distanceInfo.missingFieldMask==0 && distanceInfo.extraFieldMask==0) return@withLock Triple(adjust(best,current,flags[0],options),null,true)
            val needed=current.getFieldMask()
            Triple(getBestAppending(current,needed and 0x3ff,distanceInfo,skip,flags[0],options),getBestAppending(current,needed and 0xfc00,distanceInfo,skip,flags[0],options),false)
        }
        val date=parts.first; val time=parts.second
        if(parts.third) return date!!
        if(date==null) return time?:""
        if(time==null) return date
        val canonical=current.toCanonicalString()
        val position=canonical.indexOf('M')
        val length=if(position>=0) 1+canonical.lastIndexOf('M')-position else 0
        val style=if(length==4) if('E' in canonical) 0 else 1 else if(length==3) 2 else 3
        return SimpleFormatterImpl.formatRawPattern(getDateTimeFormat(style)!!,2,2,time,date)
    }
    private fun mapSkeleton(value: String?,flags: IntArray): String {
        val text=value!!; val result=StringBuilder(); var quoted=false; var i=0
        while(i<text.length) {
            val ch=text[i]
            if(ch=='\'') quoted=!quoted
            else if(!quoted) {
                if(ch=='j' || ch=='C') {
                    var extra=0
                    while(i+1<text.length && text[i+1]==ch) { extra++; i++ }
                    var hourLength=1+(extra and 1)
                    var periodLength=if(extra<2) 1 else 3+(extra shr 1)
                    var hourChar='h'; var periodChar='a'
                    if(ch=='j') hourChar=hour else {
                        val formats=allowedHours!!; if(formats.isEmpty()) throw IndexOutOfBoundsException()
                        val best=formats[0]!!; if(best.isEmpty()) throw IndexOutOfBoundsException()
                        hourChar=best[0]; if(best.last()=='b' || best.last()=='B') periodChar=best.last()
                    }
                    if(hourChar=='H' || hourChar=='k') periodLength=0
                    while(periodLength-->0) result.append(periodChar)
                    while(hourLength-->0) result.append(hourChar)
                } else if(ch=='J') { result.append('H'); flags[0]=flags[0] or CAP_J }
                else result.append(ch)
            }
            i++
        }
        return result.toString()
    }
    private fun getBestRaw(source: DateTimePatternMatcherCore,mask: Int,info: DateTimePatternDistanceInfo,skip: DateTimePatternMatcherCore?): Match {
        var bestDistance=Int.MAX_VALUE; var bestMissing=Int.MIN_VALUE; val best=Match("",null); val temporary=DateTimePatternDistanceInfo()
        for((trial,value) in skeletons) {
            if(trial==skip) continue
            val distance=source.getDistance(trial,mask,temporary)
            if(distance<bestDistance || (distance==bestDistance && bestMissing<temporary.missingFieldMask)) {
                bestDistance=distance; bestMissing=temporary.missingFieldMask; best.pattern=value.pattern; best.matcher=if(value.specified) trial else null
                info.setTo(temporary); if(distance==0) break
            }
        }
        return best
    }
    private fun getBestAppending(source: DateTimePatternMatcherCore,missing: Int,info: DateTimePatternDistanceInfo,skip: DateTimePatternMatcherCore?,initialFlags: Int,options: Int): String? {
        if(missing==0) return null
        var flags=initialFlags
        val matched=getBestRaw(source,missing,info,skip)
        var result=adjust(matched,source,flags,options)
        while(info.missingFieldMask!=0) {
            if(info.missingFieldMask and 0x6000==0x4000 && missing and 0x6000==0x6000) {
                matched.pattern=result; flags=flags or FIX_FRACTION
                result=adjust(matched,source,flags,options); info.missingFieldMask=info.missingFieldMask and 0x4000.inv(); continue
            }
            val starting=info.missingFieldMask
            val next=getBestRaw(source,info.missingFieldMask,info,skip)
            val temp=adjust(next,source,flags,options)
            var found=starting and info.missingFieldMask.inv(); var top=-1
            while(found!=0) { found=found ushr 1; top++ }
            field(top)
            result=SimpleFormatterImpl.formatRawPattern(appendFormats[top]!!,2,3,result,temp,"'"+fieldNames[top][0]+"'")
        }
        return result
    }
    private fun adjust(match: Match,request: DateTimePatternMatcherCore,flags: Int,options: Int): String {
        parser.set(match.pattern)
        return buildString {
            for(item in parser.getItems()) {
                if(item is String) { append(parser.quoteLiteral(item)); continue }
                val variable=item as VariableField
                var fieldText=StringBuilder(variable.toString()); val type=variable.getType()
                if(flags and FIX_FRACTION!=0 && type==13) { fieldText.append(decimal); request.original.appendFieldTo(14,fieldText) }
                else if(request.type[type]!=0) {
                    val required=request.original.getFieldChar(type)
                    var requiredLength=request.original.getFieldLength(type)
                    if(required=='E' && requiredLength<3) requiredLength=3
                    var length=requiredLength
                    val specified=match.matcher
                    if((type==11 || type==12 || type==13) && options and (1 shl type)==0) length=fieldText.length
                    else if(specified!=null && required!='c' && required!='e') {
                        val skeletonLength=specified.original.getFieldLength(type)
                        val patternNumeric=variable.isNumeric(); val requestNumeric=request.fieldIsNumeric(type)
                        if(skeletonLength==requiredLength || (patternNumeric && !requestNumeric) || (requestNumeric && !patternNumeric)) length=fieldText.length
                    }
                    var char=if(type!=11 && type!=3 && type!=6 && (type!=1 || required=='Y')) required else fieldText[0]
                    if(char=='E' && length<3) char='e'
                    if(type==11) {
                        char=when {
                            flags and CAP_J!=0 || required==hour -> hour
                            required=='h' && hour=='K' -> 'K'
                            required=='H' && hour=='k' -> 'k'
                            required=='k' && hour=='H' -> 'H'
                            required=='K' && hour=='h' -> 'h'
                            else -> char
                        }
                    }
                    fieldText=StringBuilder(); for(i in length downTo 1) fieldText.append(char)
                }
                append(fieldText)
            }
        }
    }
    open fun getRedundants(result: MutableCollection<String?>?): MutableCollection<String?> = monitor.withLock {
        val output=result?:LinkedHashSet()
        for((key,value) in skeletons) if(value.pattern !in DateTimePatternSyntaxCore.CANONICAL_ITEMS) {
            val trial=getBest(key.toString(),key,0)
            if(trial==value.pattern) output.add(value.pattern)
        }
        output
    }
    open fun getFields(pattern: String?): String {
        parser.set(pattern)
        return buildString {
            for(item in parser.getItems()) {
                if(item is String) append(parser.quoteLiteral(item))
                else append('{').append(DateTimePatternSyntaxCore.getName(item.toString())).append('}')
            }
        }
    }
    open fun skeletonsAreSimilar(id: String?,skeleton: String?): Boolean {
        if(id!! == skeleton) return true
        val first=fieldSet(id)
        val second=fieldSet(skeleton)
        if(first.size!=second.size) return false
        val other=second.iterator()
        for(item in first) {
            val a=DateTimePatternSyntaxCore.getCanonicalIndex(item,false)
            val b=DateTimePatternSyntaxCore.getCanonicalIndex(other.next(),false)
            if(a<0 || b<0) throw IndexOutOfBoundsException()
            if(DateTimePatternSyntaxCore.types[a][1]!=DateTimePatternSyntaxCore.types[b][1]) return false
        }
        return true
    }
    private fun fieldSet(pattern: String?): List<String> {
        val result=HashSet<String>()
        for(value in parser.set(pattern).getItems()) {
            val item=value.toString()
            if(!item.startsWith('G') && !item.startsWith('a')) result.add(item)
        }
        return result.sorted()
    }
    companion object {
        fun isSingleField(skeleton: String?): Boolean {
            val value=skeleton!!
            if(value.isEmpty()) throw IndexOutOfBoundsException()
            val first=value[0]
            for(i in 1 until value.length) if(value[i]!=first) return false
            return true
        }
        private const val FIX_FRACTION=1; private const val CAP_J=2
        fun empty(assertionsEnabled: Boolean=false,monitorOwner: Any?=null): DateTimePatternGeneratorCore = DateTimePatternGeneratorCore(assertionsEnabled,monitorOwner).apply { addCanonicalItems(); fillInMissing() }
    }
}
