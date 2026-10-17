// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.util.ULocale

internal class BreakTransliterator(id: String,filter: UnicodeFilter?,private var iterator: BreakIterator?=null,private var insertion: String=" ") : Transliterator(id,filter) {
    fun getInsertion(): String = insertion
    fun setInsertion(value: String) {insertion=value}
    fun getBreakIterator(): BreakIterator {if(iterator==null)iterator=WordIterator.value;return iterator!!.clone()}
    fun setBreakIterator(value: BreakIterator?) {iterator=value}
    override fun handleTransliterate(text: Replaceable,pos: Position,incremental: Boolean) {
        var boundaries=IntArray(50);var count=0
        val iterator=getBreakIterator()
        iterator.setText(ReplaceableCharacterIterator(text,pos.start,pos.limit,pos.start))
        var boundary=iterator.first()
        while(boundary!=BreakIterator.DONE && boundary<pos.limit) {
            if(boundary!=0) {
                var c=UTF16.charAt(text,boundary-1);var type=UCharacter.getType(c)
                if((1 shl type) and LETTER_OR_MARK_MASK != 0) {
                    c=UTF16.charAt(text,boundary);type=UCharacter.getType(c)
                    if((1 shl type) and LETTER_OR_MARK_MASK != 0) {
                        if(count>=boundaries.size)boundaries=boundaries.copyOf(boundaries.size*2)
                        boundaries[count++]=boundary
                    }
                }
            }
            boundary=iterator.next()
        }
        var delta=0;var last=0
        if(count!=0) {
            delta=count*insertion.length;last=boundaries[count-1]
            while(count>0){boundary=boundaries[--count];text.replace(boundary,boundary,insertion)}
        }
        pos.contextLimit+=delta;pos.limit+=delta;pos.start=if(incremental)last+delta else pos.limit
    }
    override fun addSourceTargetSet(inputFilter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet) {if(getFilterAsUnicodeSet(inputFilter).size()!=0)targetSet.addAll(insertion)}
    companion object {
        private val LETTER_OR_MARK_MASK = (1 shl 1) or (1 shl 2) or (1 shl 3) or (1 shl 4) or (1 shl 5) or (1 shl 8) or (1 shl 6) or (1 shl 7)
        fun register(){registerInstance(BreakTransliterator("Any-BreakInternal",null),false)}
    }
    private object WordIterator {val value=BreakIterator.getWordInstance(ULocale("th_TH"))}
}
