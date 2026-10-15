// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.util.CharsTrie
import io.github.heapy.icu4k.impl.coll.CollationFastLatin as F

/** One-shot encoder of ICU's fast Latin header, mini CEs and context lists. */
internal class CollationFastLatinBuilder {
    private var ce0=0L; private var ce1=0L
    private val charCEs=Array(F.NUM_FAST_CHARS) { LongArray(2) }
    private val contractionCEs=UVector64(); private val uniqueCEs=UVector64()
    private var miniCEs: CharArray?=null
    var lastSpecialPrimaries=LongArray(NUM_SPECIAL_GROUPS)
    private var firstDigitPrimary=0L; private var firstLatinPrimary=0L; private var lastLatinPrimary=0L; private var firstShortPrimary=0L
    private var shortPrimaryOverflow=false
    private val result=StringBuilder(); private var headerLength=0
    fun forData(data: CollationData?): Boolean {
        if (result.length != 0) throw IllegalStateException("attempt to reuse a CollationFastLatinBuilder")
        if (!loadGroups(data)) return false
        firstShortPrimary=firstDigitPrimary; getCEs(data); encodeUniqueCEs()
        if (shortPrimaryOverflow) { firstShortPrimary=firstLatinPrimary; resetCEs(); getCEs(data); encodeUniqueCEs() }
        val ok=!shortPrimaryOverflow
        if (ok) { encodeCharCEs(); encodeContractions() }
        contractionCEs.removeAllElements(); uniqueCEs.removeAllElements()
        return ok
    }
    fun getHeader(): CharArray {
        if (headerLength < 0) throw settingsNegativeArraySizeException()
        val a=CharArray(headerLength); copyResult(0,headerLength,a); return a
    }
    fun getTable(): CharArray {
        val length=result.length-headerLength
        if (length < 0) throw settingsNegativeArraySizeException()
        val a=CharArray(length); copyResult(headerLength,result.length,a); return a
    }
    private fun copyResult(start: Int,end: Int,a: CharArray) {
        if (start < 0 || end < start || end > result.length || end-start > a.size) throw IndexOutOfBoundsException()
        for (i in start until end) a[i-start]=result[i]
    }
    private fun loadGroups(data: CollationData?): Boolean {
        headerLength=1+NUM_SPECIAL_GROUPS; result.append(((F.VERSION shl 8) or headerLength).toChar())
        for (i in 0 until NUM_SPECIAL_GROUPS) {
            lastSpecialPrimaries.fbSet(i,data!!.getLastPrimaryForGroup(4096+i))
            if (lastSpecialPrimaries.fbAt(i) == 0L) return false
            result.append(0)
        }
        firstDigitPrimary=data!!.getFirstPrimaryForGroup(4100); firstLatinPrimary=data.getFirstPrimaryForGroup(UScript.LATIN); lastLatinPrimary=data.getLastPrimaryForGroup(UScript.LATIN)
        return firstDigitPrimary != 0L && firstLatinPrimary != 0L
    }
    private fun inSameGroup(p: Long,q: Long): Boolean {
        if (p >= firstShortPrimary) return q >= firstShortPrimary else if (q >= firstShortPrimary) return false
        val last=lastSpecialPrimaries.fbAt(NUM_SPECIAL_GROUPS-1)
        if (p > last) return q > last else if (q > last) return false
        checkAssertion { p != 0L && q != 0L }
        var i=0
        while (true) { val boundary=lastSpecialPrimaries.fbAt(i++); if (p <= boundary) return q <= boundary else if (q <= boundary) return false }
    }
    private fun resetCEs() { contractionCEs.removeAllElements(); uniqueCEs.removeAllElements(); shortPrimaryOverflow=false; result.setLength(headerLength) }
    private fun getCEs(data: CollationData?) {
        var i=0; var c=0
        while (true) {
            if (c == F.LATIN_LIMIT) c=F.PUNCT_START else if (c == F.PUNCT_LIMIT) break
            val d: CollationData?; var ce32=data!!.getCE32(c)
            if (ce32 == Collation.FALLBACK_CE32) { d=data.base; ce32=d!!.getCE32(c) } else d=data
            if (getCEsFromCE32(d,c,ce32)) { charCEs[i][0]=ce0; charCEs[i][1]=ce1; addUniqueCE(ce0); addUniqueCE(ce1) }
            else { ce0=Collation.NO_CE; charCEs[i][0]=ce0; ce1=0; charCEs[i][1]=0 }
            if (c == 0 && !isContractionCharCE(ce0)) {
                checkAssertion { contractionCEs.isEmpty() }
                addContractionEntry(F.CONTR_CHAR_MASK,ce0,ce1); charCEs[0][0]=(Collation.NO_CE_PRIMARY shl 32) or CONTRACTION_FLAG; charCEs[0][1]=0
            }
            ++i; ++c
        }
        contractionCEs.addElement(F.CONTR_CHAR_MASK.toLong())
    }
    private fun getCEsFromCE32(data: CollationData?,c: Int,initialCE32: Int): Boolean {
        val ce32=data!!.getFinalCE32(initialCE32); ce1=0
        if (Collation.isSimpleOrLongCE32(ce32)) ce0=Collation.ceFromCE32(ce32) else when (Collation.tagFromCE32(ce32)) {
            Collation.LATIN_EXPANSION_TAG -> { ce0=Collation.latinCE0FromCE32(ce32); ce1=Collation.latinCE1FromCE32(ce32) }
            Collation.EXPANSION32_TAG -> {
                val index=Collation.indexFromCE32(ce32); val length=Collation.lengthFromCE32(ce32)
                if (length > 2) return false
                ce0=Collation.ceFromCE32(data.ce32s!!.fbAt(index)); if (length == 2) ce1=Collation.ceFromCE32(data.ce32s!!.fbAt(index+1))
            }
            Collation.EXPANSION_TAG -> {
                val index=Collation.indexFromCE32(ce32); val length=Collation.lengthFromCE32(ce32)
                if (length > 2) return false
                ce0=data.ces!!.fbAt(index); if (length == 2) ce1=data.ces!!.fbAt(index+1)
            }
            Collation.CONTRACTION_TAG -> { checkAssertion { c >= 0 }; return getCEsFromContractionCE32(data,ce32) }
            Collation.OFFSET_TAG -> { checkAssertion { c >= 0 }; ce0=data.getCEFromOffsetCE32(c,ce32) }
            else -> return false
        }
        if (ce0 == 0L) return ce1 == 0L
        val p0=ce0 ushr 32
        if (p0 == 0L || p0 > lastLatinPrimary) return false
        val lower0=ce0.toInt()
        if (p0 < firstShortPrimary && (lower0 and Collation.SECONDARY_AND_CASE_MASK) != Collation.COMMON_SECONDARY_CE) return false
        if ((lower0 and Collation.ONLY_TERTIARY_MASK) < Collation.COMMON_WEIGHT16) return false
        if (ce1 != 0L) {
            val p1=ce1 ushr 32
            if (if (p1 == 0L) p0 < firstShortPrimary else !inSameGroup(p0,p1)) return false
            val lower1=ce1.toInt()
            if ((lower1 ushr 16) == 0) return false
            if (p1 != 0L && p1 < firstShortPrimary && (lower1 and Collation.SECONDARY_AND_CASE_MASK) != Collation.COMMON_SECONDARY_CE) return false
            // ICU4J checks lower0 again here, rather than lower1.
            if ((lower0 and Collation.ONLY_TERTIARY_MASK) < Collation.COMMON_WEIGHT16) return false
        }
        return ((ce0 or ce1) and Collation.QUATERNARY_MASK.toLong()) == 0L
    }
    private fun getCEsFromContractionCE32(data: CollationData,initialCE32: Int): Boolean {
        val trieIndex=Collation.indexFromCE32(initialCE32); var ce32=data.getCE32FromContexts(trieIndex)
        checkAssertion { !Collation.isContractionCE32(ce32) }
        val index=contractionCEs.size()
        if (getCEsFromCE32(data,Collation.SENTINEL_CP,ce32)) addContractionEntry(F.CONTR_CHAR_MASK,ce0,ce1) else addContractionEntry(F.CONTR_CHAR_MASK,Collation.NO_CE,0)
        var prevX=-1; var add=false
        val suffixes=CharsTrie.iterator(data.contexts!!,trieIndex+2,0)
        while (suffixes.hasNext()) {
            val entry=suffixes.next(); val suffix=entry.chars; val x=F.getCharIndex(suffix.ciChar(0))
            if (x < 0) continue
            if (x == prevX) { if (add) { addContractionEntry(x,Collation.NO_CE,0); add=false }; continue }
            if (add) addContractionEntry(prevX,ce0,ce1)
            ce32=entry.value
            if (suffix.length == 1 && getCEsFromCE32(data,Collation.SENTINEL_CP,ce32)) add=true else { addContractionEntry(x,Collation.NO_CE,0); add=false }
            prevX=x
        }
        if (add) addContractionEntry(prevX,ce0,ce1)
        ce0=(Collation.NO_CE_PRIMARY shl 32) or CONTRACTION_FLAG or index.toLong(); ce1=0
        return true
    }
    private fun addContractionEntry(x: Int,a: Long,b: Long) { contractionCEs.addElement(x.toLong()); contractionCEs.addElement(a); contractionCEs.addElement(b); addUniqueCE(a); addUniqueCE(b) }
    private fun addUniqueCE(initialCE: Long) {
        if (initialCE == 0L || (initialCE ushr 32) == Collation.NO_CE_PRIMARY) return
        val ce=initialCE and Collation.CASE_MASK.toLong().inv(); val i=binarySearch(uniqueCEs.getBuffer(),uniqueCEs.size(),ce)
        if (i < 0) uniqueCEs.insertElementAt(ce,i.inv())
    }
    private fun getMiniCE(initialCE: Long): Int {
        val ce=initialCE and Collation.CASE_MASK.toLong().inv(); val i=binarySearch(uniqueCEs.getBuffer(),uniqueCEs.size(),ce)
        checkAssertion { i >= 0 }; return miniCEs!!.fbAt(i)
    }
    private fun encodeUniqueCEs() {
        miniCEs=CharArray(uniqueCEs.size())
        var group=0; var lastGroupPrimary=lastSpecialPrimaries.fbAt(group)
        checkAssertion { (uniqueCEs.elementAti(0).toInt() ushr 16) != 0 }
        var prevPrimary=0L; var prevSecondary=0; var pri=0; var sec=0; var ter=F.COMMON_TER
        for (i in 0 until uniqueCEs.size()) {
            val ce=uniqueCEs.elementAti(i); val p=ce ushr 32
            if (p != prevPrimary) {
                while (p > lastGroupPrimary) {
                    checkAssertion { pri <= F.MAX_LONG }; result.fbSet(1+group,pri.toChar())
                    if (++group < NUM_SPECIAL_GROUPS) lastGroupPrimary=lastSpecialPrimaries.fbAt(group) else { lastGroupPrimary=0xffffffffL; break }
                }
                if (p < firstShortPrimary) {
                    if (pri == 0) pri=F.MIN_LONG else if (pri < F.MAX_LONG) pri+=F.LONG_INC else { miniCEs!!.fbSet(i,F.BAIL_OUT); continue }
                } else {
                    if (pri < F.MIN_SHORT) pri=F.MIN_SHORT else if (pri < F.MAX_SHORT-F.SHORT_INC) pri+=F.SHORT_INC
                    else { shortPrimaryOverflow=true; miniCEs!!.fbSet(i,F.BAIL_OUT); continue }
                }
                prevPrimary=p; prevSecondary=Collation.COMMON_WEIGHT16; sec=F.COMMON_SEC; ter=F.COMMON_TER
            }
            val lower=ce.toInt(); val s=lower ushr 16
            if (s != prevSecondary) {
                if (pri == 0) {
                    if (sec == 0) sec=F.MIN_SEC_HIGH else if (sec < F.MAX_SEC_HIGH) sec+=F.SEC_INC else { miniCEs!!.fbSet(i,F.BAIL_OUT); continue }
                    prevSecondary=s; ter=F.COMMON_TER
                } else if (s < Collation.COMMON_WEIGHT16) {
                    if (sec == F.COMMON_SEC) sec=F.MIN_SEC_BEFORE else if (sec < F.MAX_SEC_BEFORE) sec+=F.SEC_INC else { miniCEs!!.fbSet(i,F.BAIL_OUT); continue }
                } else if (s == Collation.COMMON_WEIGHT16) sec=F.COMMON_SEC else {
                    if (sec < F.MIN_SEC_AFTER) sec=F.MIN_SEC_AFTER else if (sec < F.MAX_SEC_AFTER) sec+=F.SEC_INC else { miniCEs!!.fbSet(i,F.BAIL_OUT); continue }
                }
                prevSecondary=s; ter=F.COMMON_TER
            }
            checkAssertion { (lower and Collation.CASE_MASK) == 0 }
            val t=lower and Collation.ONLY_TERTIARY_MASK
            if (t > Collation.COMMON_WEIGHT16) { if (ter < F.MAX_TER_AFTER) ++ter else { miniCEs!!.fbSet(i,F.BAIL_OUT); continue } }
            if (pri in F.MIN_LONG..F.MAX_LONG) { checkAssertion { sec == F.COMMON_SEC }; miniCEs!!.fbSet(i,pri or ter) }
            else miniCEs!!.fbSet(i,pri or sec or ter)
        }
    }
    private fun encodeCharCEs() {
        val start=result.length
        repeat(F.NUM_FAST_CHARS) { result.append(0) }
        val indexBase=result.length
        for (i in 0 until F.NUM_FAST_CHARS) {
            val ce=charCEs[i][0]; if (isContractionCharCE(ce)) continue
            var mini=encodeTwoCEs(ce,charCEs[i][1])
            if ((mini ushr 16) > 0) {
                val index=result.length-indexBase
                if (index > F.INDEX_MASK) mini=F.BAIL_OUT else { result.append((mini shr 16).toChar()).append(mini.toChar()); mini=F.EXPANSION or index }
            }
            result.fbSet(start+i,mini.toChar())
        }
    }
    private fun encodeContractions() {
        val indexBase=headerLength+F.NUM_FAST_CHARS; val firstIndex=result.length
        for (i in 0 until F.NUM_FAST_CHARS) {
            val ce=charCEs[i][0]; if (!isContractionCharCE(ce)) continue
            val contractionIndex=result.length-indexBase
            if (contractionIndex > F.INDEX_MASK) { result.fbSet(headerLength+i,F.BAIL_OUT.toChar()); continue }
            var firstTriple=true; var index=ce.toInt() and 0x7fffffff
            while (true) {
                val x=contractionCEs.elementAti(index)
                if (x == F.CONTR_CHAR_MASK.toLong() && !firstTriple) break
                val a=contractionCEs.elementAti(index+1); val b=contractionCEs.elementAti(index+2); val mini=encodeTwoCEs(a,b)
                if (mini == F.BAIL_OUT) result.append((x or (1L shl F.CONTR_LENGTH_SHIFT)).toInt().toChar())
                else if ((mini ushr 16) == 0) { result.append((x or (2L shl F.CONTR_LENGTH_SHIFT)).toInt().toChar()); result.append(mini.toChar()) }
                else { result.append((x or (3L shl F.CONTR_LENGTH_SHIFT)).toInt().toChar()); result.append((mini shr 16).toChar()).append(mini.toChar()) }
                firstTriple=false; index+=3
            }
            result.fbSet(headerLength+i,(F.CONTRACTION or contractionIndex).toChar())
        }
        if (result.length > firstIndex) result.append(F.CONTR_CHAR_MASK.toChar())
    }
    private fun encodeTwoCEs(first: Long,second: Long): Int {
        if (first == 0L) return 0
        if (first == Collation.NO_CE) return F.BAIL_OUT
        checkAssertion { (first ushr 32) != Collation.NO_CE_PRIMARY }
        var mini=getMiniCE(first)
        if (mini == F.BAIL_OUT) return mini
        if (mini >= F.MIN_SHORT) { val c=((first.toInt() and Collation.CASE_MASK) shr 11)+F.LOWER_CASE; mini=mini or c }
        if (second == 0L) return mini
        var mini1=getMiniCE(second); if (mini1 == F.BAIL_OUT) return mini1
        var case1=second.toInt() and Collation.CASE_MASK
        if (mini >= F.MIN_SHORT && (mini and F.SECONDARY_MASK) == F.COMMON_SEC) {
            val sec1=mini1 and F.SECONDARY_MASK; val ter1=mini1 and F.TERTIARY_MASK
            if (sec1 >= F.MIN_SEC_HIGH && case1 == 0 && ter1 == F.COMMON_TER) return (mini and F.SECONDARY_MASK.inv()) or sec1
        }
        if (mini1 <= F.SECONDARY_MASK || F.MIN_SHORT <= mini1) { case1=(case1 shr 11)+F.LOWER_CASE; mini1=mini1 or case1 }
        return (mini shl 16) or mini1
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
    companion object {
        private const val NUM_SPECIAL_GROUPS=4
        private const val CONTRACTION_FLAG=0x80000000L
        private val assertionsEnabled=collationIteratorClassAssertionsEnabled(CollationFastLatinBuilder::class)
        private fun compareInt64AsUnsigned(first: Long,second: Long): Int {
            val a=first+Long.MIN_VALUE; val b=second+Long.MIN_VALUE
            return if (a < b) -1 else if (a > b) 1 else 0
        }
        private fun binarySearch(list: LongArray?,initialLimit: Int,ce: Long): Int {
            if (initialLimit == 0) return -1
            var limit=initialLimit; var start=0
            while (true) {
                val i=((start.toLong()+limit.toLong())/2).toInt(); val cmp=compareInt64AsUnsigned(ce,list!!.fbAt(i))
                if (cmp == 0) return i
                if (cmp < 0) { if (i == start) return start.inv(); limit=i }
                else { if (i == start) return (start+1).inv(); start=i }
            }
        }
        private fun isContractionCharCE(ce: Long): Boolean = (ce ushr 32) == Collation.NO_CE_PRIMARY && ce != Collation.NO_CE
    }
}
private fun fbIndex(size: Int,i: Int): Int { if (i < 0 || i >= size) throw IndexOutOfBoundsException(); return i }
private fun LongArray.fbAt(i: Int): Long = this[fbIndex(size,i)]
private fun IntArray.fbAt(i: Int): Int = this[fbIndex(size,i)]
private fun CharArray.fbAt(i: Int): Int = this[fbIndex(size,i)].code
private fun LongArray.fbSet(i: Int,value: Long) { this[fbIndex(size,i)]=value }
private fun CharArray.fbSet(i: Int,value: Int) { this[fbIndex(size,i)]=value.toChar() }
private fun StringBuilder.fbSet(i: Int,value: Char) { this[fbIndex(length,i)]=value }
