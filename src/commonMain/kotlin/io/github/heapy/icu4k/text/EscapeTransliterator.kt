// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Utility

internal class EscapeTransliterator(id: String,private val prefix: String,private val suffix: String,private val radix: Int,
    private val minDigits: Int,private val supplementals: Boolean,private val supplementalHandler: EscapeTransliterator?) : Transliterator(id,null) {
    override fun handleTransliterate(text: Replaceable,pos: Position,incremental: Boolean) {
        var start=pos.start;var limit=pos.limit
        val buffer=StringBuilder(prefix);val prefixLength=prefix.length;var redo=false
        while(start<limit) {
            val c=if(supplementals)text.char32At(start)else text.charAt(start).code
            val length=if(supplementals)UTF16.getCharCount(c)else 1
            if(c and -0x10000 != 0 && supplementalHandler!=null) {
                buffer.setLength(0);buffer.append(supplementalHandler.prefix)
                Utility.appendNumber(buffer,c,supplementalHandler.radix,supplementalHandler.minDigits)
                buffer.append(supplementalHandler.suffix);redo=true
            }else {
                if(redo){buffer.setLength(0);buffer.append(prefix);redo=false}else buffer.setLength(prefixLength)
                Utility.appendNumber(buffer,c,radix,minDigits);buffer.append(suffix)
            }
            text.replace(start,start+length,buffer.toString());start+=buffer.length;limit+=buffer.length-length
        }
        pos.contextLimit+=limit-pos.limit;pos.limit=limit;pos.start=start
    }
    override fun addSourceTargetSet(inputFilter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet) {
        sourceSet.addAll(getFilterAsUnicodeSet(inputFilter))
        var current: EscapeTransliterator?=this
        while(current!=null) {
            if(inputFilter.size()!=0) {
                targetSet.addAll(current.prefix);targetSet.addAll(current.suffix)
                val buffer=StringBuilder()
                for(i in 0 until current.radix)Utility.appendNumber(buffer,i,current.radix,current.minDigits)
                targetSet.addAll(buffer)
            }
            current=current.supplementalHandler
        }
    }
    companion object {
        fun register() {
            fun put(variant:String,prefix:String,suffix:String,radix:Int,min:Int,supp:Boolean,handler:EscapeTransliterator?=null) {
                val id="Any-Hex"+if(variant.isEmpty())"" else "/$variant"
                registerFactory(id){EscapeTransliterator(id,prefix,suffix,radix,min,supp,handler)}
            }
            put("Unicode","U+","",16,4,true);put("Java","\\u","",16,4,false)
            put("C","\\u","",16,4,true,EscapeTransliterator("","\\U","",16,8,true,null))
            put("XML","&#x",";",16,1,true);put("XML10","&#",";",10,1,true);put("Perl","\\x{","}",16,1,true)
            put("Plain","","",16,4,true);put("","\\u","",16,4,false)
        }
    }
}
