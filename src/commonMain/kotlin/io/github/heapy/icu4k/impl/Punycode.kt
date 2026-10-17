// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2014, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.StringPrepParseException
import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.ICUInputTooLongException

/** ICU's Bootstring/Punycode codec, retaining its optional UTF-16 case-flag protocol. */
internal object Punycode {
    private const val BASE=36
    private const val TMIN=1
    private const val TMAX=26
    private fun adaptBias(input: Int,length: Int,first: Boolean): Int {
        var delta=if(first)input/700 else input/2
        delta+=delta/length
        var count=0
        while(delta>((BASE-TMIN)*TMAX)/2){delta/=BASE-TMIN;count+=BASE}
        return count+((BASE-TMIN+1)*delta)/(delta+38)
    }
    private fun decodeDigit(c: Int): Int = when {
        c<='Z'.code -> if(c<='9'.code){if(c<'0'.code)-1 else c-'0'.code+26}else c-'A'.code
        c<='z'.code -> c-'a'.code
        else -> -1
    }
    private fun asciiCaseMap(c: Char,upper: Boolean): Char = when {
        upper && c in 'a'..'z' -> (c.code-32).toChar()
        !upper && c in 'A'..'Z' -> (c.code+32).toChar()
        else -> c
    }
    private fun digitToBasic(digit: Int,upper: Boolean): Char =
        (if(digit<26)(if(upper)'A'.code else 'a'.code)+digit else '0'.code-26+digit).toChar()
    private fun flagAt(flags: BooleanArray,index: Int): Boolean {
        if(index !in flags.indices)throw IndexOutOfBoundsException()
        return flags[index]
    }
    private fun basicUpper(c: Char): Boolean = c>='A' && c>='Z' // Source uses >= for both bounds.
    private fun illegal(): Nothing = throw StringPrepParseException("Illegal char found",StringPrepParseException.ILLEGAL_CHAR_FOUND)
    fun encode(src: CharSequence,caseFlags: BooleanArray?): StringBuilder {
        val length=src.length
        if(length>1000)throw ICUInputTooLongException("input too long: $length UTF-16 code units")
        val codePoints=IntArray(length)
        val dest=StringBuilder(length)
        var count=0;var j=0
        while(j<length) {
            val c=src[j]
            if(c.code<0x80) {
                codePoints[count++]=0
                dest.append(if(caseFlags!=null)asciiCaseMap(c,flagAt(caseFlags,j))else c)
            }else {
                var n=if(caseFlags!=null && flagAt(caseFlags,j))Int.MIN_VALUE else 0
                if(!c.isSurrogate())n=n or c.code
                else if(c.isHighSurrogate() && j+1<length && src[j+1].isLowSurrogate()) {
                    j++;n=n or ((c.code-0xd800)*0x400+src[j].code-0xdc00+0x10000)
                }else illegal()
                codePoints[count++]=n
            }
            j++
        }
        val basic=dest.length
        if(basic>0)dest.append('-')
        var n=0x80;var delta=0;var bias=72;var handled=basic
        while(handled<count) {
            var m=Int.MAX_VALUE
            for(cp in 0 until count){val q=codePoints[cp] and Int.MAX_VALUE;if(n<=q && q<m)m=q}
            if(m-n>(Int.MAX_VALUE-handled-delta)/(handled+1))throw IllegalStateException("Internal program error")
            delta+=(m-n)*(handled+1);n=m
            for(cp in 0 until count) {
                var q=codePoints[cp] and Int.MAX_VALUE
                if(q<n)delta++
                else if(q==n) {
                    q=delta;var k=BASE
                    while(true) {
                        val t=(k-bias).coerceIn(TMIN,TMAX)
                        if(q<t)break
                        dest.append(digitToBasic(t+(q-t)%(BASE-t),false));q=(q-t)/(BASE-t);k+=BASE
                    }
                    dest.append(digitToBasic(q,codePoints[cp]<0))
                    bias=adaptBias(delta,handled+1,handled==basic);delta=0;handled++
                }
            }
            delta++;n++
        }
        return dest
    }
    fun decode(src: CharSequence,caseFlags: BooleanArray?): StringBuilder {
        val length=src.length
        if(length>2000)throw ICUInputTooLongException("input too long: $length characters")
        val dest=StringBuilder(length)
        var j=length
        while(j>0){j--;if(src[j]=='-')break}
        val basic=j
        var count=basic
        for(p in 0 until basic) {
            val c=src[p]
            if(c.code>=0x80)throw StringPrepParseException("Illegal char found",StringPrepParseException.INVALID_CHAR_FOUND)
            dest.append(c)
            if(caseFlags!=null && p<caseFlags.size)caseFlags[p]=basicUpper(c)
        }
        var n=0x80;var i=0;var bias=72;var firstSupplementary=1000000000
        var input=if(basic>0)basic+1 else 0
        while(input<length) {
            val old=i;var weight=1;var k=BASE
            while(true) {
                if(input>=length)illegal()
                val digit=decodeDigit(src[input++].code)
                if(digit<0)throw StringPrepParseException("Invalid char found",StringPrepParseException.INVALID_CHAR_FOUND)
                if(digit>(Int.MAX_VALUE-i)/weight)illegal()
                i+=digit*weight
                val threshold=(k-bias).coerceIn(TMIN,TMAX)
                if(digit<threshold)break
                if(weight>Int.MAX_VALUE/(BASE-threshold))illegal()
                weight*=BASE-threshold;k+=BASE
            }
            count++
            bias=adaptBias(i-old,count,old==0)
            if(i/count>Int.MAX_VALUE-n)illegal()
            n+=i/count;i%=count
            if(n>0x10ffff || n and -0x800 == 0xd800)illegal()
            val cpLength=UTF16.getCharCount(n)
            val index: Int
            if(i<=firstSupplementary) {
                index=i
                if(cpLength>1)firstSupplementary=index else firstSupplementary++
            }else index=UTF16.moveCodePointOffset(dest,firstSupplementary,i-firstSupplementary)
            if(caseFlags!=null && dest.length+cpLength<=caseFlags.size) {
                if(index<dest.length)caseFlags.copyInto(caseFlags,index+cpLength,index,dest.length)
                caseFlags[index]=basicUpper(src[input-1])
                if(cpLength==2)caseFlags[index+1]=false
            }
            if(cpLength==1)dest.insert(index,n.toChar())
            else {dest.insert(index,UTF16.getLeadSurrogate(n));dest.insert(index+1,UTF16.getTrailSurrogate(n))}
            i++
        }
        return dest
    }
}
