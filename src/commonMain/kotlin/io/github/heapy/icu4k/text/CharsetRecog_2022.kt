// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2012, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal abstract class CharsetRecog_2022(private val name: String,private val escapes: Array<IntArray>) : CharsetRecognizer() {
    override fun getName(): String = name
    override fun match(input: CharsetDetectionInput): CharsetCandidate? {
        val bytes=input.filtered;val length=input.filteredLength
        var hits=0;var misses=0;var shifts=0;var i=0
        scan@ while(i<length) {
            if(bytes[i].toInt()==0x1b) {
                sequences@ for(sequence in escapes) {
                    if(length-i<sequence.size)continue
                    for(j in 1 until sequence.size)if(sequence[j]!=bytes[i+j].toInt())continue@sequences
                    hits++;i+=sequence.size;continue@scan
                }
                misses++
            }
            if(bytes[i].toInt()==0x0e || bytes[i].toInt()==0x0f)shifts++
            i++
        }
        if(hits==0)return null
        var quality=(100*hits-100*misses)/(hits+misses)
        if(hits+shifts<5)quality-=(5-hits-shifts)*10
        return candidate(quality.coerceAtLeast(0))
    }
    class CharsetRecog_2022JP : CharsetRecog_2022("ISO-2022-JP",arrayOf(
        intArrayOf(27,36,40,67),intArrayOf(27,36,40,68),intArrayOf(27,36,64),intArrayOf(27,36,65),intArrayOf(27,36,66),intArrayOf(27,38,64),
        intArrayOf(27,40,66),intArrayOf(27,40,72),intArrayOf(27,40,73),intArrayOf(27,40,74),intArrayOf(27,46,65),intArrayOf(27,46,70)))
    class CharsetRecog_2022KR : CharsetRecog_2022("ISO-2022-KR",arrayOf(intArrayOf(27,36,41,67)))
    class CharsetRecog_2022CN : CharsetRecog_2022("ISO-2022-CN",arrayOf(
        intArrayOf(27,36,41,65),intArrayOf(27,36,41,71),intArrayOf(27,36,42,72),intArrayOf(27,36,41,69),intArrayOf(27,36,43,73),
        intArrayOf(27,36,43,74),intArrayOf(27,36,43,75),intArrayOf(27,36,43,76),intArrayOf(27,36,43,77),intArrayOf(27,78),intArrayOf(27,79)))
}
