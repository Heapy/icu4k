// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.coll.collationIteratorClassAssertionsEnabled
import io.github.heapy.icu4k.impl.coll.settingsNegativeArraySizeException

/** StringSearch's processed-CE iterator, retaining stateful shifted-weight handling. */
internal class StringSearchPCE(iter: CollationElementIterator?) {
    private val pceBuffer_ = PCEBuffer()
    private var cei_: CollationElementIterator? = null
    private var strength_ = 0
    private var toShift_ = false
    private var isShifted_ = false
    private var variableTop_ = 0
    init { init(iter) }
    fun init(iter: CollationElementIterator?) {
        cei_ = iter
        val coll = iter!!.getRuleBasedCollator()
        strength_ = coll.getStrength()
        toShift_ = coll.isAlternateHandlingShifted()
        isShifted_ = false
        variableTop_ = coll.getVariableTop()
    }
    internal fun processCE(ce: Int): Long {
        var tertiary = if (strength_ != Collator.PRIMARY && strength_ != Collator.SECONDARY) CollationElementIterator.tertiaryOrder(ce).toLong() else 0L
        var secondary = if (strength_ != Collator.PRIMARY) CollationElementIterator.secondaryOrder(ce).toLong() else 0L
        var primary = CollationElementIterator.primaryOrder(ce).toLong()
        var quaternary = 0L
        // Signed Int comparison is intentional, including for continuation CEs.
        if ((toShift_ && variableTop_ > ce && primary != 0L) || (isShifted_ && primary == 0L)) {
            if (primary == 0L) return CollationElementIterator.IGNORABLE.toLong()
            if (strength_ >= Collator.QUATERNARY) quaternary = primary
            primary = 0; secondary = 0; tertiary = 0
            isShifted_ = true
        } else {
            if (strength_ >= Collator.QUATERNARY) quaternary = 0xffff
            isShifted_ = false
        }
        return (primary shl 48) or (secondary shl 32) or (tertiary shl 16) or quaternary
    }
    fun nextProcessed(range: Range?): Long {
        var low: Int
        var high: Int
        var result: Long
        pceBuffer_.reset()
        do {
            low = cei_!!.getOffset()
            val ce = cei_!!.next()
            high = cei_!!.getOffset()
            if (ce == CollationElementIterator.NULLORDER) { result = PROCESSED_NULLORDER; break }
            result = processCE(ce)
        } while (result == CollationElementIterator.IGNORABLE.toLong())
        if (range != null) { range.ixLow_ = low; range.ixHigh_ = high }
        return result
    }
    fun previousProcessed(range: Range?): Long {
        while (pceBuffer_.empty()) {
            val raw = RCEBuffer()
            var ce: Int
            var finish = false
            do {
                val high = cei_!!.getOffset()
                ce = cei_!!.previous()
                val low = cei_!!.getOffset()
                if (ce == CollationElementIterator.NULLORDER) {
                    if (!raw.empty()) break
                    finish = true; break
                }
                raw.put(ce,low,high)
            } while ((ce and -0x10000) == 0 || isContinuation(ce))
            if (finish) break
            while (!raw.empty()) {
                val r = raw.get()!!
                val result = processCE(r.ce_)
                if (result != CollationElementIterator.IGNORABLE.toLong()) pceBuffer_.put(result,r.low_,r.high_)
            }
        }
        if (pceBuffer_.empty()) {
            if (range != null) { range.ixLow_ = -1; range.ixHigh_ = -1 }
            return CollationElementIterator.NULLORDER.toLong()
        }
        val p = pceBuffer_.get()!!
        if (range != null) { range.ixLow_ = p.low_; range.ixHigh_ = p.high_ }
        return p.ce_
    }
    class Range { var ixLow_ = 0; var ixHigh_ = 0 }
    private class PCEI { var ce_ = 0L; var low_ = 0; var high_ = 0 }
    private class RCEI { var ce_ = 0; var low_ = 0; var high_ = 0 }
    private class PCEBuffer {
        private var buffer_ = arrayOfNulls<PCEI>(16)
        private var bufferIndex_ = 0
        fun reset() { bufferIndex_ = 0 }
        fun empty(): Boolean = bufferIndex_ <= 0
        fun put(ce: Long, low: Int, high: Int) {
            if (bufferIndex_ >= buffer_.size) buffer_ = buffer_.copyOf(buffer_.size+8)
            buffer_[bufferIndex_] = PCEI().also { it.ce_ = ce; it.low_ = low; it.high_ = high }
            bufferIndex_++
        }
        fun get(): PCEI? = if (bufferIndex_ > 0) buffer_[--bufferIndex_] else null
    }
    private class RCEBuffer {
        private var buffer_ = arrayOfNulls<RCEI>(16)
        private var bufferIndex_ = 0
        fun empty(): Boolean = bufferIndex_ <= 0
        fun put(ce: Int, low: Int, high: Int) {
            if (bufferIndex_ >= buffer_.size) buffer_ = buffer_.copyOf(buffer_.size+8)
            buffer_[bufferIndex_] = RCEI().also { it.ce_ = ce; it.low_ = low; it.high_ = high }
            bufferIndex_++
        }
        fun get(): RCEI? = if (bufferIndex_ > 0) buffer_[--bufferIndex_] else null
    }
    companion object {
        const val PROCESSED_NULLORDER = -1L
        private fun isContinuation(ce: Int): Boolean = (ce and 0xc0) == 0xc0
        const val CE_MATCH = -1
        const val CE_NO_MATCH = 0
        const val CE_SKIP_TARG = 1
        const val CE_SKIP_PATN = 2
        fun compareCE64s(targCE: Long, patCE: Long, type: SearchIterator.ElementComparisonType?): Int {
            if (targCE == patCE) return CE_MATCH
            if (type == SearchIterator.ElementComparisonType.STANDARD_ELEMENT_COMPARISON) return CE_NO_MATCH
            val ts = targCE ushr 32; val ps = patCE ushr 32
            val t1 = (ts and 0xffff0000L).toInt(); val p1 = (ps and 0xffff0000L).toInt()
            if (t1 != p1) {
                if (t1 == 0) return CE_SKIP_TARG
                if (p1 == 0 && type == SearchIterator.ElementComparisonType.ANY_BASE_WEIGHT_IS_WILDCARD) return CE_SKIP_PATN
                return CE_NO_MATCH
            }
            val t2 = (ts and 0xffffL).toInt(); val p2 = (ps and 0xffffL).toInt()
            if (t2 != p2) {
                if (t2 == 0) return CE_SKIP_TARG
                if (p2 == 0 && type == SearchIterator.ElementComparisonType.ANY_BASE_WEIGHT_IS_WILDCARD) return CE_SKIP_PATN
                return if (p2 == 5 || (type == SearchIterator.ElementComparisonType.ANY_BASE_WEIGHT_IS_WILDCARD && t2 == 5)) CE_MATCH else CE_NO_MATCH
            }
            val t3 = (targCE and 0xffff0000L).toInt(); val p3 = (patCE and 0xffff0000L).toInt()
            if (t3 != p3) return if (p3 == 0x50000 || (type == SearchIterator.ElementComparisonType.ANY_BASE_WEIGHT_IS_WILDCARD && t3 == 0x50000)) CE_MATCH else CE_NO_MATCH
            return CE_MATCH
        }
    }
}

