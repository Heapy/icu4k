// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2013, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal abstract class CharsetRecog_Unicode : CharsetRecognizer() {
    abstract class Utf16(private val little: Boolean) : CharsetRecog_Unicode() {
        override fun getName(): String = if(little)"UTF-16LE" else "UTF-16BE"
        override fun match(input: CharsetDetectionInput): CharsetCandidate? {
            val bytes=input.raw;val length=minOf(bytes.size,30);var confidence=10;var i=0
            while(i<length-1) {
                val unit=if(little)unit16(bytes[i+1],bytes[i]) else unit16(bytes[i],bytes[i+1])
                if(i==0 && unit==0xfeff){confidence=100;break}
                confidence=(confidence+when {unit==0->-10;unit in 0x20..0xff || unit==10->10;else->0}).coerceIn(0,100)
                if(confidence==0 || confidence==100)break
                i+=2
            }
            if(length<4 && confidence<100)confidence=0
            return candidate(confidence)
        }
    }
    class CharsetRecog_UTF_16_BE : Utf16(false)
    class CharsetRecog_UTF_16_LE : Utf16(true)
    abstract class Utf32(private val little: Boolean) : CharsetRecog_Unicode() {
        override fun getName(): String = if(little)"UTF-32LE" else "UTF-32BE"
        override fun match(input: CharsetDetectionInput): CharsetCandidate? {
            val bytes=input.raw;val limit=input.rawLength/4*4
            if(limit==0)return null
            fun read(index: Int): Int {
                var value=0
                for(n in 0..3)value=(value shl 8) or (bytes[index+if(little)3-n else n].toInt() and 255)
                return value
            }
            val bom=read(0)==0xfeff;var valid=0;var invalid=0
            var i=0
            while(i<limit) {
                val cp=read(i)
                if(cp<0 || cp>=0x10ffff || cp in 0xd800..0xdfff)invalid++ else valid++
                i+=4
            }
            return candidate(when {
                bom && invalid==0->100
                bom && valid>invalid*10->80
                valid>3 && invalid==0->100
                valid>0 && invalid==0->80
                valid>invalid*10->25
                else->0
            })
        }
    }
    class CharsetRecog_UTF_32_BE : Utf32(false)
    class CharsetRecog_UTF_32_LE : Utf32(true)
    companion object { private fun unit16(hi: Byte,lo: Byte): Int = ((hi.toInt() and 255) shl 8) or (lo.toInt() and 255) }
}
