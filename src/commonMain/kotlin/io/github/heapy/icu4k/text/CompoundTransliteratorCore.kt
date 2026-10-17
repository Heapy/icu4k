// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Compound execution and rule/source-target composition, retaining the source's shared element array. */
internal class CompoundTransliteratorCore(private val trans: Array<Element>,private val numAnonymousRBTs: Int,
    private val filter: () -> UnicodeSet?,private val filterAsSet: (UnicodeSet) -> UnicodeSet) {
    interface Element {
        val id: String?
        val maximumContextLength: Int
        fun filteredTransliterate(text: Replaceable,pos: TransliterationPosition,incremental: Boolean)
        fun toRules(escapeUnprintable: Boolean): String
        fun baseToRules(escapeUnprintable: Boolean): String
        fun addSourceTargetSet(filter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet)
    }
    fun getCount(): Int = trans.size
    fun getTransliterator(index: Int): Element {
        if(index !in trans.indices)throw IndexOutOfBoundsException()
        return trans[index]
    }
    fun computeMaximumContextLength(): Int {var max=0;for(t in trans){val len=t.maximumContextLength;if(len>max)max=len};return max}
    fun toRules(escapeUnprintable: Boolean): String {
        val result=StringBuilder()
        fun smartAppend(c: Char){if(result.isNotEmpty() && result.last()!=c)result.append(c)}
        if(numAnonymousRBTs>=1 && filter()!=null)result.append("::").append(filter()!!.toPattern(escapeUnprintable)).append(';')
        for(i in trans.indices) {
            val rule=if(trans[i].id!!.startsWith("%Pass")) {
                var rule=trans[i].toRules(escapeUnprintable)
                if(numAnonymousRBTs>1 && i>0 && trans[i-1].id!!.startsWith("%Pass"))rule="::Null;"+rule
                rule
            } else if(trans[i].id!!.indexOf(';')>=0)trans[i].toRules(escapeUnprintable)else trans[i].baseToRules(escapeUnprintable)
            smartAppend('\n');result.append(rule);smartAppend(';')
        }
        return result.toString()
    }
    fun addSourceTargetSet(filter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet) {
        val myFilter=UnicodeSet(filterAsSet(filter))
        val tempTargetSet=UnicodeSet()
        for(t in trans){tempTargetSet.clear();t.addSourceTargetSet(myFilter,sourceSet,tempTargetSet);targetSet.addAll(tempTargetSet);myFilter.addAll(tempTargetSet)}
    }
    fun handleTransliterate(text: Replaceable,index: TransliterationPosition,incremental: Boolean) {
        if(trans.isEmpty()){index.start=index.limit;return}
        var compoundLimit=index.limit
        val compoundStart=index.start
        var delta=0
        for(t in trans) {
            index.start=compoundStart
            val limit=index.limit
            if(index.start==index.limit)break
            t.filteredTransliterate(text,index,incremental)
            if(!incremental && index.start!=index.limit)throw RuntimeException("ERROR: Incomplete non-incremental transliteration by "+t.id)
            delta+=index.limit-limit
            if(incremental)index.limit=index.start
        }
        compoundLimit+=delta;index.limit=compoundLimit
    }
}
