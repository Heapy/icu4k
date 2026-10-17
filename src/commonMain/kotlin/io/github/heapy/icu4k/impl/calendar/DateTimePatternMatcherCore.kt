// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2016, Google, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.TYPE_LIMIT
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.DAYPERIOD
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.getCanonicalChar
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.NONE
import io.github.heapy.icu4k.text.DateTimePatternGenerator.FormatParser
import io.github.heapy.icu4k.text.DateTimePatternGenerator.VariableField
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.types
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.MINUTE
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.FRACTIONAL_SECOND
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.SECOND
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.HOUR
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.EXTRA_FIELD
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.MISSING_FIELD
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.showMask
import kotlin.math.abs

/** Compact source skeleton storage; the flag models Java's optional assertions. */
internal class DateTimeSkeletonFields(private val assertionsEnabled: Boolean=false) {
    private val chars=ByteArray(TYPE_LIMIT)
    private val lengths=ByteArray(TYPE_LIMIT)
    private fun check(field: Int) { if(field !in 0 until TYPE_LIMIT) throw IndexOutOfBoundsException() }
    fun clear() { chars.fill(0); lengths.fill(0) }
    fun copyFieldFrom(other: DateTimeSkeletonFields,field: Int) { check(field); chars[field]=other.chars[field]; lengths[field]=other.lengths[field] }
    fun clearField(field: Int) { check(field); chars[field]=0; lengths[field]=0 }
    fun getFieldChar(field: Int): Char { check(field); return chars[field].toInt().toChar() }
    fun getFieldLength(field: Int): Int { check(field); return lengths[field].toInt() }
    fun populate(field: Int,value: String?) {
        val text=value!!
        if(assertionsEnabled) for(ch in text) if(ch!=text[0]) throw AssertionError()
        if(text.isEmpty()) throw IndexOutOfBoundsException()
        populate(field,text[0],text.length)
    }
    fun populate(field: Int,ch: Char,length: Int) {
        if(assertionsEnabled && (ch.code>127 || length>127)) throw AssertionError()
        check(field); chars[field]=ch.code.toByte(); lengths[field]=length.toByte()
    }
    fun isFieldEmpty(field: Int): Boolean { check(field); return lengths[field]==0.toByte() }
    override fun toString(): String=toString(false)
    fun toString(skipDayPeriod: Boolean): String=appendTo(StringBuilder(),false,skipDayPeriod).toString()
    fun toCanonicalString(skipDayPeriod: Boolean=false): String=appendTo(StringBuilder(),true,skipDayPeriod).toString()
    fun appendTo(builder: StringBuilder): StringBuilder=appendTo(builder,false,false)
    private fun appendTo(builder: StringBuilder,canonical: Boolean,skipDayPeriod: Boolean): StringBuilder {
        for(i in 0 until TYPE_LIMIT) if(!skipDayPeriod || i!=DAYPERIOD) appendFieldTo(i,builder,canonical)
        return builder
    }
    fun appendFieldTo(field: Int,builder: StringBuilder): StringBuilder=appendFieldTo(field,builder,false)
    private fun appendFieldTo(field: Int,builder: StringBuilder,canonical: Boolean): StringBuilder {
        check(field)
        var ch=chars[field].toInt().toChar()
        val length=lengths[field].toInt()
        if(canonical) ch=getCanonicalChar(field,ch)
        for(i in 0 until length) builder.append(ch)
        return builder
    }
    fun compareTo(other: DateTimeSkeletonFields): Int {
        for(i in 0 until TYPE_LIMIT) {
            val ch=chars[i]-other.chars[i]; if(ch!=0) return ch
            val length=lengths[i]-other.lengths[i]; if(length!=0) return length
        }
        return 0
    }
    override fun equals(other: Any?): Boolean=this===other || other is DateTimeSkeletonFields && compareTo(other)==0
    override fun hashCode(): Int=chars.contentHashCode() xor lengths.contentHashCode()
}

