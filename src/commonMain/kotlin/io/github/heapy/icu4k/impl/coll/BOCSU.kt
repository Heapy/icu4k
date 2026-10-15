// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.util.ByteArrayWrapper

/** Binary ordered compression for the identical level of collation sort keys. */
internal object BOCSU {
    fun writeIdenticalLevelRun(initialPrev: Int,s: CharSequence?,start: Int,length: Int,sink: ByteArrayWrapper?): Int {
        var prev=initialPrev;var i=start
        while(i<length) {
            val desired=s!!.length*2
            ensureAppendCapacity(sink!!,16,desired)
            val buffer=sink.bytes!!;val capacity=buffer.size;var p=sink.size;val lastSafe=capacity-4
            while(i<length && p<=lastSafe) {
                prev=if(prev<0x4e00 || prev>=0xa000) (prev and -128)-REACH_NEG_1 else 0x9fff-REACH_POS_2
                val c=codePointAt(s,i);i+=if(c>=0x10000) 2 else 1
                if(c == 0xfffe) { put(buffer,p++,2);prev=0 } else { p=writeDiff(c-prev,buffer,p);prev=c }
            }
            sink.size=p
        }
        return prev
    }
    private fun ensureAppendCapacity(sink: ByteArrayWrapper,minCapacity: Int,desired: Int) {
        if(sink.bytes!!.size-sink.size>=minCapacity) return
        val capacity=if(desired<minCapacity) minCapacity else desired
        sink.ensureCapacity(sink.size+capacity)
    }
    private fun codePointAt(s: CharSequence,index: Int): Int {
        if(s is String && (index<0 || index>=s.length)) throw IndexOutOfBoundsException()
        val lead=s[index].code
        if(lead in 0xd800..0xdbff && index+1<s.length) {
            val trail=s[index+1].code
            if(trail in 0xdc00..0xdfff) return ((lead-0xd800) shl 10)+trail-0xdc00+0x10000
        }
        return lead
    }
    private const val MIN=3
    private const val MAX=255
    private const val MIDDLE=129
    private const val TAIL_COUNT=253
    private const val REACH_POS_1=80
    private const val REACH_NEG_1=-80
    private const val REACH_POS_2=42*TAIL_COUNT+42-1
    private const val REACH_NEG_2=-REACH_POS_2-1
    private const val REACH_POS_3=3*TAIL_COUNT*TAIL_COUNT+2*TAIL_COUNT+TAIL_COUNT-1
    private const val REACH_NEG_3=-REACH_POS_3-1
    private const val START_POS_2=MIDDLE+80+1
    private const val START_POS_3=START_POS_2+42
    private const val START_NEG_2=MIDDLE+REACH_NEG_1
    private const val START_NEG_3=START_NEG_2-42
    private fun getNegDivMod(number: Int,factor: Int): Long {
        var modulo=number%factor;var result=(number/factor).toLong()
        if(modulo<0) { --result;modulo+=factor }
        return (result shl 32) or modulo.toLong()
    }
    private fun writeDiff(initialDiff: Int,buffer: ByteArray,initialOffset: Int): Int {
        var diff=initialDiff;var offset=initialOffset
        if(diff>=REACH_NEG_1) {
            if(diff<=REACH_POS_1) put(buffer,offset++,MIDDLE+diff)
            else if(diff<=REACH_POS_2) { put(buffer,offset++,START_POS_2+diff/TAIL_COUNT);put(buffer,offset++,MIN+diff%TAIL_COUNT) }
            else if(diff<=REACH_POS_3) {
                put(buffer,offset+2,MIN+diff%TAIL_COUNT);diff/=TAIL_COUNT
                put(buffer,offset+1,MIN+diff%TAIL_COUNT);put(buffer,offset,START_POS_3+diff/TAIL_COUNT);offset+=3
            } else {
                put(buffer,offset+3,MIN+diff%TAIL_COUNT);diff/=TAIL_COUNT
                put(buffer,offset+2,MIN+diff%TAIL_COUNT);diff/=TAIL_COUNT
                put(buffer,offset+1,MIN+diff%TAIL_COUNT);put(buffer,offset,MAX);offset+=4
            }
        } else {
            var division=getNegDivMod(diff,TAIL_COUNT);var modulo=division.toInt()
            if(diff>=REACH_NEG_2) { diff=(division shr 32).toInt();put(buffer,offset++,START_NEG_2+diff);put(buffer,offset++,MIN+modulo) }
            else if(diff>=REACH_NEG_3) {
                put(buffer,offset+2,MIN+modulo);diff=(division shr 32).toInt();division=getNegDivMod(diff,TAIL_COUNT)
                modulo=division.toInt();diff=(division shr 32).toInt()
                put(buffer,offset+1,MIN+modulo);put(buffer,offset,START_NEG_3+diff);offset+=3
            } else {
                put(buffer,offset+3,MIN+modulo);diff=(division shr 32).toInt();division=getNegDivMod(diff,TAIL_COUNT)
                modulo=division.toInt();diff=(division shr 32).toInt();put(buffer,offset+2,MIN+modulo)
                division=getNegDivMod(diff,TAIL_COUNT);modulo=division.toInt();put(buffer,offset+1,MIN+modulo);put(buffer,offset,MIN);offset+=4
            }
        }
        return offset
    }
    private fun put(buffer: ByteArray,index: Int,value: Int) { if(index<0 || index>=buffer.size) throw IndexOutOfBoundsException();buffer[index]=value.toByte() }
}
