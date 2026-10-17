// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.PatternProps
import io.github.heapy.icu4k.impl.UCharacterName
import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.lang.UCharacter

internal class UnicodeNameTransliterator(filter: UnicodeFilter?) : Transliterator("Any-Name",filter) {
    override fun handleTransliterate(text: Replaceable,pos: Position,incremental: Boolean) {
        var cursor=pos.start;var limit=pos.limit
        val buffer=StringBuilder(OPEN_DELIM)
        while(cursor<limit) {
            val c=text.char32At(cursor);val name=UCharacter.getExtendedName(c)
            if(name!=null) {
                buffer.setLength(3);buffer.append(name).append('}')
                val length=UTF16.getCharCount(c)
                text.replace(cursor,cursor+length,buffer.toString());cursor+=buffer.length;limit+=buffer.length-length
            }else cursor++
        }
        pos.contextLimit+=limit-pos.limit;pos.limit=limit;pos.start=cursor
    }
    override fun addSourceTargetSet(inputFilter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet) {
        val filter=getFilterAsUnicodeSet(inputFilter)
        if(filter.size()>0) {
            sourceSet.addAll(filter)
            targetSet.add('0'.code,'9'.code).add('A'.code,'Z'.code).add('-'.code).add(' '.code).addAll(OPEN_DELIM).add('}'.code)
                .add('a'.code,'z'.code).add('<'.code).add('>'.code).add('('.code).add(')'.code)
        }
    }
    companion object {const val OPEN_DELIM="\\N{";fun register(){registerFactory("Any-Name"){UnicodeNameTransliterator(null)}}}
}
internal class NameUnicodeTransliterator(filter: UnicodeFilter?) : Transliterator("Name-Any",filter) {
    override fun handleTransliterate(text: Replaceable,pos: Position,incremental: Boolean) {
        val max=UCharacterName.INSTANCE.getMaxCharNameLength()+1
        val name=StringBuilder(max);val legal=UnicodeSet()
        UCharacterName.INSTANCE.getCharNameCharacters(legal)
        var cursor=pos.start;var limit=pos.limit;var mode=0;var open=-1
        while(cursor<limit) {
            var c=text.char32At(cursor)
            when(mode) {
                0->if(c=='\\'.code) {
                    open=cursor
                    val i=Utility.parsePattern("\\N~{~",text,cursor,limit)
                    if(i>=0 && i<limit){mode=1;name.setLength(0);cursor=i;continue}
                }
                1-> {
                    if(PatternProps.isWhiteSpace(c)) {
                        if(name.isNotEmpty() && name.last()!=' ') {name.append(' ');if(name.length>max)mode=0}
                    }else if(c=='}'.code) {
                        if(name.isNotEmpty() && name.last()==' ')name.setLength(name.length-1)
                        c=UCharacter.getCharFromExtendedName(name.toString())
                        if(c != -1) {
                            cursor++
                            val str=UTF16.valueOf(c)
                            text.replace(open,cursor,str)
                            val delta=cursor-open-str.length;cursor-=delta;limit-=delta
                        }
                        mode=0;open=-1;continue
                    }else if(legal.contains(c)) {
                        UTF16.append(name,c)
                        if(name.length>=max)mode=0
                    }else {cursor--;mode=0}
                }
            }
            cursor+=UTF16.getCharCount(c)
        }
        pos.contextLimit+=limit-pos.limit;pos.limit=limit;pos.start=if(incremental && open>=0)open else cursor
    }
    override fun addSourceTargetSet(inputFilter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet) {
        val filter=getFilterAsUnicodeSet(inputFilter)
        if(!filter.containsAll(UnicodeNameTransliterator.OPEN_DELIM) || !filter.contains('}'.code))return
        val items=UnicodeSet().add('0'.code,'9'.code).add('A'.code,'F'.code).add('a'.code,'z'.code)
            .add('<'.code).add('>'.code).add('('.code).add(')'.code).add('-'.code).add(' '.code).addAll(UnicodeNameTransliterator.OPEN_DELIM).add('}'.code)
        items.retainAll(filter)
        if(items.size()>0){sourceSet.addAll(items);targetSet.add(0,0x10ffff)}
    }
    companion object {fun register(){registerFactory("Name-Any"){NameUnicodeTransliterator(null)}}}
}
