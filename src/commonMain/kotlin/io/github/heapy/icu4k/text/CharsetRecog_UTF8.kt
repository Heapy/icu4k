// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2014, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal class CharsetRecog_UTF8 : CharsetRecognizer() {
    override fun getName(): String = "UTF-8"
    override fun match(input: CharsetDetectionInput): CharsetCandidate? {
        val bytes=input.raw;val length=input.rawLength
        val bom=length>=3 && bytes[0].toInt() and 255==0xef && bytes[1].toInt() and 255==0xbb && bytes[2].toInt() and 255==0xbf
        var valid=0;var invalid=0;var i=0
        while(i<length) {
            var b=bytes[i].toInt()
            if(b and 0x80==0){i++;continue}
            var trails=when {
                b and 0xe0==0xc0->1
                b and 0xf0==0xe0->2
                b and 0xf8==0xf0->3
                else->{invalid++;i++;continue}
            }
            while(true) {
                i++
                if(i>=length)break
                b=bytes[i].toInt()
                if(b and 0xc0!=0x80){invalid++;break}
                trails--
                if(trails==0){valid++;break}
            }
            i++
        }
        return candidate(when {
            bom && invalid==0->100
            bom && valid>invalid*10->80
            valid>3 && invalid==0->100
            valid>0 && invalid==0->80
            valid==0 && invalid==0->15
            valid>invalid*10->25
            else->0
        })
    }
}
