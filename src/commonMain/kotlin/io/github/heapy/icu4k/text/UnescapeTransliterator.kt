// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.lang.UCharacter

internal class UnescapeTransliterator(id: String,private val spec: IntArray) : Transliterator(id,null) {
    override fun handleTransliterate(text: Replaceable,pos: Position,incremental: Boolean) {
        var start=pos.start;var limit=pos.limit
        scan@ while(start<limit) {
            var pattern=0
            while(spec[pattern]!=0xffff) {
                val prefixLength=spec[pattern++];val suffixLength=spec[pattern++];val radix=spec[pattern++];val min=spec[pattern++];val max=spec[pattern++]
                var s=start;var match=true
                for(i in 0 until prefixLength) {
                    if(s>=limit && i>0){if(incremental)break@scan;match=false;break}
                    if(text.charAt(s++).code!=spec[pattern+i]){match=false;break}
                }
                if(match) {
                    var value=0;var digits=0
                    while(true) {
                        if(s>=limit){if(s>start && incremental)break@scan;break}
                        val ch=text.char32At(s);val digit=UCharacter.digit(ch,radix)
                        if(digit<0)break
                        s+=UTF16.getCharCount(ch);value=value*radix+digit
                        if(++digits==max)break
                    }
                    match=digits>=min
                    if(match) {
                        for(i in 0 until suffixLength) {
                            if(s>=limit){if(s>start && incremental)break@scan;match=false;break}
                            if(text.charAt(s++).code!=spec[pattern+prefixLength+i]){match=false;break}
                        }
                        if(match) {
                            val str=UTF16.valueOf(value)
                            text.replace(start,s,str);limit-=s-start-str.length
                            break
                        }
                    }
                }
                pattern+=prefixLength+suffixLength
            }
            if(start<limit)start+=UTF16.getCharCount(text.char32At(start))
        }
        pos.contextLimit+=limit-pos.limit;pos.limit=limit;pos.start=start
    }
    override fun addSourceTargetSet(inputFilter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet) {
        val filter=getFilterAsUnicodeSet(inputFilter);val items=UnicodeSet();val buffer=StringBuilder()
        var i=0
        while(spec[i]!=0xffff) {
            val end=i+spec[i]+spec[i+1]+5;val radix=spec[i+2]
            for(j in 0 until radix)Utility.appendNumber(buffer,j,radix,0)
            for(j in i+5 until end)items.add(spec[j])
            i=end
        }
        items.addAll(buffer).retainAll(filter)
        if(items.size()>0){sourceSet.addAll(items);targetSet.add(0,0x10ffff)}
    }
    companion object {
        fun register() {
            fun specification(prefix:String,suffix:String,radix:Int,min:Int,max:Int):IntArray = intArrayOf(prefix.length,suffix.length,radix,min,max)+prefix.map {it.code}.toIntArray()+suffix.map {it.code}.toIntArray()
            val parts=listOf("Unicode" to specification("U+","",16,4,6),"Java" to specification("\\u","",16,4,4),
                "C" to specification("\\U","",16,8,8),"XML" to specification("&#x",";",16,1,6),"XML10" to specification("&#",";",10,1,7),"Perl" to specification("\\x{","}",16,1,6))
            for((variant,part)in parts) {
                val id="Hex-Any/$variant"
                val spec=(if(variant=="C")parts[1].second+part else part)+intArrayOf(0xffff)
                registerFactory(id){UnescapeTransliterator(id,spec)}
            }
            registerFactory("Hex-Any"){UnescapeTransliterator("Hex-Any",parts.flatMap {it.second.toList()}.toIntArray()+intArrayOf(0xffff))}
        }
    }
}
