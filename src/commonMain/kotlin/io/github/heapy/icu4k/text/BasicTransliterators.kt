// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.UCaseProps
import io.github.heapy.icu4k.impl.Norm2AllModes
import io.github.heapy.icu4k.impl.createTextTrieMapLock
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.util.ULocale

internal class NullTransliterator : Transliterator("Any-Null",null) {
    override fun handleTransliterate(text: Replaceable,pos: Position,incremental: Boolean) {pos.start=pos.limit}
    override fun addSourceTargetSet(inputFilter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet) { }
}
internal class RemoveTransliterator : Transliterator("Any-Remove",null) {
    override fun handleTransliterate(text: Replaceable,pos: Position,incremental: Boolean) {
        text.replace(pos.start,pos.limit,"")
        val length=pos.limit-pos.start
        pos.contextLimit-=length;pos.limit-=length
    }
    override fun addSourceTargetSet(inputFilter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet) {sourceSet.addAll(getFilterAsUnicodeSet(inputFilter))}
    companion object {fun register(){registerFactory("Any-Remove"){RemoveTransliterator()};registerSpecialInverse("Remove","Null",false)}}
}
internal class CaseTransliterator(private val mode: Int,private val locale: ULocale=ULocale.US) : Transliterator(IDS[mode],null) {
    private val csp=UCaseProps.INSTANCE
    private val caseLocale=UCaseProps.getCaseLocale(locale.getLanguage())
    private val cacheLock=createTextTrieMapLock(this)
    private var utility: SourceTargetUtility?=null
    init {if(mode==2)setMaximumContextLength(2)}
    override fun handleTransliterate(text: Replaceable,pos: Position,incremental: Boolean) {
        if(pos.start>=pos.limit)return
        val iterator=ReplaceableContextIterator()
        val result=StringBuilder()
        var title=true
        if(mode==2) {
            var start=pos.start-1
            while(start>=pos.contextStart) {
                val c=text.char32At(start)
                val type=csp.getTypeOrIgnorable(c)
                if(type>0){title=false;break}else if(type==0)break
                start-=UTF16.getCharCount(c)
            }
        }
        iterator.setText(text);iterator.setIndex(pos.start);iterator.setLimit(pos.limit);iterator.setContextLimits(pos.contextStart,pos.contextLimit)
        while(true) {
            var c=iterator.nextCaseMapCP()
            if(c<0)break
            val type=if(mode==2)csp.getTypeOrIgnorable(c) else 0
            if(type<0)continue
            c=when(mode) {
                0->csp.toFullLower(c,iterator,result,caseLocale)
                1->csp.toFullUpper(c,iterator,result,caseLocale)
                2->if(title)csp.toFullTitle(c,iterator,result,caseLocale)else csp.toFullLower(c,iterator,result,caseLocale)
                else->csp.toFullFolding(c,result,0)
            }
            if(mode==2)title=type==0
            if(iterator.didReachLimit() && incremental){pos.start=iterator.getCaseMapCPStart();return}
            if(c<0)continue
            val delta=if(c<=UCaseProps.MAX_STRING_LENGTH)iterator.replace(result.toString()).also {result.setLength(0)} else iterator.replace(UTF16.valueOf(c))
            if(delta!=0){pos.limit+=delta;pos.contextLimit+=delta}
        }
        pos.start=pos.limit
    }
    override fun addSourceTargetSet(inputFilter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet) {
        val transform=Transform<String,String> {s->when(mode) {
            0->UCharacter.toLowerCase(locale,s);1->UCharacter.toUpperCase(locale,s);2->UCharacter.toTitleCase(locale,s,null);else->UCharacter.foldCase(s,true)
        }}
        val cache=if(mode==3)foldLock.withLock {foldUtility ?: SourceTargetUtility(transform).also {foldUtility=it}}
            else cacheLock.withLock {utility ?: SourceTargetUtility(transform).also {utility=it}}
        cache.addSourceTargetSet(::getFilterAsUnicodeSet,inputFilter,sourceSet,targetSet)
    }
    companion object {
        private val IDS=arrayOf("Any-Lower","Any-Upper","Any-Title","Any-CaseFold")
        private val foldLock=createTextTrieMapLock(IDS)
        private var foldUtility: SourceTargetUtility?=null
        fun register() {
            for(mode in IDS.indices) {
                registerFactory(IDS[mode]){CaseTransliterator(mode)}
                when(mode){0->registerSpecialInverse("Lower","Upper",true);2->registerSpecialInverse("Title","Lower",false);3->registerSpecialInverse("CaseFold","Upper",false)}
            }
        }
    }
}
internal class NormalizationTransliterator(id: String,private val normalizer: Normalizer2) : Transliterator(id,null) {
    override fun handleTransliterate(text: Replaceable,pos: Position,incremental: Boolean) {
        var start=pos.start;var limit=pos.limit
        if(start>=limit)return
        val segment=StringBuilder();val normalized=StringBuilder()
        var c=text.char32At(start)
        do {
            val previous=start;segment.setLength(0)
            do {UTF16.append(segment,c);start+=UTF16.getCharCount(c)}while(start<limit && !normalizer.hasBoundaryBefore(text.char32At(start).also {c=it}))
            if(start==limit && incremental && !normalizer.hasBoundaryAfter(c)){start=previous;break}
            normalizer.normalize(segment,normalized)
            if(segment.toString()!=normalized.toString()) {
                text.replace(previous,start,normalized.toString())
                val delta=normalized.length-(start-previous);start+=delta;limit+=delta
            }
        }while(start<limit)
        pos.start=start;pos.contextLimit+=limit-pos.limit;pos.limit=limit
    }
    override fun addSourceTargetSet(inputFilter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet) {
        val utility=cacheLock.withLock {caches[normalizer] ?: SourceTargetUtility(Transform {normalizer.normalize(it)},normalizer).also {caches[normalizer]=it}}
        utility.addSourceTargetSet(::getFilterAsUnicodeSet,inputFilter,sourceSet,targetSet)
    }
    companion object {
        private val caches=HashMap<Normalizer2,SourceTargetUtility>()
        private val cacheLock=createTextTrieMapLock(caches)
        fun register() {
            val forms=listOf("NFC" to {Normalizer2.getNFCInstance()},"NFD" to {Normalizer2.getNFDInstance()},"NFKC" to {Normalizer2.getNFKCInstance()},"NFKD" to {Normalizer2.getNFKDInstance()},"FCD" to {Norm2AllModes.getFCDNormalizer2()},"FCC" to {Norm2AllModes.getNFCInstance().fcc})
            for((id,norm) in forms)registerFactory("Any-$id"){NormalizationTransliterator(id,norm())}
            registerSpecialInverse("NFC","NFD",true);registerSpecialInverse("NFKC","NFKD",true);registerSpecialInverse("FCC","NFD",false);registerSpecialInverse("FCD","FCD",false)
        }
    }
}