internal class DateTimePatternMatcherCore(private val assertionsEnabled: Boolean=false): Comparable<DateTimePatternMatcherCore> {
    val type=IntArray(TYPE_LIMIT)
    val original=DateTimeSkeletonFields(assertionsEnabled)
    val baseOriginal=DateTimeSkeletonFields(assertionsEnabled)
    private var addedDefaultDayPeriod=false
    fun fieldIsNumeric(field: Int): Boolean { if(field !in type.indices) throw IndexOutOfBoundsException(); return type[field]>0 }
    override fun toString(): String=original.toString(addedDefaultDayPeriod)
    fun toCanonicalString(): String=original.toCanonicalString(addedDefaultDayPeriod)
    fun getBasePattern(): String=baseOriginal.toString(addedDefaultDayPeriod)
    fun set(pattern: String?,parser: FormatParser?,allowDuplicateFields: Boolean): DateTimePatternMatcherCore {
        type.fill(NONE); original.clear(); baseOriginal.clear(); addedDefaultDayPeriod=false
        parser!!.set(pattern)
        for(obj in parser.getItems()) {
            if(obj !is VariableField) continue
            val value=obj.toString()
            val row=types[obj.canonicalIndex]
            val field=row[1]
            if(!original.isFieldEmpty(field)) {
                val first=original.getFieldChar(field)
                val second=value[0]
                if(allowDuplicateFields || (first=='r' && (second=='U' || second=='y')) || ((first=='U' || first=='y') && second=='r')) continue
                throw IllegalArgumentException("Conflicting fields:\t$first, $value\t in $pattern")
            }
            original.populate(field,value)
            val repeatChar=row[0].toChar()
            val repeatCount=if(repeatChar in "GEzvQ") 1 else row[3]
            baseOriginal.populate(field,repeatChar,repeatCount)
            var subField=row[2]
            if(subField>0) subField+=value.length
            type[field]=subField
        }
        if(!original.isFieldEmpty(MINUTE) && !original.isFieldEmpty(FRACTIONAL_SECOND) && original.isFieldEmpty(SECOND)) {
            for(row in types) if(row[1]==SECOND) {
                original.populate(SECOND,row[0].toChar(),row[3]); baseOriginal.populate(SECOND,row[0].toChar(),row[3])
                type[SECOND]=if(row[2]>0) row[2]+1 else row[2]
                break
            }
        }
        if(!original.isFieldEmpty(HOUR)) {
            if(original.getFieldChar(HOUR)=='h' || original.getFieldChar(HOUR)=='K') {
                if(original.isFieldEmpty(DAYPERIOD)) for(row in types) if(row[1]==DAYPERIOD) {
                    original.populate(DAYPERIOD,row[0].toChar(),row[3]); baseOriginal.populate(DAYPERIOD,row[0].toChar(),row[3])
                    type[DAYPERIOD]=row[2]; addedDefaultDayPeriod=true; break
                }
            } else if(!original.isFieldEmpty(DAYPERIOD)) { original.clearField(DAYPERIOD); baseOriginal.clearField(DAYPERIOD); type[DAYPERIOD]=NONE }
        }
        return this
    }
    fun getFieldMask(): Int {
        var result=0
        for(i in type.indices) if(type[i]!=0) result=result or (1 shl i)
        return result
    }
    fun extractFrom(source: DateTimePatternMatcherCore,fieldMask: Int) {
        // Deliberately leaves baseOriginal and addedDefaultDayPeriod unchanged.
        for(i in type.indices) if(fieldMask and (1 shl i)!=0) { type[i]=source.type[i]; original.copyFieldFrom(source.original,i) }
            else { type[i]=NONE; original.clearField(i) }
    }
    fun getDistance(other: DateTimePatternMatcherCore,includeMask: Int,info: DateTimePatternDistanceInfo): Int {
        var result=0; info.clear()
        for(i in 0 until TYPE_LIMIT) {
            val mine=if(includeMask and (1 shl i)==0) 0 else type[i]
            val theirs=other.type[i]
            if(mine==theirs) continue
            if(mine==0) { result+=EXTRA_FIELD; info.addExtra(i) }
            else if(theirs==0) { result+=MISSING_FIELD; info.addMissing(i) }
            else result+=abs(mine-theirs)
        }
        return result
    }
    override fun compareTo(other: DateTimePatternMatcherCore): Int {
        val result=original.compareTo(other.original)
        return if(result>0) -1 else if(result<0) 1 else 0
    }
    override fun equals(other: Any?): Boolean=this===other || other is DateTimePatternMatcherCore && original==other.original
    override fun hashCode(): Int=original.hashCode()
}

internal class DateTimePatternDistanceInfo {
    var missingFieldMask=0
    var extraFieldMask=0
    fun clear() { missingFieldMask=0; extraFieldMask=0 }
    fun setTo(other: DateTimePatternDistanceInfo) { missingFieldMask=other.missingFieldMask; extraFieldMask=other.extraFieldMask }
    fun addMissing(field: Int) { missingFieldMask=missingFieldMask or (1 shl field) }
    fun addExtra(field: Int) { extraFieldMask=extraFieldMask or (1 shl field) }
    override fun toString(): String="missingFieldMask: ${showMask(missingFieldMask)}, extraFieldMask: ${showMask(extraFieldMask)}"
}
