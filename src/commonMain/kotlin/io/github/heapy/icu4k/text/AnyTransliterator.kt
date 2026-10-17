// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.createTextTrieMapLock
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.util.MissingResourceException

internal class AnyTransliterator private constructor(id: String?,filter: UnicodeFilter?,private val target: String,private val targetScript: Int,private val cache: Cache) : Transliterator(id,filter) {
    private constructor(id: String,target: String,variant: String,script: Int) : this(id,null,target+(if(variant.isNotEmpty())"/$variant" else ""),script,Cache())
    private class Cache {
        private val values=HashMap<Int,Transliterator>()
        private val lock=createTextTrieMapLock(values)
        fun get(key: Int): Transliterator? = lock.withLock {values[key]}
        fun putIfAbsent(key: Int,value: Transliterator): Transliterator? = lock.withLock {val previous=values[key];if(previous==null)values[key]=value;previous}
    }
    override fun handleTransliterate(text: Replaceable,pos: Position,incremental: Boolean) {
        val allStart=pos.start;var allLimit=pos.limit
        val iterator=ScriptRunIterator(text,pos.contextStart,pos.contextLimit)
        while(iterator.next()) {
            if(iterator.limit<=allStart)continue
            val transliterator=getTransliterator(iterator.scriptCode)
            if(transliterator==null){pos.start=iterator.limit;continue}
            val inc=incremental && iterator.limit>=allLimit
            pos.start=maxOf(allStart,iterator.start);pos.limit=minOf(allLimit,iterator.limit)
            val limit=pos.limit
            transliterator.filteredTransliterate(text,pos,inc)
            val delta=pos.limit-limit;allLimit+=delta;iterator.adjustLimit(delta)
            if(iterator.limit>=allLimit)break
        }
        pos.limit=allLimit
    }
    private fun getTransliterator(source: Int): Transliterator? {
        if(source==targetScript || source==UScript.INVALID_CODE)return if(isWide(targetScript))null else WidthFix.value
        var trans=cache.get(source)
        if(trans==null) {
            val name=UScript.getName(source)
            try {trans=getInstance("$name-$target",FORWARD)}catch(_:RuntimeException) { }
            if(trans==null)try {trans=getInstance("$name-Latin;Latin-$target",FORWARD)}catch(_:RuntimeException) { }
            if(trans!=null) {
                if(!isWide(targetScript))trans=CompoundTransliterator(listOf(WidthFix.value,trans))
                val previous=cache.putIfAbsent(source,trans)
                if(previous!=null)trans=previous
            }else if(!isWide(targetScript))return WidthFix.value
        }
        return trans
    }
    fun safeClone(): Transliterator = AnyTransliterator(getID(),(getFilter() as UnicodeSet?)?.let(::UnicodeSet),target,targetScript,cache)
    override fun addSourceTargetSet(inputFilter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet) {
        val filter=getFilterAsUnicodeSet(inputFilter);sourceSet.addAll(filter)
        if(filter.size()!=0)targetSet.add(0,0x10ffff)
    }
    private class ScriptRunIterator(private val text: Replaceable,private val textStart: Int,private var textLimit: Int) {
        var scriptCode=UScript.INVALID_CODE
        var start=0
        var limit=textStart
        fun next(): Boolean {
            scriptCode=UScript.INVALID_CODE;start=limit
            if(start==textLimit)return false
            while(start>textStart) {
                val script=UScript.getScript(text.char32At(start-1))
                if(script==UScript.COMMON || script==UScript.INHERITED)start-- else break
            }
            while(limit<textLimit) {
                val script=UScript.getScript(text.char32At(limit))
                if(script!=UScript.COMMON && script!=UScript.INHERITED) {
                    if(scriptCode==UScript.INVALID_CODE)scriptCode=script else if(script!=scriptCode)break
                }
                limit++
            }
            return true
        }
        fun adjustLimit(delta: Int){limit+=delta;textLimit+=delta}
    }
    companion object {
        private fun isWide(script: Int): Boolean = script==UScript.BOPOMOFO || script==UScript.HAN || script==UScript.HANGUL || script==UScript.HIRAGANA || script==UScript.KATAKANA
        fun register() {
            val seen=HashMap<String,MutableSet<String>>()
            val sources=getAvailableSources()
            while(sources.hasMoreElements()) {
                val source=sources.nextElement()
                if(source.equals("Any",true))continue
                val targets=getAvailableTargets(source)
                while(targets.hasMoreElements()) {
                    val target=targets.nextElement()
                    val script=try {UScript.getCode(target)?.get(0) ?: UScript.INVALID_CODE}catch(_:MissingResourceException){UScript.INVALID_CODE}
                    if(script==UScript.INVALID_CODE)continue
                    val variants=seen.getOrPut(target){HashSet()}
                    val available=getAvailableVariants(source,target)
                    while(available.hasMoreElements()) {
                        val variant=available.nextElement()
                        if(variants.contains(variant))continue
                        variants.add(variant)
                        val id=TransliteratorIDParser.STVtoID("Any",target,variant)
                        registerInstance(AnyTransliterator(id,target,variant,script))
                        registerSpecialInverse(target,"Null",false)
                    }
                }
            }
        }
    }
    private object WidthFix {val value=getInstance("[[:dt=Nar:][:dt=Wide:]] nfkd")}
}
