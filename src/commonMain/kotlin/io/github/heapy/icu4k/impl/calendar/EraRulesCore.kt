// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.impl.CalType
import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.ICUException
import io.github.heapy.icu4k.util.UResourceBundle

/** EraRules data validation, sparse era codes and Gregorian date lookup. */
internal class EraRulesCore private constructor(private val starts: IntArray,private val minEra: Int,private val numEras: Int,owner: Owner) {
    interface Owner {
        fun currentTimeMillis(): Long
        /** Acquire the current default zone, then get its offset at the supplied instant. */
        fun defaultZoneOffset(time: Long): Int
    }
    private val currentEra: Int
    init {
        var time=owner.currentTimeMillis()
        time+=owner.defaultZoneOffset(time)
        val date=Grego.timeToFields(time,null)
        currentEra=getEraCode(date[0],date[1]+1,date[2])
    }
    fun getNumberOfEras(): Int=numEras
    fun getMaxEraCode(): Int=minEra+starts.size-1
    fun getCurrentEraCode(): Int=currentEra
    private fun start(era: Int): Int {
        var date=0
        if(era>=minEra) {
            val index=era-minEra
            if(index<starts.size) {
                if(index<0) throw IndexOutOfBoundsException()
                date=starts[index]
            }
        }
        if(date!=0) return date
        throw IllegalArgumentException("eraCode is not found in our data")
    }
    fun getStartDate(era: Int,fillIn: IntArray?): IntArray=decodeDate(start(era),fillIn)
    fun getStartYear(era: Int): Int=getStartDate(era,null)[0]
    fun getEraCode(year: Int,month: Int,day: Int): Int {
        if(month !in 1..12 || day !in 1..31) throw IllegalArgumentException("Illegal date - year:${year}month:${month}day:$day")
        if(numEras>1 && starts.isEmpty()) throw IndexOutOfBoundsException()
        if(numEras>1 && starts[starts.size-1]==MIN_ENCODED_START) {
            for(index in starts.indices) if(starts[index]!=0 && compare(starts[index],year,month,day)<=0) return minEra+index
        }
        for(index in starts.lastIndex downTo 0) if(starts[index]!=0 && compare(starts[index],year,month,day)<=0) return minEra+index
        return minEra
    }
    companion object {
        const val MIN_ENCODED_START: Int=-2147483391
        fun getInstance(type: CalType,includeTentative: Boolean,owner: Owner): EraRulesCore=getInstance(type.getId(),includeTentative,owner)
        fun getInstance(id: String,includeTentative: Boolean,owner: Owner): EraRulesCore {
            val eras=UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"supplementalData")["calendarData"][id]["eras"]
            return fromResource(id,eras,includeTentative,owner)
        }
        fun fromResource(id: String,eras: UResourceBundle,includeTentative: Boolean,owner: Owner): EraRulesCore {
            var count=eras.getSize()
            var firstTentative=Int.MAX_VALUE
            val dates=ArrayList<Int>(count)
            val iterator=eras.getIterator()
            while(iterator.hasNext()) {
                val rule=iterator.next()
                val key=rule.getKey()
                val era=try { parseEraCode(key) } catch(_: NumberFormatException) { throw ICUException("Invalid era rule key:$key in era rule data for $id") }
                if(era<0) throw ICUException("Era rule key:$key in era rule data for $id must be >= 0")
                while(dates.size<era+1) dates.add(0)
                if(era !in dates.indices) throw IndexOutOfBoundsException()
                if(dates[era]!=0) throw ICUException("Duplicated era rule for rule key:$key in era rule data for $id")
                var named=true
                var hasEnd=false
                val rules=rule.getIterator()
                while(rules.hasNext()) {
                    val value=rules.next()
                    when(value.getKey()) {
                        "start" -> {
                            val fields=value.getIntVector()
                            if(fields.size!=3 || fields[0] !in -32768..32767 || fields[1] !in 1..12 || fields[2] !in 1..31)
                                throw ICUException("Invalid era rule date data:${fields.joinToString(prefix="[",postfix="]")} in era rule data for $id")
                            dates[era]=encodeDate(fields[0],fields[1],fields[2])
                        }
                        "named" -> if(value.getString()=="false") named=false
                        "end" -> hasEnd=true
                    }
                }
                if(dates[era]==0) {
                    if(hasEnd) dates[era]=MIN_ENCODED_START
                    else throw ICUException("Missing era start/end rule date for key:$key in era rule data for $id")
                }
                if(named) {
                    if(era>=firstTentative) throw ICUException("Non-tentative era($era) must be placed before the first tentative era")
                } else if(era<firstTentative) firstTentative=era
            }
            if(!includeTentative) {
                while(firstTentative<dates.size) { if(dates.last()!=0) count--; dates.removeAt(dates.lastIndex) }
                while(dates.isNotEmpty() && dates.last()==0) dates.removeAt(dates.lastIndex)
            }
            var minEra=0
            while(dates.isNotEmpty() && dates[0]==0) { dates.removeAt(0); minEra++ }
            return EraRulesCore(dates.toIntArray(),minEra,count,owner)
        }
        private fun parseEraCode(key: String?): Int {
            if(key==null) throw NumberFormatException()
            val normalized=buildString(key.length) {
                for(ch in key) {
                    val digit=if(ch.code>127) UCharacter.digit(ch.code,10) else -1
                    append(if(digit>=0) ('0'.code+digit).toChar() else ch)
                }
            }
            return normalized.toInt()
        }
        private fun encodeDate(year: Int,month: Int,day: Int): Int=(year shl 16) or (month shl 8) or day
        private fun decodeDate(date: Int,fillIn: IntArray?): IntArray {
            val result=if(fillIn!=null && fillIn.size>=3) fillIn else IntArray(3)
            result[0]=if(date==MIN_ENCODED_START) Int.MIN_VALUE else date shr 16
            result[1]=if(date==MIN_ENCODED_START) 1 else (date and 0xff00) shr 8
            result[2]=if(date==MIN_ENCODED_START) 1 else date and 0xff
            return result
        }
        private fun compare(date: Int,year: Int,month: Int,day: Int): Int {
            if(year< -32768) {
                if(date==MIN_ENCODED_START) return if(year>Int.MIN_VALUE || month>1 || day>1) -1 else 0
                return 1
            }
            if(year>32767) return -1
            return date.compareTo(encodeDate(year,month,day))
        }
    }
}
