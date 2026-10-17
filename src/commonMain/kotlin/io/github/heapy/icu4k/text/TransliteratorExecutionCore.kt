// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Utility

/** Transliterator's batch, streaming and filter/rollback algorithms; the owner supplies virtual dispatch. */
internal class TransliteratorExecutionCore(private val input: Input) {
    interface Input {
        val id: String?
        var filter: UnicodeSet?
        fun handleTransliterate(text: Replaceable,pos: TransliterationPosition,incremental: Boolean)
    }
    fun transliterate(text: Replaceable,start: Int,limit: Int): Int {
        if(start<0 || limit<start || text.length()<limit)return -1
        val pos=TransliterationPosition(start,limit,start)
        filteredTransliterate(text,pos,false,true)
        return pos.limit
    }
    fun transliterate(text: Replaceable){transliterate(text,0,text.length())}
    fun transliterate(text: String): String {val result=ReplaceableString(text);transliterate(result);return result.toString()}
    fun transliterate(text: Replaceable,index: TransliterationPosition,insertion: String?) {
        index.validate(text.length())
        if(insertion!=null){text.replace(index.limit,index.limit,insertion);index.limit+=insertion.length;index.contextLimit+=insertion.length}
        if(index.limit>0 && text.charAt(index.limit-1).isHighSurrogate())return
        filteredTransliterate(text,index,true,true)
    }
    fun transliterate(text: Replaceable,index: TransliterationPosition,insertion: Int) = transliterate(text,index,UTF16.valueOf(insertion))
    fun finishTransliteration(text: Replaceable,index: TransliterationPosition){index.validate(text.length());filteredTransliterate(text,index,false,true)}
    fun filteredTransliterate(text: Replaceable,index: TransliterationPosition,incremental: Boolean) = filteredTransliterate(text,index,incremental,false)
    private fun filteredTransliterate(text: Replaceable,index: TransliterationPosition,incremental: Boolean,rollback: Boolean) {
        if(input.filter==null && !rollback){input.handleTransliterate(text,index,incremental);return}
        var globalLimit=index.limit
        while(true) {
            if(input.filter!=null) {
                while(index.start<globalLimit) {
                    val filter=input.filter
                    val cp=text.char32At(index.start)
                    if(filter!!.contains(cp))break
                    index.start+=UTF16.getCharCount(cp)
                }
                index.limit=index.start
                while(index.limit<globalLimit) {
                    val filter=input.filter
                    val cp=text.char32At(index.limit)
                    if(!filter!!.contains(cp))break
                    index.limit+=UTF16.getCharCount(cp)
                }
            }
            if(index.start==index.limit)break
            val incrementalRun=if(index.limit<globalLimit)false else incremental
            if(rollback && incrementalRun) {
                val runStart=index.start
                var runLimit=index.limit
                val runLength=runLimit-runStart
                var rollbackOrigin=text.length()
                text.copy(runStart,runLimit,rollbackOrigin)
                var passStart=runStart
                var rollbackStart=rollbackOrigin
                var passLimit=index.start
                var uncommittedLength=0
                var totalDelta=0
                while(true) {
                    val charLength=UTF16.getCharCount(text.char32At(passLimit))
                    passLimit+=charLength
                    if(passLimit>runLimit)break
                    uncommittedLength+=charLength
                    index.limit=passLimit
                    input.handleTransliterate(text,index,true)
                    val delta=index.limit-passLimit
                    if(index.start!=index.limit) {
                        val rs=rollbackStart+delta-(index.limit-passStart)
                        text.replace(passStart,index.limit,"")
                        text.copy(rs,rs+uncommittedLength,passStart)
                        index.start=passStart;index.limit=passLimit;index.contextLimit-=delta
                    } else {
                        passStart=index.start;passLimit=index.start
                        rollbackStart+=delta+uncommittedLength
                        uncommittedLength=0;runLimit+=delta;totalDelta+=delta
                    }
                }
                rollbackOrigin+=totalDelta;globalLimit+=totalDelta
                text.replace(rollbackOrigin,rollbackOrigin+runLength,"")
                index.start=passStart
            } else {
                val limit=index.limit
                input.handleTransliterate(text,index,incrementalRun)
                val delta=index.limit-limit
                if(!incrementalRun && index.start!=index.limit)throw RuntimeException("ERROR: Incomplete non-incremental transliteration by "+input.id)
                globalLimit+=delta
            }
            if(input.filter==null || incrementalRun)break
        }
        index.limit=globalLimit
    }
    fun setFilter(filter: UnicodeFilter?) {
        if(filter==null)input.filter=null
        else try{input.filter=UnicodeSet(filter as UnicodeSet).freeze()}catch(e: Exception) {
            input.filter=UnicodeSet()
            filter.addMatchSetTo(input.filter!!)
            input.filter!!.freeze()
        }
    }
    fun getFilterAsUnicodeSet(externalFilter: UnicodeSet): UnicodeSet {
        if(input.filter==null)return externalFilter
        return UnicodeSet(externalFilter).retainAll(input.filter!!).freeze()
    }
    fun baseToRules(escapeUnprintable: Boolean): String {
        if(!escapeUnprintable)return "::"+input.id+";"
        val result=StringBuilder()
        val id=input.id!!
        var i=0
        while(i<id.length){val cp=UTF16.charAt(id,i);if(!Utility.escapeUnprintable(result,cp))UTF16.append(result,cp);i+=UTF16.getCharCount(cp)}
        result.insert(0,"::");result.append(';');return result.toString()
    }
}
