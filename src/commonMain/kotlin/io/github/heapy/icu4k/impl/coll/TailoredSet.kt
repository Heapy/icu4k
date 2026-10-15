// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.Normalizer2Impl.Hangul
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.CharsTrie

/** Finds mappings which differ from the base, including context-dependent strings. */
internal class TailoredSet(private val tailored: UnicodeSet?) {
    private var data: CollationData?=null
    private var baseData: CollationData?=null
    private val unreversedPrefix=StringBuilder()
    private var suffix: String?=null
    fun forData(d: CollationData?) {
        data=d;baseData=d!!.base;checkAssertion { baseData!=null }
        for(range in data!!.trie!!) {
            if(range.leadSurrogate) break
            enumTailoredRange(range.startCodePoint,range.endCodePoint,range.value,this)
        }
    }
    private fun enumTailoredRange(start: Int,end: Int,ce32: Int,ts: TailoredSet) {
        if(ce32==Collation.FALLBACK_CE32) return
        ts.handleCE32(start,end,ce32)
    }
    private fun handleCE32(initialStart: Int,end: Int,initialCE32: Int) {
        checkAssertion { initialCE32!=Collation.FALLBACK_CE32 }
        var ce32=initialCE32
        if(Collation.isSpecialCE32(ce32)) { ce32=data!!.getIndirectCE32(ce32);if(ce32==Collation.FALLBACK_CE32) return }
        var start=initialStart
        do {
            val baseCE32=baseData!!.getFinalCE32(baseData!!.getCE32(start))
            if(Collation.isSelfContainedCE32(ce32) && Collation.isSelfContainedCE32(baseCE32)) { if(ce32!=baseCE32) tailored!!.add(start) }
            else compare(start,ce32,baseCE32)
        } while(++start<=end)
    }
    private fun compare(c: Int,initialCE32: Int,initialBaseCE32: Int) {
        var ce32=initialCE32;var baseCE32=initialBaseCE32
        if(Collation.isPrefixCE32(ce32)) {
            val index=Collation.indexFromCE32(ce32);ce32=data!!.getFinalCE32(data!!.getCE32FromContexts(index))
            if(Collation.isPrefixCE32(baseCE32)) {
                val baseIndex=Collation.indexFromCE32(baseCE32);baseCE32=baseData!!.getFinalCE32(baseData!!.getCE32FromContexts(baseIndex))
                comparePrefixes(c,data!!.contexts,index+2,baseData!!.contexts,baseIndex+2)
            } else addPrefixes(data,c,data!!.contexts,index+2)
        } else if(Collation.isPrefixCE32(baseCE32)) {
            val baseIndex=Collation.indexFromCE32(baseCE32);baseCE32=baseData!!.getFinalCE32(baseData!!.getCE32FromContexts(baseIndex))
            addPrefixes(baseData,c,baseData!!.contexts,baseIndex+2)
        }
        if(Collation.isContractionCE32(ce32)) {
            val index=Collation.indexFromCE32(ce32)
            ce32=if(ce32 and Collation.CONTRACT_SINGLE_CP_NO_MATCH!=0) Collation.NO_CE32 else data!!.getFinalCE32(data!!.getCE32FromContexts(index))
            if(Collation.isContractionCE32(baseCE32)) {
                val baseIndex=Collation.indexFromCE32(baseCE32)
                baseCE32=if(baseCE32 and Collation.CONTRACT_SINGLE_CP_NO_MATCH!=0) Collation.NO_CE32 else baseData!!.getFinalCE32(baseData!!.getCE32FromContexts(baseIndex))
                compareContractions(c,data!!.contexts,index+2,baseData!!.contexts,baseIndex+2)
            } else addContractions(c,data!!.contexts,index+2)
        } else if(Collation.isContractionCE32(baseCE32)) {
            val baseIndex=Collation.indexFromCE32(baseCE32);baseCE32=baseData!!.getFinalCE32(baseData!!.getCE32FromContexts(baseIndex))
            addContractions(c,baseData!!.contexts,baseIndex+2)
        }
        val tag=if(Collation.isSpecialCE32(ce32)) {
            val t=Collation.tagFromCE32(ce32);checkAssertion { t!=Collation.PREFIX_TAG };checkAssertion { t!=Collation.CONTRACTION_TAG };checkAssertion { t!=Collation.OFFSET_TAG };t
        } else -1
        val baseTag=if(Collation.isSpecialCE32(baseCE32)) {
            val t=Collation.tagFromCE32(baseCE32);checkAssertion { t!=Collation.PREFIX_TAG };checkAssertion { t!=Collation.CONTRACTION_TAG };t
        } else -1
        if(baseTag==Collation.OFFSET_TAG) {
            if(!Collation.isLongPrimaryCE32(ce32)) { add(c);return }
            val dataCE=longAt(baseData!!.ces,Collation.indexFromCE32(baseCE32));val p=Collation.getThreeBytePrimaryForOffsetData(c,dataCE)
            if(Collation.primaryFromLongPrimaryCE32(ce32)!=p) { add(c);return }
        }
        if(tag!=baseTag) { add(c);return }
        if(tag==Collation.EXPANSION32_TAG || tag==Collation.EXPANSION_TAG) {
            val length=Collation.lengthFromCE32(ce32);val baseLength=Collation.lengthFromCE32(baseCE32)
            if(length!=baseLength) { add(c);return }
            val idx0=Collation.indexFromCE32(ce32);val idx1=Collation.indexFromCE32(baseCE32)
            for(i in 0 until length) {
                val differs=if(tag==Collation.EXPANSION32_TAG) intAt(data!!.ce32s,idx0+i)!=intAt(baseData!!.ce32s,idx1+i) else longAt(data!!.ces,idx0+i)!=longAt(baseData!!.ces,idx1+i)
                if(differs) { add(c);break }
            }
        } else if(tag==Collation.HANGUL_TAG) {
            val jamos=StringBuilder();val length=Hangul.decompose(c,jamos)
            if(tailored!!.contains(jamos[0].code) || tailored.contains(jamos[1].code) || (length==3 && tailored.contains(jamos[2].code))) add(c)
        } else if(ce32!=baseCE32) add(c)
    }
    private fun comparePrefixes(c: Int,p: CharSequence?,pidx: Int,q: CharSequence?,qidx: Int) {
        val prefixes=CharsTrie(p!!,pidx).iterator();val basePrefixes=CharsTrie(q!!,qidx).iterator()
        var tp: String?=null;var bp: String?=null;val none="\uffff"
        var te: CharsTrie.Entry?=null;var be: CharsTrie.Entry?=null;var tNone=false;var bNone=false
        while(true) {
            if(tp==null) { if(prefixes.hasNext()) { te=prefixes.next();tp=te.chars.toString();tNone=false } else { te=null;tp=none;tNone=true } }
            if(bp==null) { if(basePrefixes.hasNext()) { be=basePrefixes.next();bp=be.chars.toString();bNone=false } else { be=null;bp=none;bNone=true } }
            // Explicit exhaustion flags preserve Java sentinel identity on JS as well.
            if(tNone && bNone) break
            val cmp=tp.compareTo(bp)
            if(cmp<0) { checkAssertion { te!=null };addPrefix(data,tp,c,te!!.value);te=null;tp=null }
            else if(cmp>0) { checkAssertion { be!=null };addPrefix(baseData,bp,c,be!!.value);be=null;bp=null }
            else { setPrefix(tp);checkAssertion { te!=null && be!=null };compare(c,te!!.value,be!!.value);resetPrefix();te=null;be=null;tp=null;bp=null }
        }
    }
    private fun compareContractions(c: Int,p: CharSequence?,pidx: Int,q: CharSequence?,qidx: Int) {
        val suffixes=CharsTrie(p!!,pidx).iterator();val baseSuffixes=CharsTrie(q!!,qidx).iterator()
        var ts: String?=null;var bs: String?=null;val none="\uffff\uffff"
        var te: CharsTrie.Entry?=null;var be: CharsTrie.Entry?=null;var tNone=false;var bNone=false
        while(true) {
            if(ts==null) { if(suffixes.hasNext()) { te=suffixes.next();ts=te.chars.toString();tNone=false } else { te=null;ts=none;tNone=true } }
            if(bs==null) { if(baseSuffixes.hasNext()) { be=baseSuffixes.next();bs=be.chars.toString();bNone=false } else { be=null;bs=none;bNone=true } }
            if(tNone && bNone) break
            val cmp=ts.compareTo(bs)
            if(cmp<0) { addSuffix(c,ts);te=null;ts=null }
            else if(cmp>0) { addSuffix(c,bs);be=null;bs=null }
            else { suffix=ts;compare(c,te!!.value,be!!.value);suffix=null;te=null;be=null;ts=null;bs=null }
        }
    }
    private fun addPrefixes(d: CollationData?,c: Int,p: CharSequence?,pidx: Int) {
        val prefixes=CharsTrie(p!!,pidx).iterator()
        while(prefixes.hasNext()) { val e=prefixes.next();addPrefix(d,e.chars,c,e.value) }
    }
    private fun addPrefix(d: CollationData?,pfx: CharSequence,c: Int,initialCE32: Int) {
        setPrefix(pfx);val ce32=d!!.getFinalCE32(initialCE32)
        if(Collation.isContractionCE32(ce32)) addContractions(c,d.contexts,Collation.indexFromCE32(ce32)+2)
        appendCodePoint(unreversedPrefix,c);tailored!!.add(StringBuilder(unreversedPrefix));resetPrefix()
    }
    private fun addContractions(c: Int,p: CharSequence?,pidx: Int) {
        val suffixes=CharsTrie(p!!,pidx).iterator()
        while(suffixes.hasNext()) addSuffix(c,suffixes.next().chars)
    }
    private fun addSuffix(c: Int,sfx: CharSequence) { val s=StringBuilder(unreversedPrefix);appendCodePoint(s,c);s.append(sfx);tailored!!.add(s) }
    private fun add(c: Int) {
        if(unreversedPrefix.isEmpty() && suffix==null) tailored!!.add(c)
        else { val s=StringBuilder(unreversedPrefix);appendCodePoint(s,c);if(suffix!=null) s.append(suffix);tailored!!.add(s) }
    }
    private fun setPrefix(pfx: CharSequence) {
        unreversedPrefix.setLength(0);unreversedPrefix.append(pfx)
        val chars=CharArray(unreversedPrefix.length) { unreversedPrefix[unreversedPrefix.length-1-it] };var i=0
        while(i<chars.size-1) {
            if(chars[i].code in 0xdc00..0xdfff && chars[i+1].code in 0xd800..0xdbff) { val c=chars[i];chars[i]=chars[i+1];chars[i+1]=c;i+=2 } else ++i
        }
        unreversedPrefix.setLength(0);unreversedPrefix.append(chars.concatToString())
    }
    private fun resetPrefix() { unreversedPrefix.setLength(0) }
    private inline fun checkAssertion(condition: () -> Boolean) { if(assertionsEnabled && !condition()) throw AssertionError() }
    private companion object {
        val assertionsEnabled=collationIteratorClassAssertionsEnabled(TailoredSet::class)
        fun intAt(a: IntArray?,i: Int): Int { val values=a!!;if(i !in values.indices) throw IndexOutOfBoundsException();return values[i] }
        fun longAt(a: LongArray?,i: Int): Long { val values=a!!;if(i !in values.indices) throw IndexOutOfBoundsException();return values[i] }
        fun appendCodePoint(target: StringBuilder,cp: Int) { if(cp !in 0..0x10ffff) throw IllegalArgumentException("Not a valid Unicode code point: 0x"+cp.toUInt().toString(16).uppercase());UTF16.append(target,cp) }
    }
}
