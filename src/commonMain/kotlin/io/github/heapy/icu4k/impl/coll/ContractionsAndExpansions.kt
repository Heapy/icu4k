// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.CharsTrie

/** Enumerates context strings and CE expansions in tailoring and base mappings. */
internal class ContractionsAndExpansions(private val contractions: UnicodeSet?,private val expansions: UnicodeSet?,private val sink: CESink?,private val addPrefixes: Boolean) {
    private var data: CollationData?=null
    private var checkTailored=0
    private val tailored=UnicodeSet()
    private var ranges: UnicodeSet?=null
    private val unreversedPrefix=StringBuilder()
    private var suffix: String?=null
    private val ces=LongArray(Collation.MAX_EXPANSION_LENGTH)
    interface CESink {
        fun handleCE(ce: Long)
        fun handleExpansion(ces: LongArray?,start: Int,length: Int)
    }
    fun forData(d: CollationData?) {
        if(d!!.base!=null) checkTailored=-1
        data=d
        for(range in data!!.trie!!) {
            if(range.leadSurrogate) break
            enumCnERange(range.startCodePoint,range.endCodePoint,range.value,this)
        }
        if(d.base==null) return
        tailored.freeze();checkTailored=1;data=d.base
        for(range in data!!.trie!!) {
            if(range.leadSurrogate) break
            enumCnERange(range.startCodePoint,range.endCodePoint,range.value,this)
        }
    }
    private fun enumCnERange(start: Int,end: Int,ce32: Int,cne: ContractionsAndExpansions) {
        if(cne.checkTailored==0) Unit
        else if(cne.checkTailored<0) {
            if(ce32==Collation.FALLBACK_CE32) return
            cne.tailored.add(start,end)
        } else if(start==end) { if(cne.tailored.contains(start)) return }
        else if(cne.tailored.containsSome(start,end)) {
            if(cne.ranges==null) cne.ranges=UnicodeSet()
            cne.ranges!!.set(start,end).removeAll(cne.tailored)
            val count=cne.ranges!!.getRangeCount()
            for(i in 0 until count) cne.handleCE32(cne.ranges!!.getRangeStart(i),cne.ranges!!.getRangeEnd(i),ce32)
        }
        // ICU4J also visits the original range after visiting the subtracted ranges.
        cne.handleCE32(start,end,ce32)
    }
    fun forCodePoint(initialData: CollationData?,c: Int) {
        var d=initialData!!;var ce32=d.getCE32(c)
        if(ce32==Collation.FALLBACK_CE32) { d=d.base!!;ce32=d.getCE32(c) }
        data=d;handleCE32(c,c,ce32)
    }
    private fun handleCE32(start: Int,end: Int,initialCE32: Int) {
        var ce32=initialCE32
        while(true) {
            if(ce32 and 255<Collation.SPECIAL_CE32_LOW_BYTE) { sink?.handleCE(Collation.ceFromSimpleCE32(ce32));return }
            when(Collation.tagFromCE32(ce32)) {
                Collation.FALLBACK_TAG -> return
                Collation.RESERVED_TAG_3,Collation.BUILDER_DATA_TAG,Collation.LEAD_SURROGATE_TAG -> throw AssertionError("Unexpected CE32 tag type ${Collation.tagFromCE32(ce32)} for ce32=0x${ce32.toUInt().toString(16).padStart(8,'0')}")
                Collation.LONG_PRIMARY_TAG -> { sink?.handleCE(Collation.ceFromLongPrimaryCE32(ce32));return }
                Collation.LONG_SECONDARY_TAG -> { sink?.handleCE(Collation.ceFromLongSecondaryCE32(ce32));return }
                Collation.LATIN_EXPANSION_TAG -> {
                    if(sink!=null) { ces[0]=Collation.latinCE0FromCE32(ce32);ces[1]=Collation.latinCE1FromCE32(ce32);sink.handleExpansion(ces,0,2) }
                    if(unreversedPrefix.isEmpty()) addExpansions(start,end)
                    return
                }
                Collation.EXPANSION32_TAG -> {
                    if(sink!=null) {
                        val index=Collation.indexFromCE32(ce32);val length=Collation.lengthFromCE32(ce32)
                        for(i in 0 until length) ces[i]=Collation.ceFromCE32(intAt(data!!.ce32s,index+i))
                        sink.handleExpansion(ces,0,length)
                    }
                    if(unreversedPrefix.isEmpty()) addExpansions(start,end)
                    return
                }
                Collation.EXPANSION_TAG -> {
                    if(sink!=null) sink.handleExpansion(data!!.ces,Collation.indexFromCE32(ce32),Collation.lengthFromCE32(ce32))
                    if(unreversedPrefix.isEmpty()) addExpansions(start,end)
                    return
                }
                Collation.PREFIX_TAG -> { handlePrefixes(start,end,ce32);return }
                Collation.CONTRACTION_TAG -> { handleContractions(start,end,ce32);return }
                Collation.DIGIT_TAG -> ce32=intAt(data!!.ce32s,Collation.indexFromCE32(ce32))
                Collation.U0000_TAG -> { checkAssertion { start==0 && end==0 };ce32=intAt(data!!.ce32s,0) }
                Collation.HANGUL_TAG -> {
                    if(sink!=null) {
                        val iter=UTF16CollationIterator(data);val hangul=StringBuilder(1)
                        var c=start
                        while(c<=end) {
                            hangul.setLength(0);appendCodePoint(hangul,c);iter.setText(false,hangul,0)
                            val length=iter.fetchCEs();checkAssertion { length>=2 && iter.getCE(length-1)==Collation.NO_CE }
                            sink.handleExpansion(iter.getCEs(),0,length-1);++c
                        }
                    }
                    if(unreversedPrefix.isEmpty()) addExpansions(start,end)
                    return
                }
                Collation.OFFSET_TAG,Collation.IMPLICIT_TAG -> return
            }
        }
    }
    private fun handlePrefixes(start: Int,end: Int,ce32: Int) {
        val index=Collation.indexFromCE32(ce32)
        handleCE32(start,end,data!!.getCE32FromContexts(index))
        if(!addPrefixes) return
        val prefixes=CharsTrie(data!!.contexts!!,index+2).iterator()
        while(prefixes.hasNext()) {
            val entry=prefixes.next();setPrefix(entry.chars)
            addStrings(start,end,contractions);addStrings(start,end,expansions);handleCE32(start,end,entry.value)
        }
        resetPrefix()
    }
    fun handleContractions(start: Int,end: Int,initialCE32: Int) {
        val index=Collation.indexFromCE32(initialCE32)
        if(initialCE32 and Collation.CONTRACT_SINGLE_CP_NO_MATCH!=0) checkAssertion { unreversedPrefix.isNotEmpty() }
        else {
            val ce32=data!!.getCE32FromContexts(index);checkAssertion { !Collation.isContractionCE32(ce32) }
            handleCE32(start,end,ce32)
        }
        val suffixes=CharsTrie(data!!.contexts!!,index+2).iterator()
        while(suffixes.hasNext()) {
            val entry=suffixes.next();suffix=entry.chars.toString();addStrings(start,end,contractions)
            if(unreversedPrefix.isNotEmpty()) addStrings(start,end,expansions)
            handleCE32(start,end,entry.value)
        }
        suffix=null
    }
    fun addExpansions(start: Int,end: Int) {
        if(unreversedPrefix.isEmpty() && suffix==null) expansions?.add(start,end) else addStrings(start,end,expansions)
    }
    fun addStrings(initialStart: Int,end: Int,set: UnicodeSet?) {
        if(set==null) return
        val s=StringBuilder(unreversedPrefix);var start=initialStart
        do { appendCodePoint(s,start);if(suffix!=null) s.append(suffix);set.add(s);s.setLength(unreversedPrefix.length) } while(++start<=end)
    }
    private fun setPrefix(pfx: CharSequence) {
        unreversedPrefix.setLength(0);unreversedPrefix.append(pfx)
        // Match Java StringBuilder.reverse(), including surrogate-pair repair.
        val chars=CharArray(unreversedPrefix.length) { unreversedPrefix[unreversedPrefix.length-1-it] }
        var i=0
        while(i<chars.size-1) {
            if(chars[i].code in 0xdc00..0xdfff && chars[i+1].code in 0xd800..0xdbff) { val c=chars[i];chars[i]=chars[i+1];chars[i+1]=c;i+=2 } else ++i
        }
        unreversedPrefix.setLength(0);unreversedPrefix.append(chars.concatToString())
    }
    private fun resetPrefix() { unreversedPrefix.setLength(0) }
    private inline fun checkAssertion(condition: () -> Boolean) { if(assertionsEnabled && !condition()) throw AssertionError() }
    private companion object {
        val assertionsEnabled=collationIteratorClassAssertionsEnabled(ContractionsAndExpansions::class)
        fun appendCodePoint(target: StringBuilder,cp: Int) {
            if(cp !in 0..0x10ffff) throw IllegalArgumentException("Not a valid Unicode code point: 0x"+cp.toUInt().toString(16).uppercase())
            UTF16.append(target,cp)
        }
        fun intAt(a: IntArray?,i: Int): Int { val values=a!!;if(i !in values.indices) throw IndexOutOfBoundsException();return values[i] }
    }
}