/** StringSearch's circular history; returned entries are mutable aliases of reused slots. */
internal class StringSearchCEBuffer(
    patternLength: Int,
    patternText: String?,
    comparisonType: SearchIterator.ElementComparisonType?,
    initProcessed: () -> Boolean,
    private val processed: () -> StringSearchPCE?,
    private val assertionsEnabled: Boolean = collationIteratorClassAssertionsEnabled(StringSearchCEBuffer::class),
) {
    class CEI { var ce_ = 0L; var lowIndex_ = 0; var highIndex_ = 0 }
    private var buf_: Array<CEI?>? = null
    internal var bufSize_ = patternLength + 32
        private set
    internal var firstIx_ = 0
        private set
    internal var limitIx_ = 0
        private set
    init {
        if (comparisonType != SearchIterator.ElementComparisonType.STANDARD_ELEMENT_COMPARISON && patternText != null) {
            for (c in patternText) bufSize_ += if (MIGHT_BE_JAMO_L(c)) 8 else 3
        }
        if (initProcessed()) {
            if (bufSize_ < 0) throw settingsNegativeArraySizeException()
            buf_ = arrayOfNulls(bufSize_)
        }
    }
    fun get(index: Int): CEI? = fetch(index,false)
    fun getPrevious(index: Int): CEI? = fetch(index,true)
    private fun fetch(index: Int, previous: Boolean): CEI? {
        if (bufSize_ == 0) throw ArithmeticException("/ by zero")
        val i = index % bufSize_
        if (index >= firstIx_ && index < limitIx_) return entry(i)
        if (index != limitIx_) { if (assertionsEnabled) throw AssertionError(); return null }
        limitIx_++
        if (limitIx_ - firstIx_ >= bufSize_) firstIx_++
        val range = StringSearchPCE.Range()
        if (entry(i) == null) buf_!![i] = CEI()
        val result = entry(i)!!
        result.ce_ = if (previous) processed()!!.previousProcessed(range) else processed()!!.nextProcessed(range)
        result.lowIndex_ = range.ixLow_; result.highIndex_ = range.ixHigh_
        return result
    }
    private fun entry(i: Int): CEI? {
        val b = buf_!!
        if (i < 0 || i >= b.size) throw IndexOutOfBoundsException()
        return b[i]
    }
    companion object {
        fun MIGHT_BE_JAMO_L(c: Char): Boolean = c in '\u1100'..'\u115e' || c in '\u3131'..'\u314e' || c in '\u3165'..'\u3186'
    }
}
