// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2012, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.math.ln

internal abstract class CharsetRecog_mbcs(private val name: String,private val language: String,private val common: IntArray?) : CharsetRecognizer() {
    override fun getName(): String = name
    override fun getLanguage(): String = language
    override fun match(input: CharsetDetectionInput): CharsetCandidate? {
        val iterator=IteratedChar();var doubleBytes=0;var commonCount=0;var bad=0;var total=0
        while(nextChar(iterator,input)) {
            total++
            if(iterator.error)bad++
            else if(iterator.charValue !in 0..255) {
                doubleBytes++
                if(common!=null && contains(common,iterator.charValue))commonCount++
            }
            if(bad>=2 && bad*5>=doubleBytes)return null
        }
        return candidate(confidence(doubleBytes,commonCount,bad,total))
    }
    internal fun confidence(doubleBytes: Int,commonCount: Int,bad: Int,total: Int): Int {
        if(doubleBytes<=10 && bad==0)return if(doubleBytes==0 && total<10)0 else 10
        if(doubleBytes<20*bad)return 0
        return if(common==null)minOf(30+doubleBytes-20*bad,100) else {
            val scale=90.0/ln((Float.fromBits(doubleBytes.toFloat().toRawBits())/4).toDouble())
            minOf((ln((commonCount+1).toDouble())*scale+10).toInt(),100)
        }
    }
    private fun contains(values: IntArray,value: Int): Boolean {
        var low=0;var high=values.lastIndex
        while(low<=high) {
            val mid=(low+high) ushr 1
            when {values[mid]<value->low=mid+1;values[mid]>value->high=mid-1;else->return true}
        }
        return false
    }
    class IteratedChar {
        var charValue=0;var nextIndex=0;var error=false;var done=false
        fun reset() {charValue=0;nextIndex=0;error=false;done=false}
        fun nextByte(input: CharsetDetectionInput): Int {
            if(nextIndex>=input.rawLength){done=true;return -1}
            return input.raw[nextIndex++].toInt() and 255
        }
    }
    abstract fun nextChar(iterator: IteratedChar,input: CharsetDetectionInput): Boolean
    class CharsetRecog_sjis : CharsetRecog_mbcs("Shift_JIS","ja",CharsetMbcsTables.sjis) {
        override fun nextChar(iterator: IteratedChar,input: CharsetDetectionInput): Boolean {
            iterator.error=false
            val first=iterator.nextByte(input);iterator.charValue=first
            if(first<0)return false
            if(first<=0x7f || first in 0xa1..0xdf)return true
            val second=iterator.nextByte(input);if(second<0)return false
            iterator.charValue=(first shl 8) or second
            if(second !in 0x40..0xff)iterator.error=true
            return true
        }
    }
    class CharsetRecog_big5 : CharsetRecog_mbcs("Big5","zh",CharsetMbcsTables.big5) {
        override fun nextChar(iterator: IteratedChar,input: CharsetDetectionInput): Boolean {
            iterator.error=false
            val first=iterator.nextByte(input);iterator.charValue=first
            if(first<0)return false
            if(first<=0x7f || first==0xff)return true
            val second=iterator.nextByte(input);if(second<0)return false
            iterator.charValue=(iterator.charValue shl 8) or second
            if(second<0x40 || second==0x7f || second==0xff)iterator.error=true
            return true
        }
    }
    abstract class CharsetRecog_euc(name: String,language: String,common: IntArray) : CharsetRecog_mbcs(name,language,common) {
        final override fun nextChar(iterator: IteratedChar,input: CharsetDetectionInput): Boolean {
            iterator.error=false
            val first=iterator.nextByte(input);iterator.charValue=first
            if(first<0){iterator.done=true;return false}
            if(first<=0x8d)return !iterator.done
            val second=iterator.nextByte(input);iterator.charValue=(iterator.charValue shl 8) or second
            if(first in 0xa1..0xfe || first==0x8e) {
                if(second<0xa1)iterator.error=true
            }else if(first==0x8f) {
                val third=iterator.nextByte(input);iterator.charValue=(iterator.charValue shl 8) or third
                if(third<0xa1)iterator.error=true
            }
            return !iterator.done
        }
        class CharsetRecog_euc_jp : CharsetRecog_euc("EUC-JP","ja",CharsetMbcsTables.euc_jp)
        class CharsetRecog_euc_kr : CharsetRecog_euc("EUC-KR","ko",CharsetMbcsTables.euc_kr)
    }
    class CharsetRecog_gb_18030 : CharsetRecog_mbcs("GB18030","zh",CharsetMbcsTables.gb_18030) {
        override fun nextChar(iterator: IteratedChar,input: CharsetDetectionInput): Boolean {
            iterator.error=false
            val first=iterator.nextByte(input);iterator.charValue=first
            if(first<0){iterator.done=true;return false}
            if(first<=0x80)return !iterator.done
            val second=iterator.nextByte(input);iterator.charValue=(iterator.charValue shl 8) or second
            if(first in 0x81..0xfe) {
                // ICU4J uses decimal 80 for the second lower bound, accepting 0x7f too.
                if(second in 0x40..0x7e || second in 80..0xfe)return !iterator.done
                if(second in 0x30..0x39) {
                    val third=iterator.nextByte(input)
                    if(third in 0x81..0xfe) {
                        val fourth=iterator.nextByte(input)
                        if(fourth in 0x30..0x39){iterator.charValue=(iterator.charValue shl 16) or (third shl 8) or fourth;return !iterator.done}
                    }
                }
                iterator.error=true
            }
            return !iterator.done
        }
    }
}
