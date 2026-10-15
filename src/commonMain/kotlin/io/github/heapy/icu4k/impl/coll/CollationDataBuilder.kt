// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2012-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.Norm2AllModes
import io.github.heapy.icu4k.impl.Normalizer2Impl.Hangul
import io.github.heapy.icu4k.impl.Trie2Writable
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.text.UnicodeSetIterator
import io.github.heapy.icu4k.util.CharsTrie
import io.github.heapy.icu4k.util.CharsTrieBuilder
import io.github.heapy.icu4k.util.StringTrieBuilder

/** Builds runtime mappings and context tries from character/CE mappings. */
internal class CollationDataBuilder {
    interface CEModifier { fun modifyCE32(ce32: Int): Long;fun modifyCE(ce: Long): Long }
    var nfcImpl=Norm2AllModes.getNFCInstance().impl
    var base: CollationData?=null
    var baseSettings: CollationSettings?=null
    var trie: Trie2Writable?=null
    val ce32s=UVector32()
    val ce64s=UVector64()
    val conditionalCE32s=ArrayList<ConditionalCE32>()
    val contextChars=UnicodeSet()
    val contexts=StringBuilder()
    private var contextsEra=0
    val unsafeBackwardSet=UnicodeSet()
    var modified=false
    var fastLatinEnabled=false
    var fastLatinBuilder: CollationFastLatinBuilder?=null
    private var collIter: DataBuilderCollationIterator?=null
    init { ce32s.addElement(0) }
    fun initForTailoring(b: CollationData?) {
        if(trie!=null) throw IllegalStateException("attempt to reuse a CollationDataBuilder")
        if(b==null) throw IllegalArgumentException("null CollationData")
        base=b;trie=Trie2Writable(Collation.FALLBACK_CE32,Collation.FFFD_CE32)
        for(c in 0xc0..0xff) trie!!.set(c,Collation.FALLBACK_CE32)
        trie!!.setRange(Hangul.HANGUL_BASE,Hangul.HANGUL_END,Collation.makeCE32FromTagAndIndex(Collation.HANGUL_TAG,0),true)
        unsafeBackwardSet.addAll(b.unsafeBackwardSet!!)
    }
    fun isCompressibleLeadByte(b: Int): Boolean = base!!.isCompressibleLeadByte(b)
    fun isCompressiblePrimary(p: Long): Boolean = isCompressibleLeadByte(p.toInt() ushr 24)
    fun hasMappings(): Boolean = modified
    fun isAssigned(c: Int): Boolean = Collation.isAssignedCE32(trie!![c])
    fun add(prefix: CharSequence?,s: CharSequence?,ces: LongArray?,cesLength: Int) { val ce32=encodeCEs(ces,cesLength);addCE32(prefix,s,ce32) }
    fun encodeCEs(ces: LongArray?,cesLength: Int): Int {
        if(cesLength<0 || cesLength>Collation.MAX_EXPANSION_LENGTH) throw IllegalArgumentException("mapping to too many CEs")
        if(!isMutable()) throw IllegalStateException("attempt to add mappings after build()")
        if(cesLength==0) return encodeOneCEAsCE32(0)
        if(cesLength==1) return encodeOneCE(longAt(ces,0))
        if(cesLength==2) {
            val ce0=longAt(ces,0);val ce1=longAt(ces,1);val p0=ce0 ushr 32
            if(ce0 and 0xffffffffff00ffL==Collation.COMMON_SECONDARY_CE.toLong() && ce1 and -4278190081L==Collation.COMMON_TERTIARY_CE.toLong() && p0!=0L)
                return p0.toInt() or ((ce0.toInt() and 0xff00) shl 8) or ((ce1.toInt() shr 16) and 0xff00) or Collation.SPECIAL_CE32_LOW_BYTE or Collation.LATIN_EXPANSION_TAG
        }
        val newCE32s=IntArray(Collation.MAX_EXPANSION_LENGTH)
        var i=0
        while(true) { if(i==cesLength) return encodeExpansion32(newCE32s,0,cesLength);val ce32=encodeOneCEAsCE32(longAt(ces,i));if(ce32==Collation.NO_CE32) break;newCE32s[i++]=ce32 }
        return encodeExpansion(ces,0,cesLength)
    }
    fun addCE32(prefix: CharSequence?,s: CharSequence?,ce32: Int) {
        if(s!!.isEmpty()) throw IllegalArgumentException("mapping from empty string")
        if(!isMutable()) throw IllegalStateException("attempt to add mappings after build()")
        val c=cpAt(s,0);val cLength=charCount(c);var oldCE32=trie!![c];val hasContext=prefix!!.isNotEmpty() || s.length>cLength
        if(oldCE32==Collation.FALLBACK_CE32) {
            val b=base!!.getFinalCE32(base!!.getCE32(c))
            if(hasContext || Collation.ce32HasContext(b)) { oldCE32=copyFromBaseCE32(c,b,true);trie!!.set(c,oldCE32) }
        }
        if(!hasContext) {
            if(!isBuilderContextCE32(oldCE32)) trie!!.set(c,ce32)
            else { val cond=getConditionalCE32ForCE32(oldCE32);cond.builtCE32=Collation.NO_CE32;cond.ce32=ce32 }
        } else {
            var cond: ConditionalCE32
            if(!isBuilderContextCE32(oldCE32)) {
                val index=addConditionalCE32("\u0000",oldCE32);trie!!.set(c,makeBuilderContextCE32(index));contextChars.add(c);cond=getConditionalCE32(index)
            } else { cond=getConditionalCE32ForCE32(oldCE32);cond.builtCE32=Collation.NO_CE32 }
            val suffix=s.subSequence(cLength,s.length)
            val context=StringBuilder().append(prefix.length.toChar()).append(prefix).append(suffix).toString()
            unsafeBackwardSet.addAll(suffix)
            while(true) {
                val next=cond.next
                if(next<0) { cond.next=addConditionalCE32(context,ce32);break }
                val nextCond=getConditionalCE32(next);val cmp=context.compareTo(nextCond.context)
                if(cmp<0) { val index=addConditionalCE32(context,ce32);cond.next=index;getConditionalCE32(index).next=next;break }
                if(cmp==0) { nextCond.ce32=ce32;break }
                cond=nextCond
            }
        }
        modified=true
    }
    fun copyFrom(src: CollationDataBuilder?,modifier: CEModifier?) {
        if(!isMutable()) throw IllegalStateException("attempt to copyFrom() after build()")
        val helper=CopyHelper(src,this,modifier)
        for(range in src!!.trie!!) { if(range.leadSurrogate) break;if(range.value!=Collation.UNASSIGNED_CE32 && range.value!=Collation.FALLBACK_CE32) helper.copyRangeCE32(range.startCodePoint,range.endCodePoint,range.value) }
        modified=modified or src.modified
    }
    fun optimize(set: UnicodeSet?) {
        if(set!!.isEmpty()) return
        val iter=UnicodeSetIterator(set)
        while(iter.next() && iter.codepoint!=UnicodeSetIterator.IS_STRING) {
            val c=iter.codepoint;var ce32=trie!![c]
            if(ce32==Collation.FALLBACK_CE32) { ce32=base!!.getFinalCE32(base!!.getCE32(c));ce32=copyFromBaseCE32(c,ce32,true);trie!!.set(c,ce32) }
        }
        modified=true
    }
    fun suppressContractions(set: UnicodeSet?) {
        if(set!!.isEmpty()) return
        val iter=UnicodeSetIterator(set)
        while(iter.next() && iter.codepoint!=UnicodeSetIterator.IS_STRING) {
            val c=iter.codepoint;var ce32=trie!![c]
            if(ce32==Collation.FALLBACK_CE32) { ce32=base!!.getFinalCE32(base!!.getCE32(c));if(Collation.ce32HasContext(ce32)) { ce32=copyFromBaseCE32(c,ce32,false);trie!!.set(c,ce32) } }
            else if(isBuilderContextCE32(ce32)) { ce32=getConditionalCE32ForCE32(ce32).ce32;trie!!.set(c,ce32);contextChars.remove(c) }
        }
        modified=true
    }
    fun enableFastLatin() { fastLatinEnabled=true }
    fun build(data: CollationData?) {
        buildMappings(data)
        if(base!=null) { data!!.numericPrimary=base!!.numericPrimary;data.compressibleBytes=base!!.compressibleBytes;data.numScripts=base!!.numScripts;data.scriptsIndex=base!!.scriptsIndex;data.scriptStarts=base!!.scriptStarts }
        buildFastLatinTable(data)
    }
    fun getCEs(s: CharSequence?,ces: LongArray?,cesLength: Int): Int = getCEs(s,0,ces,cesLength)
    fun getCEs(prefix: CharSequence?,s: CharSequence?,ces: LongArray?,cesLength: Int): Int {
        val length=prefix!!.length
        return if(length==0) getCEs(s,0,ces,cesLength) else getCEs(StringBuilder(prefix).append(s),length,ces,cesLength)
    }
    class ConditionalCE32(var context: String,var ce32: Int) {
        var defaultCE32=Collation.NO_CE32;var builtCE32=Collation.NO_CE32;var era=-1;var next=-1
        fun hasContext(): Boolean = context.length>1
        fun prefixLength(): Int = context[0].code
    }
    fun getCE32FromOffsetCE32(fromBase: Boolean,c: Int,ce32: Int): Int {
        val i=Collation.indexFromCE32(ce32);val dataCE=if(fromBase) longAt(base!!.ces,i) else ce64s.elementAti(i)
        return Collation.makeLongPrimaryCE32(Collation.getThreeBytePrimaryForOffsetData(c,dataCE))
    }
    fun addCE(ce: Long): Int { val length=ce64s.size();for(i in 0 until length) if(ce==ce64s.elementAti(i)) return i;ce64s.addElement(ce);return length }
    fun addCE32(ce32: Int): Int { val length=ce32s.size();for(i in 0 until length) if(ce32==ce32s.elementAti(i)) return i;ce32s.addElement(ce32);return length }
    fun addConditionalCE32(context: String,ce32: Int): Int {
        checkAssertion { context.isNotEmpty() };val index=conditionalCE32s.size
        if(index>Collation.MAX_INDEX) throw IndexOutOfBoundsException("too many context-sensitive mappings")
        conditionalCE32s.add(ConditionalCE32(context,ce32));return index
    }
    fun getConditionalCE32(index: Int): ConditionalCE32 { if(index !in conditionalCE32s.indices) throw IndexOutOfBoundsException();return conditionalCE32s[index] }
    fun getConditionalCE32ForCE32(ce32: Int): ConditionalCE32 = getConditionalCE32(Collation.indexFromCE32(ce32))
    fun encodeOneCE(ce: Long): Int {
        val ce32=encodeOneCEAsCE32(ce);if(ce32!=Collation.NO_CE32) return ce32
        val index=addCE(ce);if(index>Collation.MAX_INDEX) throw IndexOutOfBoundsException("too many mappings")
        return Collation.makeCE32FromTagIndexAndLength(Collation.EXPANSION_TAG,index,1)
    }
    fun encodeExpansion(ces: LongArray?,start: Int,length: Int): Int {
        val first=longAt(ces,start);val max=ce64s.size()-length
        for(i in 0..max) if(first==ce64s.elementAti(i)) {
            if(i>Collation.MAX_INDEX) throw IndexOutOfBoundsException("too many mappings")
            var j=1
            while(true) { if(j==length) return Collation.makeCE32FromTagIndexAndLength(Collation.EXPANSION_TAG,i,length);if(ce64s.elementAti(i+j)!=longAt(ces,start+j)) break;++j }
        }
        val index=ce64s.size();if(index>Collation.MAX_INDEX) throw IndexOutOfBoundsException("too many mappings")
        for(j in 0 until length) ce64s.addElement(longAt(ces,start+j))
        return Collation.makeCE32FromTagIndexAndLength(Collation.EXPANSION_TAG,index,length)
    }
    fun encodeExpansion32(values: IntArray?,start: Int,length: Int): Int {
        val first=intAt(values,start);val max=ce32s.size()-length
        for(i in 0..max) if(first==ce32s.elementAti(i)) {
            if(i>Collation.MAX_INDEX) throw IndexOutOfBoundsException("too many mappings")
            var j=1
            while(true) { if(j==length) return Collation.makeCE32FromTagIndexAndLength(Collation.EXPANSION32_TAG,i,length);if(ce32s.elementAti(i+j)!=intAt(values,start+j)) break;++j }
        }
        val index=ce32s.size();if(index>Collation.MAX_INDEX) throw IndexOutOfBoundsException("too many mappings")
        for(j in 0 until length) ce32s.addElement(intAt(values,start+j))
        return Collation.makeCE32FromTagIndexAndLength(Collation.EXPANSION32_TAG,index,length)
    }
    fun copyFromBaseCE32(c: Int,initialCE32: Int,withContext: Boolean): Int {
        var ce32=initialCE32
        if(!Collation.isSpecialCE32(ce32)) return ce32
        when(Collation.tagFromCE32(ce32)) {
            Collation.LONG_PRIMARY_TAG,Collation.LONG_SECONDARY_TAG,Collation.LATIN_EXPANSION_TAG -> Unit
            Collation.EXPANSION32_TAG -> ce32=encodeExpansion32(base!!.ce32s,Collation.indexFromCE32(ce32),Collation.lengthFromCE32(ce32))
            Collation.EXPANSION_TAG -> ce32=encodeExpansion(base!!.ces,Collation.indexFromCE32(ce32),Collation.lengthFromCE32(ce32))
            Collation.PREFIX_TAG -> {
                val trieIndex=Collation.indexFromCE32(ce32);ce32=base!!.getCE32FromContexts(trieIndex)
                if(!withContext) return copyFromBaseCE32(c,ce32,false)
                val head=ConditionalCE32("",0);val context=StringBuilder("\u0000");var index: Int
                if(Collation.isContractionCE32(ce32)) index=copyContractionsFromBaseCE32(context,c,ce32,head)
                else { ce32=copyFromBaseCE32(c,ce32,true);index=addConditionalCE32(context.toString(),ce32);head.next=index }
                var cond=getConditionalCE32(index);val prefixes=CharsTrie.iterator(base!!.contexts!!,trieIndex+2,0)
                while(prefixes.hasNext()) {
                    val e=prefixes.next();context.setLength(0);context.append(e.chars);reverse(context);context.insert(0,e.chars.length.toChar());ce32=e.value
                    if(Collation.isContractionCE32(ce32)) index=copyContractionsFromBaseCE32(context,c,ce32,cond)
                    else { ce32=copyFromBaseCE32(c,ce32,true);index=addConditionalCE32(context.toString(),ce32);cond.next=index }
                    cond=getConditionalCE32(index)
                }
                ce32=makeBuilderContextCE32(head.next);contextChars.add(c)
            }
            Collation.CONTRACTION_TAG -> {
                if(!withContext) return copyFromBaseCE32(c,base!!.getCE32FromContexts(Collation.indexFromCE32(ce32)),false)
                val head=ConditionalCE32("",0);copyContractionsFromBaseCE32(StringBuilder("\u0000"),c,ce32,head);ce32=makeBuilderContextCE32(head.next);contextChars.add(c)
            }
            Collation.HANGUL_TAG -> throw UnsupportedOperationException("We forbid tailoring of Hangul syllables.")
            Collation.OFFSET_TAG -> ce32=getCE32FromOffsetCE32(true,c,ce32)
            Collation.IMPLICIT_TAG -> ce32=encodeOneCE(Collation.unassignedCEFromCodePoint(c))
            else -> throw AssertionError("copyFromBaseCE32(c, ce32, withContext) requires ce32 == base.getFinalCE32(ce32)")
        }
        return ce32
    }
    fun copyContractionsFromBaseCE32(context: StringBuilder,c: Int,initialCE32: Int,initialCond: ConditionalCE32): Int {
        val trieIndex=Collation.indexFromCE32(initialCE32);var cond=initialCond;var index: Int
        if(initialCE32 and Collation.CONTRACT_SINGLE_CP_NO_MATCH!=0) { checkAssertion { context.length>1 };index=-1 }
        else {
            var ce32=base!!.getCE32FromContexts(trieIndex);checkAssertion { !Collation.isContractionCE32(ce32) };ce32=copyFromBaseCE32(c,ce32,true)
            index=addConditionalCE32(context.toString(),ce32);cond.next=index;cond=getConditionalCE32(index)
        }
        val suffixStart=context.length;val suffixes=CharsTrie.iterator(base!!.contexts!!,trieIndex+2,0)
        while(suffixes.hasNext()) { val e=suffixes.next();context.append(e.chars);val ce32=copyFromBaseCE32(c,e.value,true);index=addConditionalCE32(context.toString(),ce32);cond.next=index;cond=getConditionalCE32(index);context.setLength(suffixStart) }
        checkAssertion { index>=0 };return index
    }
    private class CopyHelper(val src: CollationDataBuilder?,val dest: CollationDataBuilder,val modifier: CEModifier?) {
        val modifiedCEs=LongArray(Collation.MAX_EXPANSION_LENGTH)
        fun copyRangeCE32(start: Int,end: Int,initial: Int) {
            val ce32=copyCE32(initial);dest.trie!!.setRange(start,end,ce32,true)
            if(isBuilderContextCE32(ce32)) dest.contextChars.add(start,end)
        }
        fun copyCE32(initial: Int): Int {
            var ce32=initial
            if(!Collation.isSpecialCE32(ce32)) { val ce=modifier!!.modifyCE32(ce32);if(ce!=Collation.NO_CE) ce32=dest.encodeOneCE(ce) }
            else {
                val tag=Collation.tagFromCE32(ce32)
                if(tag==Collation.EXPANSION32_TAG) {
                    val values=src!!.ce32s.getBuffer();val index=Collation.indexFromCE32(ce32);val length=Collation.lengthFromCE32(ce32);var changed=false
                    for(i in 0 until length) {
                        ce32=intAt(values,index+i);var ce=0L
                        if(Collation.isSpecialCE32(ce32) || modifier!!.modifyCE32(ce32).also { ce=it }==Collation.NO_CE) { if(changed) modifiedCEs[i]=Collation.ceFromCE32(ce32) }
                        else { if(!changed) { for(j in 0 until i) modifiedCEs[j]=Collation.ceFromCE32(intAt(values,index+j));changed=true };modifiedCEs[i]=ce }
                    }
                    ce32=if(changed) dest.encodeCEs(modifiedCEs,length) else dest.encodeExpansion32(values,index,length)
                } else if(tag==Collation.EXPANSION_TAG) {
                    val values=src!!.ce64s.getBuffer();val index=Collation.indexFromCE32(ce32);val length=Collation.lengthFromCE32(ce32);var changed=false
                    for(i in 0 until length) {
                        val srcCE=longAt(values,index+i);val ce=modifier!!.modifyCE(srcCE)
                        if(ce==Collation.NO_CE) { if(changed) modifiedCEs[i]=srcCE }
                        else { if(!changed) { for(j in 0 until i) modifiedCEs[j]=longAt(values,index+j);changed=true };modifiedCEs[i]=ce }
                    }
                    ce32=if(changed) dest.encodeCEs(modifiedCEs,length) else dest.encodeExpansion(values,index,length)
                } else if(tag==Collation.BUILDER_DATA_TAG) {
                    var cond=src!!.getConditionalCE32ForCE32(ce32);checkAssertion { !cond.hasContext() }
                    var index=dest.addConditionalCE32(cond.context,copyCE32(cond.ce32));ce32=makeBuilderContextCE32(index)
                    while(cond.next>=0) { cond=src.getConditionalCE32(cond.next);val previous=dest.getConditionalCE32(index);index=dest.addConditionalCE32(cond.context,copyCE32(cond.ce32));dest.unsafeBackwardSet.addAll(cond.context.substring(cond.prefixLength()+1));previous.next=index }
                } else checkAssertion { tag==Collation.LONG_PRIMARY_TAG || tag==Collation.LONG_SECONDARY_TAG || tag==Collation.LATIN_EXPANSION_TAG || tag==Collation.HANGUL_TAG }
            }
            return ce32
        }
    }
    fun getJamoCE32s(values: IntArray?): Boolean {
        var any=base==null;var needCopy=false
        for(j in 0 until CollationData.JAMO_CE32S_LENGTH) {
            val jamo=jamoCpFromIndex(j);var fromBase=false;var ce32=trie!![jamo];any=any or Collation.isAssignedCE32(ce32)
            if(ce32==Collation.FALLBACK_CE32) { fromBase=true;ce32=base!!.getCE32(jamo) }
            if(Collation.isSpecialCE32(ce32)) when(Collation.tagFromCE32(ce32)) {
                Collation.LONG_PRIMARY_TAG,Collation.LONG_SECONDARY_TAG,Collation.LATIN_EXPANSION_TAG -> Unit
                Collation.EXPANSION32_TAG,Collation.EXPANSION_TAG,Collation.PREFIX_TAG,Collation.CONTRACTION_TAG -> if(fromBase) { ce32=Collation.FALLBACK_CE32;needCopy=true }
                Collation.IMPLICIT_TAG -> { checkAssertion { fromBase };ce32=Collation.FALLBACK_CE32;needCopy=true }
                Collation.OFFSET_TAG -> ce32=getCE32FromOffsetCE32(fromBase,jamo,ce32)
                else -> throw AssertionError("unexpected special tag in ce32=0x${ce32.toUInt().toString(16).padStart(8,'0')}")
            }
            setInt(values,j,ce32)
        }
        if(any && needCopy) for(j in 0 until CollationData.JAMO_CE32S_LENGTH) if(intAt(values,j)==Collation.FALLBACK_CE32) { val jamo=jamoCpFromIndex(j);setInt(values,j,copyFromBaseCE32(jamo,base!!.getCE32(jamo),true)) }
        return any
    }
    fun setDigitTags() {
        val iter=UnicodeSetIterator(UnicodeSet("[:Nd:]"))
        while(iter.next()) {
            checkAssertion { iter.codepoint!=UnicodeSetIterator.IS_STRING };val c=iter.codepoint;var ce32=trie!![c]
            if(ce32!=Collation.FALLBACK_CE32 && ce32!=Collation.UNASSIGNED_CE32) {
                val index=addCE32(ce32);if(index>Collation.MAX_INDEX) throw IndexOutOfBoundsException("too many mappings")
                ce32=Collation.makeCE32FromTagIndexAndLength(Collation.DIGIT_TAG,index,UCharacter.digit(c));trie!!.set(c,ce32)
            }
        }
    }
    fun setLeadSurrogates() {
        for(lead in 0xd800 until 0xdc00) {
            var leadValue=-1;val iterator=trie!!.iteratorForLeadSurrogate(lead.toChar())
            while(iterator.hasNext()) {
                val range=iterator.next();val value=when(range.value) { Collation.UNASSIGNED_CE32 -> Collation.LEAD_ALL_UNASSIGNED;Collation.FALLBACK_CE32 -> Collation.LEAD_ALL_FALLBACK;else -> { leadValue=Collation.LEAD_MIXED;break } }
                if(leadValue<0) leadValue=value else if(leadValue!=value) { leadValue=Collation.LEAD_MIXED;break }
            }
            trie!!.setForLeadSurrogateCodeUnit(lead.toChar(),Collation.makeCE32FromTagAndIndex(Collation.LEAD_SURROGATE_TAG,0) or leadValue)
        }
    }
    fun buildMappings(data: CollationData?) {
        if(!isMutable()) throw IllegalStateException("attempt to build() after build()")
        buildContexts();val jamos=IntArray(CollationData.JAMO_CE32S_LENGTH);var jamoIndex=-1
        if(getJamoCE32s(jamos)) {
            jamoIndex=ce32s.size();for(value in jamos) ce32s.addElement(value)
            var anySpecial=false
            for(i in Hangul.JAMO_L_COUNT until CollationData.JAMO_CE32S_LENGTH) if(Collation.isSpecialCE32(jamos[i])) { anySpecial=true;break }
            val hangulCE32=Collation.makeCE32FromTagAndIndex(Collation.HANGUL_TAG,0);var c=Hangul.HANGUL_BASE
            for(i in 0 until Hangul.JAMO_L_COUNT) { var ce32=hangulCE32;if(!anySpecial && !Collation.isSpecialCE32(jamos[i])) ce32=ce32 or Collation.HANGUL_NO_SPECIAL_JAMO;val limit=c+Hangul.JAMO_VT_COUNT;trie!!.setRange(c,limit-1,ce32,true);c=limit }
        } else {
            var c=Hangul.HANGUL_BASE
            while(c<Hangul.HANGUL_LIMIT) { val ce32=base!!.getCE32(c);checkAssertion { Collation.hasCE32Tag(ce32,Collation.HANGUL_TAG) };val limit=c+Hangul.JAMO_VT_COUNT;trie!!.setRange(c,limit-1,ce32,true);c=limit }
        }
        setDigitTags();setLeadSurrogates();ce32s.setElementAt(trie!![0],0);trie!!.set(0,Collation.makeCE32FromTagAndIndex(Collation.U0000_TAG,0))
        val frozenTrie=trie!!.toTrie2_32();data!!.trie=frozenTrie
        var c=0x10000
        for(lead in 0xd800 until 0xdc00) { if(unsafeBackwardSet.containsSome(c,c+0x3ff)) unsafeBackwardSet.add(lead);c+=0x400 }
        unsafeBackwardSet.freeze();data.ce32s=ce32s.getBuffer();data.ces=ce64s.getBuffer();data.contexts=contexts.toString();data.base=base
        data.jamoCE32s=if(jamoIndex>=0) jamos else base!!.jamoCE32s;data.unsafeBackwardSet=unsafeBackwardSet
    }
    fun clearContexts() { contexts.setLength(0);++contextsEra }
    fun buildContexts() {
        clearContexts();val iter=UnicodeSetIterator(contextChars)
        while(iter.next()) {
            checkAssertion { iter.codepoint!=UnicodeSetIterator.IS_STRING };val c=iter.codepoint;var ce32=trie!![c]
            if(!isBuilderContextCE32(ce32)) throw AssertionError("Impossible: No context data for c in contextChars.")
            ce32=buildContext(getConditionalCE32ForCE32(ce32));trie!!.set(c,ce32)
        }
    }
    fun buildContext(head: ConditionalCE32): Int {
        checkAssertion { !head.hasContext() };checkAssertion { head.next>=0 }
        var prefixBuilder: CharsTrieBuilder?=null;var contractionBuilder: CharsTrieBuilder?=null;var cond=head
        while(true) {
            checkAssertion { cond===head || cond.hasContext() }
            val prefixLength=cond.prefixLength();val prefix=StringBuilder().append(cond.context,0,prefixLength+1);val prefixString=prefix.toString();val first=cond;var last: ConditionalCE32
            do { last=cond;cond.defaultCE32=Collation.NO_CE32;if(cond.next<0) break;cond=getConditionalCE32(cond.next) } while(cond.context.startsWith(prefixString))
            val suffixStart=prefixLength+1;val ce32: Int
            if(last.context.length==suffixStart) { checkAssertion { first===last };ce32=last.ce32;cond=last }
            else {
                if(contractionBuilder==null) contractionBuilder=CharsTrieBuilder() else contractionBuilder.clear()
                var emptySuffix=Collation.NO_CE32;var flags=0
                if(first.context.length==suffixStart) { emptySuffix=first.ce32;cond=getConditionalCE32(first.next) }
                else {
                    flags=flags or Collation.CONTRACT_SINGLE_CP_NO_MATCH;cond=head
                    while(true) {
                        val length=cond.prefixLength();if(length==prefixLength) break
                        if(cond.defaultCE32!=Collation.NO_CE32 && (length==0 || prefixString.regionMatches(prefix.length-length,cond.context,1,length))) emptySuffix=cond.defaultCE32
                        cond=getConditionalCE32(cond.next)
                    }
                    cond=first
                }
                flags=flags or Collation.CONTRACT_NEXT_CCC
                while(true) {
                    val suffix=cond.context.substring(suffixStart);var fcd16=nfcImpl.getFCD16(cpAt(suffix,0))
                    if(fcd16<=0xff) flags=flags and Collation.CONTRACT_NEXT_CCC.inv()
                    fcd16=nfcImpl.getFCD16(cpBefore(suffix,suffix.length));if(fcd16>0xff) flags=flags or Collation.CONTRACT_TRAILING_CCC
                    contractionBuilder.add(suffix,cond.ce32);if(cond===last) break;cond=getConditionalCE32(cond.next)
                }
                val index=addContextTrie(emptySuffix,contractionBuilder);if(index>Collation.MAX_INDEX) throw IndexOutOfBoundsException("too many context-sensitive mappings")
                ce32=Collation.makeCE32FromTagAndIndex(Collation.CONTRACTION_TAG,index) or flags
            }
            checkAssertion { cond===last };first.defaultCE32=ce32
            if(prefixLength==0) { if(cond.next<0) return ce32 }
            else { prefix.deleteRange(0,1);reverse(prefix);if(prefixBuilder==null) prefixBuilder=CharsTrieBuilder();prefixBuilder.add(prefix,ce32);if(cond.next<0) break }
            cond=getConditionalCE32(cond.next)
        }
        checkAssertion { head.defaultCE32!=Collation.NO_CE32 };val index=addContextTrie(head.defaultCE32,prefixBuilder!!)
        if(index>Collation.MAX_INDEX) throw IndexOutOfBoundsException("too many context-sensitive mappings")
        return Collation.makeCE32FromTagAndIndex(Collation.PREFIX_TAG,index)
    }
    fun addContextTrie(defaultCE32: Int,trieBuilder: CharsTrieBuilder): Int {
        val context=StringBuilder().append((defaultCE32 shr 16).toChar()).append(defaultCE32.toChar()).append(trieBuilder.buildCharSequence(StringTrieBuilder.Option.SMALL))
        var index=contexts.indexOf(context.toString());if(index<0) { index=contexts.length;contexts.append(context) };return index
    }
    fun buildFastLatinTable(data: CollationData?) {
        if(!fastLatinEnabled) return
        fastLatinBuilder=CollationFastLatinBuilder()
        if(fastLatinBuilder!!.forData(data)) {
            var header: CharArray?=fastLatinBuilder!!.getHeader();var table: CharArray?=fastLatinBuilder!!.getTable()
            if(base!=null && header.contentEquals(base!!.fastLatinTableHeader) && table.contentEquals(base!!.fastLatinTable)) { fastLatinBuilder=null;header=base!!.fastLatinTableHeader;table=base!!.fastLatinTable }
            data!!.fastLatinTableHeader=header;data.fastLatinTable=table
        } else fastLatinBuilder=null
    }
    fun getCEs(s: CharSequence?,start: Int,ces: LongArray?,cesLength: Int): Int {
        if(collIter==null) collIter=DataBuilderCollationIterator(this,CollationData(nfcImpl))
        return collIter!!.fetchCEs(s,start,ces,cesLength)
    }
    fun isMutable(): Boolean = trie!=null && !unsafeBackwardSet.isFrozen()
    private class DataBuilderCollationIterator(val builder: CollationDataBuilder,val builderData: CollationData): CollationIterator(builderData,false) {
        val jamoCE32s=IntArray(CollationData.JAMO_CE32S_LENGTH)
        var s: CharSequence?=null
        var pos=0
        init {
            builderData.base=builder.base
            for(j in jamoCE32s.indices) jamoCE32s[j]=Collation.makeCE32FromTagAndIndex(Collation.BUILDER_DATA_TAG,jamoCpFromIndex(j)) or IS_BUILDER_JAMO_CE32
            builderData.jamoCE32s=jamoCE32s
        }
        fun fetchCEs(str: CharSequence?,start: Int,ces: LongArray?,initialLength: Int): Int {
            var length=initialLength;builderData.ce32s=builder.ce32s.getBuffer();builderData.ces=builder.ce64s.getBuffer();builderData.contexts=builder.contexts.toString()
            reset();s=str;pos=start
            while(pos<s!!.length) {
                clearCEs();val c=cpAt(s!!,pos);pos+=charCount(c);var ce32=builder.trie!![c]
                val d=if(ce32==Collation.FALLBACK_CE32) { ce32=builder.base!!.getCE32(c);builder.base } else builderData
                appendCEsFromCE32(d,c,ce32,true)
                for(i in 0 until getCEsLength()) { val ce=getCE(i);if(ce!=0L) { if(length<Collation.MAX_EXPANSION_LENGTH) setLong(ces,length,ce);++length } }
            }
            return length
        }
        override fun resetToOffset(newOffset: Int) { reset();pos=newOffset }
        override fun getOffset(): Int = pos
        override fun nextCodePoint(): Int { if(pos==s!!.length) return Collation.SENTINEL_CP;val c=cpAt(s!!,pos);pos+=charCount(c);return c }
        override fun previousCodePoint(): Int { if(pos==0) return Collation.SENTINEL_CP;val c=cpBefore(s!!,pos);pos-=charCount(c);return c }
        override fun forwardNumCodePoints(num: Int) { pos=offset(s!!,pos,num) }
        override fun backwardNumCodePoints(num: Int) { pos=offset(s!!,pos,-num) }
        override fun getDataCE32(c: Int): Int = builder.trie!![c]
        override fun getCE32FromBuilderData(ce32: Int): Int {
            checkAssertion { Collation.hasCE32Tag(ce32,Collation.BUILDER_DATA_TAG) }
            if(ce32 and IS_BUILDER_JAMO_CE32!=0) return builder.trie!![Collation.indexFromCE32(ce32)]
            val cond=builder.getConditionalCE32ForCE32(ce32)
            if(cond.builtCE32==Collation.NO_CE32 || cond.era!=builder.contextsEra) {
                try { cond.builtCE32=builder.buildContext(cond) } catch(e: IndexOutOfBoundsException) { builder.clearContexts();cond.builtCE32=builder.buildContext(cond) }
                cond.era=builder.contextsEra;builderData.contexts=builder.contexts.toString()
            }
            return cond.builtCE32
        }
    }
    companion object {
        private const val IS_BUILDER_JAMO_CE32=0x100
        private val assertionsEnabled=collationIteratorClassAssertionsEnabled(CollationDataBuilder::class)
        private inline fun checkAssertion(condition: () -> Boolean) { if(assertionsEnabled && !condition()) throw AssertionError() }
        fun makeBuilderContextCE32(index: Int): Int = Collation.makeCE32FromTagAndIndex(Collation.BUILDER_DATA_TAG,index)
        fun isBuilderContextCE32(ce32: Int): Boolean = Collation.hasCE32Tag(ce32,Collation.BUILDER_DATA_TAG)
        fun encodeOneCEAsCE32(ce: Long): Int {
            val p=ce ushr 32;val lower=ce.toInt();val t=lower and 0xffff;checkAssertion { t and 0xc000!=0xc000 }
            if(ce and 0xffff00ff00ffL==0L) return p.toInt() or (lower ushr 16) or (t shr 8)
            if(ce and 0xffffffffffL==Collation.COMMON_SEC_AND_TER_CE.toLong()) return Collation.makeLongPrimaryCE32(p)
            if(p==0L && t and 255==0) return Collation.makeLongSecondaryCE32(lower)
            return Collation.NO_CE32
        }
        fun jamoCpFromIndex(index: Int): Int {
            var i=index;if(i<Hangul.JAMO_L_COUNT) return Hangul.JAMO_L_BASE+i;i-=Hangul.JAMO_L_COUNT
            if(i<Hangul.JAMO_V_COUNT) return Hangul.JAMO_V_BASE+i;i-=Hangul.JAMO_V_COUNT
            return Hangul.JAMO_T_BASE+1+i
        }
        private fun intAt(a: IntArray?,i: Int): Int { val v=a!!;if(i !in v.indices) throw IndexOutOfBoundsException();return v[i] }
        private fun longAt(a: LongArray?,i: Int): Long { val v=a!!;if(i !in v.indices) throw IndexOutOfBoundsException();return v[i] }
        private fun setInt(a: IntArray?,i: Int,value: Int) { val v=a!!;if(i !in v.indices) throw IndexOutOfBoundsException();v[i]=value }
        private fun setLong(a: LongArray?,i: Int,value: Long) { val v=a!!;if(i !in v.indices) throw IndexOutOfBoundsException();v[i]=value }
        private fun cpAt(s: CharSequence,i: Int): Int {
            if(s is String && i !in s.indices) throw IndexOutOfBoundsException()
            val c=s[i].code;if(c in 0xd800..0xdbff && i+1<s.length) { val t=s[i+1].code;if(t in 0xdc00..0xdfff) return ((c-0xd800) shl 10)+t-0xdc00+0x10000 };return c
        }
        private fun cpBefore(s: CharSequence,i: Int): Int {
            if(s is String && i-1 !in s.indices) throw IndexOutOfBoundsException()
            val c=s[i-1].code;if(c in 0xdc00..0xdfff && i>1) { val l=s[i-2].code;if(l in 0xd800..0xdbff) return ((l-0xd800) shl 10)+c-0xdc00+0x10000 };return c
        }
        private fun charCount(c: Int): Int = if(c>=0x10000) 2 else 1
        private fun offset(s: CharSequence,start: Int,num: Int): Int {
            val length=s.length;if(start<0 || start>length) throw IndexOutOfBoundsException();var i=start;var n=num
            if(n>=0) { while(n>0 && i<length) { val c=s[i++].code;if(c in 0xd800..0xdbff && i<length && s[i].code in 0xdc00..0xdfff) ++i;--n } }
            else { while(n<0 && i>0) { val c=s[--i].code;if(c in 0xdc00..0xdfff && i>0 && s[i-1].code in 0xd800..0xdbff) --i;++n } }
            if(n!=0) throw IndexOutOfBoundsException();return i
        }
        private fun reverse(s: StringBuilder) {
            val chars=CharArray(s.length) { s[s.length-1-it] };var i=0
            while(i<chars.size-1) { if(chars[i].code in 0xdc00..0xdfff && chars[i+1].code in 0xd800..0xdbff) { val c=chars[i];chars[i]=chars[i+1];chars[i+1]=c;i+=2 } else ++i }
            s.setLength(0);s.append(chars.concatToString())
        }
    }
}
