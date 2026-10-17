// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2010, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.IDNA
import io.github.heapy.icu4k.text.StringPrep
import io.github.heapy.icu4k.text.StringPrepParseException as Failure
import io.github.heapy.icu4k.text.UCharacterIterator

internal object IDNA2003 {
    private val namePrep=StringPrep.getInstance(StringPrep.RFC3491_NAMEPREP)
    private fun lower(c: Char): Char = if(c in 'A'..'Z')(c.code+32).toChar() else c
    private fun lower(s: CharSequence): StringBuilder = StringBuilder().also {out->s.forEach {out.append(lower(it))}}
    private fun startsWithPrefix(s: CharSequence): Boolean = s.length>=4 && lower(s[0])=='x' && lower(s[1])=='n' && s[2]=='-' && s[3]=='-'
    private fun compareASCII(s1: CharSequence,s2: CharSequence): Int {
        var i=0
        while(true) {
            if(i==s1.length)return 0
            val c1=s1[i];val c2=s2[i]
            if(c1!=c2){val result=lower(c1).code-lower(c2).code;if(result!=0)return result}
            i++
        }
    }
    private fun separator(c: Char): Boolean = c=='.' || c=='\u3002' || c=='\uff0e' || c=='\uff61'
    private fun isLDH(c: Char): Boolean = c=='-' || c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z'
    private fun zeroLabel(): Nothing = throw Failure("Found zero length label after NamePrep.",Failure.ZERO_LENGTH_LABEL)
    fun convertToASCII(src: UCharacterIterator,options: Int): StringBuilder {
        var ascii=true
        while(true){val ch=src.next();if(ch==UCharacterIterator.DONE)break;if(ch>0x7f){ascii=false;break}}
        src.setToStart()
        val prepared=if(!ascii)namePrep.prepare(src,options) else StringBuilder(src.getText())
        val length=prepared.length
        if(length==0)zeroLabel()
        ascii=true;var ldh=true;var failPos=-1
        for(j in 0 until length){val ch=prepared[j];if(ch.code>0x7f)ascii=false else if(!isLDH(ch)){ldh=false;failPos=j}}
        if(options and IDNA.USE_STD3_RULES!=0 && (!ldh || prepared[0]=='-' || prepared[length-1]=='-')) {
            val position=if(!ldh){if(failPos>0)failPos-1 else failPos}else if(prepared[0]=='-')0 else length-1
            throw Failure("The input does not conform to the STD 3 ASCII rules",Failure.STD3_ASCII_RULES_ERROR,prepared.toString(),position)
        }
        val dest=if(ascii)prepared else {
            if(startsWithPrefix(prepared))throw Failure("The input does not start with the ACE Prefix.",Failure.ACE_PREFIX_ERROR,prepared.toString(),0)
            StringBuilder("xn--").append(lower(Punycode.encode(prepared,BooleanArray(length))))
        }
        if(dest.length>63)throw Failure("The labels in the input are too long. Length > 63.",Failure.LABEL_TOO_LONG_ERROR,dest.toString(),0)
        return dest
    }
    fun convertToUnicode(src: UCharacterIterator,options: Int): StringBuilder {
        var ascii=true;val saved=src.getIndex()
        while(true){val ch=src.next();if(ch==UCharacterIterator.DONE)break;if(ch>0x7f)ascii=false}
        val prepared=if(!ascii) {
            try {src.setIndex(saved);namePrep.prepare(src,options)}catch(e: Failure){return StringBuilder(src.getText())}
        }else StringBuilder(src.getText())
        if(startsWithPrefix(prepared)) {
            var decoded=try {Punycode.decode(prepared.substring(4,prepared.length),null)}catch(e: Failure){null}
            if(decoded!=null) {
                val checked=convertToASCII(UCharacterIterator.getInstance(decoded),options)
                if(compareASCII(prepared,checked)!=0)decoded=null
            }
            if(decoded!=null)return decoded
        }
        return StringBuilder(src.getText())
    }
    fun convertIDNToASCII(src: String,options: Int): StringBuilder = convertDomain(src,options,true)
    fun convertIDNToUnicode(src: String,options: Int): StringBuilder = convertDomain(src,options,false)
    private fun convertDomain(src: String,options: Int,toASCII: Boolean): StringBuilder {
        val result=StringBuilder();var start=0;var end=0
        while(true) {
            while(end<src.length && !separator(src[end]))end++
            val label=src.substring(start,end)
            if(toASCII) {
                if(!(label.isEmpty() && end==src.length))result.append(convertToASCII(UCharacterIterator.getInstance(label),options))
            }else {
                if(label.isEmpty() && end!=src.length)zeroLabel()
                result.append(convertToUnicode(UCharacterIterator.getInstance(label),options))
            }
            if(end==src.length)break
            result.append(if(toASCII)'.' else src[end]);end++;start=end
        }
        if(result.length>255)throw Failure("The output exceed the max allowed length.",Failure.DOMAIN_NAME_TOO_LONG_ERROR)
        return result
    }
    fun compare(s1: String,s2: String,options: Int): Int = compareASCII(convertIDNToASCII(s1,options),convertIDNToASCII(s2,options))
}
