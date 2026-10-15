// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2010-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.Trie2_32
import io.github.heapy.icu4k.impl.Normalizer2Impl.Hangul
import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.BytesTrie
import io.github.heapy.icu4k.util.CharsTrie
import io.github.heapy.icu4k.util.ICUException

/** Stateful CE iteration over a subclass's code-point cursor. */
internal abstract class CollationIterator {
    protected val trie: Trie2_32?
    protected val data: CollationData
    private var ceBuffer: CEBuffer?
    private var cesIndex = 0
    private var skipped: SkippedState? = null
    private var numCpFwd = -1
    private var isNumeric: Boolean
    private val buffer: CEBuffer get() = ceBuffer!!

    constructor(d: CollationData?) { trie = d!!.trie; data = d; isNumeric = false; ceBuffer = null }
    constructor(d: CollationData?, numeric: Boolean) { trie = d!!.trie; data = d; isNumeric = numeric; ceBuffer = CEBuffer() }
    override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) return false
        other as CollationIterator
        if (buffer.length != other.buffer.length || cesIndex != other.cesIndex || numCpFwd != other.numCpFwd || isNumeric != other.isNumeric) return false
        for (i in 0 until buffer.length) if (buffer.get(i) != other.buffer.get(i)) return false
        return true
    }
    override fun hashCode(): Int = 0
    abstract fun resetToOffset(newOffset: Int)
    abstract fun getOffset(): Int
    fun nextCE(): Long {
        val b = buffer
        if (cesIndex < b.length) return b.get(cesIndex++)
        checkAssertion { cesIndex == b.length }
        b.incLength()
        val pair = handleNextCE32()
        val c = (pair shr 32).toInt()
        var ce32 = pair.toInt()
        var t = ce32 and 0xff
        if (t < Collation.SPECIAL_CE32_LOW_BYTE) return b.set(cesIndex++, simpleCE(ce32, t))
        val d: CollationData
        if (t == Collation.SPECIAL_CE32_LOW_BYTE) {
            if (c < 0) return b.set(cesIndex++, Collation.NO_CE)
            d = data.base!!
            ce32 = d.getCE32(c)
            t = ce32 and 0xff
            if (t < Collation.SPECIAL_CE32_LOW_BYTE) return b.set(cesIndex++, simpleCE(ce32, t))
        } else d = data
        if (t == Collation.LONG_PRIMARY_CE32_LOW_BYTE) return b.set(cesIndex++, ((ce32 - t).toLong() shl 32) or Collation.COMMON_SEC_AND_TER_CE.toLong())
        return nextCEFromCE32(d, c, ce32)
    }
    fun fetchCEs(): Int {
        while (nextCE() != Collation.NO_CE) cesIndex = buffer.length
        return buffer.length
    }
    fun setCurrentCE(ce: Long) { checkAssertion { cesIndex > 0 }; buffer.set(cesIndex - 1, ce) }
    fun previousCE(offsets: UVector32?): Long {
        val b = buffer
        if (b.length > 0) return b.get(--b.length)
        offsets!!.removeAllElements()
        val limitOffset = getOffset()
        val c = previousCodePoint()
        if (c < 0) return Collation.NO_CE
        if (data.isUnsafeBackward(c, isNumeric)) return previousCEUnsafe(c, offsets)
        var ce32 = data.getCE32(c)
        val d: CollationData
        if (ce32 == Collation.FALLBACK_CE32) { d = data.base!!; ce32 = d.getCE32(c) } else d = data
        if (Collation.isSimpleOrLongCE32(ce32)) return Collation.ceFromCE32(ce32)
        appendCEsFromCE32(d, c, ce32, false)
        if (b.length > 1) {
            offsets.addElement(getOffset())
            while (offsets.size() <= b.length) offsets.addElement(limitOffset)
        }
        return b.get(--b.length)
    }
    fun getCEsLength(): Int = buffer.length
    fun getCE(i: Int): Long = buffer.get(i)
    fun getCEs(): LongArray = buffer.getCEs()
    fun clearCEs() { buffer.length = 0; cesIndex = 0 }
    fun clearCEsIfNoneRemaining() { if (cesIndex == buffer.length) clearCEs() }
    abstract fun nextCodePoint(): Int
    abstract fun previousCodePoint(): Int
    protected fun reset() { buffer.length = 0; cesIndex = 0; skipped?.clear() }
    protected fun reset(numeric: Boolean) { if (ceBuffer == null) ceBuffer = CEBuffer(); reset(); isNumeric = numeric }
    protected open fun handleNextCE32(): Long {
        val c = nextCodePoint()
        return if (c < 0) NO_CP_AND_CE32 else makeCodePointAndCE32Pair(c, data.getCE32(c))
    }
    protected open fun makeCodePointAndCE32Pair(c: Int, ce32: Int): Long = (c.toLong() shl 32) or (ce32.toLong() and 0xffffffffL)
    protected open fun handleGetTrailSurrogate(): Char = '\u0000'
    protected open fun forbidSurrogateCodePoints(): Boolean = false
    protected abstract fun forwardNumCodePoints(num: Int)
    protected abstract fun backwardNumCodePoints(num: Int)
    protected open fun getDataCE32(c: Int): Int = data.getCE32(c)
    protected open fun getCE32FromBuilderData(ce32: Int): Int = throw ICUException("internal program error: should be unreachable")
    protected fun appendCEsFromCE32(initialData: CollationData?, codePoint: Int, initialCE32: Int, forward: Boolean) {
        var d = initialData
        var c = codePoint
        var ce32 = initialCE32
        while (Collation.isSpecialCE32(ce32)) {
            when (Collation.tagFromCE32(ce32)) {
                Collation.FALLBACK_TAG, Collation.RESERVED_TAG_3 -> throw ICUException("internal program error: should be unreachable")
                Collation.LONG_PRIMARY_TAG -> { buffer.append(Collation.ceFromLongPrimaryCE32(ce32)); return }
                Collation.LONG_SECONDARY_TAG -> { buffer.append(Collation.ceFromLongSecondaryCE32(ce32)); return }
                Collation.LATIN_EXPANSION_TAG -> {
                    buffer.ensureAppendCapacity(2)
                    buffer.set(buffer.length, Collation.latinCE0FromCE32(ce32)); buffer.set(buffer.length + 1, Collation.latinCE1FromCE32(ce32))
                    buffer.length += 2; return
                }
                Collation.EXPANSION32_TAG -> {
                    var index = Collation.indexFromCE32(ce32); var length = Collation.lengthFromCE32(ce32)
                    buffer.ensureAppendCapacity(length)
                    do { buffer.appendUnsafe(Collation.ceFromCE32(d!!.ce32s!!.ciAt(index++))) } while (--length > 0)
                    return
                }
                Collation.EXPANSION_TAG -> {
                    var index = Collation.indexFromCE32(ce32); var length = Collation.lengthFromCE32(ce32)
                    buffer.ensureAppendCapacity(length)
                    do { buffer.appendUnsafe(d!!.ces!!.ciAt(index++)) } while (--length > 0)
                    return
                }
                Collation.BUILDER_DATA_TAG -> {
                    ce32 = getCE32FromBuilderData(ce32)
                    if (ce32 == Collation.FALLBACK_CE32) { d = data.base; ce32 = d!!.getCE32(c) }
                }
                Collation.PREFIX_TAG -> {
                    if (forward) backwardNumCodePoints(1)
                    ce32 = getCE32FromPrefix(d!!, ce32)
                    if (forward) forwardNumCodePoints(1)
                }
                Collation.CONTRACTION_TAG -> {
                    val index = Collation.indexFromCE32(ce32)
                    val defaultCE32 = d!!.getCE32FromContexts(index)
                    if (!forward) { ce32 = defaultCE32; continue }
                    val nextCp: Int
                    if (skipped == null && numCpFwd < 0) {
                        nextCp = nextCodePoint()
                        if (nextCp < 0) { ce32 = defaultCE32; continue }
                        if ((ce32 and Collation.CONTRACT_NEXT_CCC) != 0 && !CollationFCD.mayHaveLccc(nextCp)) {
                            backwardNumCodePoints(1); ce32 = defaultCE32; continue
                        }
                    } else {
                        nextCp = nextSkippedCodePoint()
                        if (nextCp < 0) { ce32 = defaultCE32; continue }
                        if ((ce32 and Collation.CONTRACT_NEXT_CCC) != 0 && !CollationFCD.mayHaveLccc(nextCp)) {
                            backwardNumSkipped(1); ce32 = defaultCE32; continue
                        }
                    }
                    ce32 = nextCE32FromContraction(d, ce32, d.contexts!!, index + 2, defaultCE32, nextCp)
                    if (ce32 == Collation.NO_CE32) return
                }
                Collation.DIGIT_TAG -> {
                    if (isNumeric) { appendNumericCEs(ce32, forward); return }
                    ce32 = d!!.ce32s!!.ciAt(Collation.indexFromCE32(ce32))
                }
                Collation.U0000_TAG -> { checkAssertion { c == 0 }; ce32 = d!!.ce32s!!.ciAt(0) }
                Collation.HANGUL_TAG -> {
                    val jamo = d!!.jamoCE32s!!
                    c -= Hangul.HANGUL_BASE
                    val t = c % Hangul.JAMO_T_COUNT
                    c /= Hangul.JAMO_T_COUNT
                    val v = c % Hangul.JAMO_V_COUNT
                    c /= Hangul.JAMO_V_COUNT
                    if ((ce32 and Collation.HANGUL_NO_SPECIAL_JAMO) != 0) {
                        buffer.ensureAppendCapacity(if (t == 0) 2 else 3)
                        buffer.set(buffer.length, Collation.ceFromCE32(jamo.ciAt(c))); buffer.set(buffer.length + 1, Collation.ceFromCE32(jamo.ciAt(19 + v)))
                        buffer.length += 2
                        if (t != 0) buffer.appendUnsafe(Collation.ceFromCE32(jamo.ciAt(39 + t)))
                        return
                    }
                    appendCEsFromCE32(d, Collation.SENTINEL_CP, jamo.ciAt(c), forward)
                    appendCEsFromCE32(d, Collation.SENTINEL_CP, jamo.ciAt(19 + v), forward)
                    if (t == 0) return
                    ce32 = jamo.ciAt(39 + t); c = Collation.SENTINEL_CP
                }
                Collation.LEAD_SURROGATE_TAG -> {
                    checkAssertion { forward }; checkAssertion { isLeadSurrogate(c) }
                    val trail = handleGetTrailSurrogate()
                    if (trail.code in 0xdc00..0xdfff) {
                        c = ((c.toChar().code - 0xd800) shl 10) + trail.code - 0xdc00 + 0x10000
                        val leadType = ce32 and Collation.LEAD_TYPE_MASK
                        if (leadType == Collation.LEAD_ALL_UNASSIGNED) ce32 = Collation.UNASSIGNED_CE32
                        else if (leadType == Collation.LEAD_ALL_FALLBACK) { d = d!!.base; ce32 = d!!.getCE32FromSupplementary(c) }
                        else {
                            ce32 = d!!.getCE32FromSupplementary(c)
                            if (ce32 == Collation.FALLBACK_CE32) { d = d.base; ce32 = d!!.getCE32FromSupplementary(c) }
                        }
                    } else ce32 = Collation.UNASSIGNED_CE32
                }
                Collation.OFFSET_TAG -> { checkAssertion { c >= 0 }; val ce = d!!.getCEFromOffsetCE32(c, ce32); buffer.append(ce); return }
                Collation.IMPLICIT_TAG -> {
                    checkAssertion { c >= 0 }
                    if (isSurrogate(c) && forbidSurrogateCodePoints()) ce32 = Collation.FFFD_CE32
                    else { buffer.append(Collation.unassignedCEFromCodePoint(c)); return }
                }
            }
        }
        buffer.append(Collation.ceFromSimpleCE32(ce32))
    }
    private fun nextCEFromCE32(d: CollationData, c: Int, ce32: Int): Long {
        --buffer.length
        appendCEsFromCE32(d, c, ce32, true)
        return buffer.get(cesIndex++)
    }
    private fun getCE32FromPrefix(d: CollationData, initial: Int): Int {
        var index = Collation.indexFromCE32(initial)
        var ce32 = d.getCE32FromContexts(index)
        index += 2
        var lookBehind = 0
        val prefixes = CharsTrie(d.contexts!!, index)
        while (true) {
            val c = previousCodePoint()
            if (c < 0) break
            ++lookBehind
            val match = prefixes.nextForCodePoint(c)
            if (match.hasValue()) ce32 = prefixes.getValue()
            if (!match.hasNext()) break
        }
        forwardNumCodePoints(lookBehind)
        return ce32
    }
    private fun nextSkippedCodePoint(): Int {
        if (skipped != null && skipped!!.hasNext()) return skipped!!.next()
        if (numCpFwd == 0) return Collation.SENTINEL_CP
        val c = nextCodePoint()
        if (skipped != null && !skipped!!.isEmpty() && c >= 0) skipped!!.incBeyond()
        if (numCpFwd > 0 && c >= 0) --numCpFwd
        return c
    }
    private fun backwardNumSkipped(count: Int) {
        var n = count
        if (skipped != null && !skipped!!.isEmpty()) n = skipped!!.backwardNumCodePoints(n)
        backwardNumCodePoints(n)
        if (numCpFwd >= 0) numCpFwd += n
    }
    private fun nextCE32FromContraction(d: CollationData, contractionCE32: Int, chars: CharSequence, offset: Int, initial: Int, next: Int): Int {
        var ce32 = initial; var c = next
        var lookAhead = 1; var sinceMatch = 1
        val suffixes = CharsTrie(chars, offset)
        if (skipped != null && !skipped!!.isEmpty()) skipped!!.saveTrieState(suffixes)
        var match = suffixes.firstForCodePoint(c)
        while (true) {
            if (match.hasValue()) {
                ce32 = suffixes.getValue()
                if (!match.hasNext()) return ce32
                c = nextSkippedCodePoint()
                if (c < 0) return ce32
                if (skipped != null && !skipped!!.isEmpty()) skipped!!.saveTrieState(suffixes)
                sinceMatch = 1
            } else {
                val nextCp = if (match == BytesTrie.Result.NO_MATCH) Collation.SENTINEL_CP else nextSkippedCodePoint()
                if (match == BytesTrie.Result.NO_MATCH || nextCp < 0) {
                    if ((contractionCE32 and Collation.CONTRACT_TRAILING_CCC) != 0 &&
                        ((contractionCE32 and Collation.CONTRACT_SINGLE_CP_NO_MATCH) == 0 || sinceMatch < lookAhead)) {
                        if (sinceMatch > 1) {
                            backwardNumSkipped(sinceMatch); c = nextSkippedCodePoint()
                            lookAhead -= sinceMatch - 1; sinceMatch = 1
                        }
                        if (d.getFCD16(c) > 0xff) return nextCE32FromDiscontiguousContraction(d, suffixes, ce32, lookAhead, c)
                    }
                    break
                }
                c = nextCp; ++sinceMatch
            }
            ++lookAhead
            match = suffixes.nextForCodePoint(c)
        }
        backwardNumSkipped(sinceMatch)
        return ce32
    }
    private fun nextCE32FromDiscontiguousContraction(initialData: CollationData, suffixes: CharsTrie, initial: Int, initialLookAhead: Int, first: Int): Int {
        var d = initialData; var ce32 = initial; var lookAhead = initialLookAhead; var c = first
        var fcd16 = d.getFCD16(c)
        checkAssertion { fcd16 > 0xff }
        val nextCp = nextSkippedCodePoint()
        if (nextCp < 0) { backwardNumSkipped(1); return ce32 }
        ++lookAhead
        var prevCC = fcd16 and 0xff
        fcd16 = d.getFCD16(nextCp)
        if (fcd16 <= 0xff) { backwardNumSkipped(2); return ce32 }
        if (skipped == null || skipped!!.isEmpty()) {
            if (skipped == null) skipped = SkippedState()
            suffixes.reset()
            if (lookAhead > 2) {
                backwardNumCodePoints(lookAhead)
                suffixes.firstForCodePoint(nextCodePoint())
                for (i in 3 until lookAhead) suffixes.nextForCodePoint(nextCodePoint())
                forwardNumCodePoints(2)
            }
            skipped!!.saveTrieState(suffixes)
        } else skipped!!.resetToTrieState(suffixes)
        skipped!!.setFirstSkipped(c)
        var sinceMatch = 2
        c = nextCp
        while (true) {
            val match = if (prevCC < (fcd16 shr 8)) suffixes.nextForCodePoint(c) else null
            if (match != null && match.hasValue()) {
                ce32 = suffixes.getValue(); sinceMatch = 0; skipped!!.recordMatch()
                if (!match.hasNext()) break
                skipped!!.saveTrieState(suffixes)
            } else {
                skipped!!.skip(c); skipped!!.resetToTrieState(suffixes); prevCC = fcd16 and 0xff
            }
            c = nextSkippedCodePoint()
            if (c < 0) break
            ++sinceMatch
            fcd16 = d.getFCD16(c)
            if (fcd16 <= 0xff) break
        }
        backwardNumSkipped(sinceMatch)
        val top = skipped!!.isEmpty()
        skipped!!.replaceMatch()
        if (top && !skipped!!.isEmpty()) {
            c = Collation.SENTINEL_CP
            while (true) {
                appendCEsFromCE32(d, c, ce32, true)
                if (!skipped!!.hasNext()) break
                c = skipped!!.next(); ce32 = getDataCE32(c)
                if (ce32 == Collation.FALLBACK_CE32) { d = data.base!!; ce32 = d.getCE32(c) } else d = data
            }
            skipped!!.clear(); ce32 = Collation.NO_CE32
        }
        return ce32
    }
    private fun previousCEUnsafe(first: Int, offsets: UVector32): Long {
        var c = first; var numBackward = 1
        while (true) { c = previousCodePoint(); if (c < 0) break; ++numBackward; if (!data.isUnsafeBackward(c, isNumeric)) break }
        numCpFwd = numBackward; cesIndex = 0
        checkAssertion { buffer.length == 0 }
        var offset = getOffset()
        while (numCpFwd > 0) {
            --numCpFwd; nextCE()
            checkAssertion { buffer.get(buffer.length - 1) != Collation.NO_CE }
            cesIndex = buffer.length
            checkAssertion { offsets.size() < buffer.length }
            offsets.addElement(offset)
            offset = getOffset()
            while (offsets.size() < buffer.length) offsets.addElement(offset)
        }
        checkAssertion { offsets.size() == buffer.length }
        offsets.addElement(offset); numCpFwd = -1
        backwardNumCodePoints(numBackward); cesIndex = 0
        return buffer.get(--buffer.length)
    }
    private fun appendNumericCEs(initial: Int, forward: Boolean) {
        var ce32 = initial
        val digits = StringBuilder()
        if (forward) {
            while (true) {
                digits.append(Collation.digitFromCE32(ce32))
                if (numCpFwd == 0) break
                val c = nextCodePoint()
                if (c < 0) break
                ce32 = data.getCE32(c)
                if (ce32 == Collation.FALLBACK_CE32) ce32 = data.base!!.getCE32(c)
                if (!Collation.hasCE32Tag(ce32, Collation.DIGIT_TAG)) { backwardNumCodePoints(1); break }
                if (numCpFwd > 0) --numCpFwd
            }
        } else {
            while (true) {
                digits.append(Collation.digitFromCE32(ce32))
                val c = previousCodePoint()
                if (c < 0) break
                ce32 = data.getCE32(c)
                if (ce32 == Collation.FALLBACK_CE32) ce32 = data.base!!.getCE32(c)
                if (!Collation.hasCE32Tag(ce32, Collation.DIGIT_TAG)) { forwardNumCodePoints(1); break }
            }
            digits.reverse()
        }
        var pos = 0
        do {
            while (pos < digits.length - 1 && digits.ciChar(pos).code == 0) ++pos
            val length = minOf(254, digits.length - pos)
            appendNumericSegmentCEs(digits.subSequence(pos, pos + length))
            pos += length
        } while (pos < digits.length)
    }
    private fun appendNumericSegmentCEs(digits: CharSequence) {
        var length = digits.length
        checkAssertion { length in 1..254 }; checkAssertion { length == 1 || digits.ciChar(0).code != 0 }
        val numericPrimary = data.numericPrimary
        if (length <= 7) {
            var value = digits.ciChar(0).code
            for (i in 1 until length) value = value * 10 + digits.ciChar(i).code
            var firstByte = 2; var numBytes = 74
            if (value < numBytes) { buffer.append(Collation.makeCE(numericPrimary or ((firstByte + value) shl 16).toLong())); return }
            value -= numBytes; firstByte += numBytes; numBytes = 40
            if (value < numBytes * 254) {
                buffer.append(Collation.makeCE(numericPrimary or ((firstByte + value / 254) shl 16).toLong() or ((2 + value % 254) shl 8).toLong())); return
            }
            value -= numBytes * 254; firstByte += numBytes; numBytes = 16
            if (value < numBytes * 254 * 254) {
                var primary = numericPrimary or (2 + value % 254).toLong()
                value /= 254; primary = primary or ((2 + value % 254) shl 8).toLong()
                value /= 254; primary = primary or ((firstByte + value % 254) shl 16).toLong()
                buffer.append(Collation.makeCE(primary)); return
            }
        }
        checkAssertion { length >= 7 }
        val numPairs = (length + 1) / 2
        var primary = numericPrimary or ((128 + numPairs) shl 16).toLong()
        while (digits.ciChar(length - 1).code == 0 && digits.ciChar(length - 2).code == 0) length -= 2
        var pair: Int; var pos: Int
        if ((length and 1) != 0) { pair = digits.ciChar(0).code; pos = 1 }
        else { pair = digits.ciChar(0).code * 10 + digits.ciChar(1).code; pos = 2 }
        pair = 11 + 2 * pair
        var shift = 8
        while (pos < length) {
            if (shift == 0) { primary = primary or pair.toLong(); buffer.append(Collation.makeCE(primary)); primary = numericPrimary; shift = 16 }
            else { primary = primary or (pair shl shift).toLong(); shift -= 8 }
            pair = 11 + 2 * (digits.ciChar(pos).code * 10 + digits.ciChar(pos + 1).code); pos += 2
        }
        primary = primary or ((pair - 1) shl shift).toLong()
        buffer.append(Collation.makeCE(primary))
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
    private class CEBuffer {
        var length = 0
        private var values = LongArray(40)
        fun append(ce: Long) { if (length >= 40) ensureAppendCapacity(1); set(length++, ce) }
        fun appendUnsafe(ce: Long) { set(length++, ce) }
        fun ensureAppendCapacity(appCap: Int) {
            var capacity = values.size
            if (length + appCap <= capacity) return
            do { capacity *= if (capacity < 1000) 4 else 2 } while (capacity < length + appCap)
            val next = LongArray(capacity); values.copyInto(next, 0, 0, length); values = next
        }
        fun incLength() { if (length >= 40) ensureAppendCapacity(1); ++length }
        fun set(i: Int, ce: Long): Long { values[ciIndex(values.size, i)] = ce; return ce }
        fun get(i: Int): Long = values.ciAt(i)
        fun getCEs(): LongArray = values
    }
    private class SkippedState {
        private val oldBuffer = StringBuilder()
        private val newBuffer = StringBuilder()
        private var pos = 0
        private var skipLengthAtMatch = 0
        private val state = CharsTrie.State()
        fun clear() { oldBuffer.setLength(0); pos = 0 }
        fun isEmpty(): Boolean = oldBuffer.isEmpty()
        fun hasNext(): Boolean = pos < oldBuffer.length
        fun next(): Int { val c = ciCodePointAt(oldBuffer, pos); pos += UTF16.getCharCount(c); return c }
        fun incBeyond() { if (skippedAssertionsEnabled && hasNext()) throw AssertionError(); ++pos }
        fun backwardNumCodePoints(n: Int): Int {
            val length = oldBuffer.length; val beyond = pos - length
            return if (beyond > 0) {
                if (beyond >= n) { pos -= n; n } else { pos = ciOffsetByCodePoints(oldBuffer, length, beyond - n); beyond }
            } else { pos = ciOffsetByCodePoints(oldBuffer, pos, -n); 0 }
        }
        fun setFirstSkipped(c: Int) { skipLengthAtMatch = 0; newBuffer.setLength(0); UTF16.append(newBuffer, c) }
        fun skip(c: Int) { UTF16.append(newBuffer, c) }
        fun recordMatch() { skipLengthAtMatch = newBuffer.length }
        fun replaceMatch() {
            if (pos > oldBuffer.length) pos = oldBuffer.length
            oldBuffer.deleteRange(0, pos).insert(0, newBuffer.subSequence(0, skipLengthAtMatch)); pos = 0
        }
        fun saveTrieState(trie: CharsTrie) { trie.saveState(state) }
        fun resetToTrieState(trie: CharsTrie) { trie.resetToState(state) }
        companion object { private val skippedAssertionsEnabled = collationIteratorClassAssertionsEnabled(SkippedState::class) }
    }
    companion object {
        protected const val NO_CP_AND_CE32: Long = -4294967104L
        protected fun isLeadSurrogate(c: Int): Boolean = (c and -1024) == 0xd800
        protected fun isTrailSurrogate(c: Int): Boolean = (c and -1024) == 0xdc00
        private fun isSurrogate(c: Int): Boolean = (c and -2048) == 0xd800
        private fun simpleCE(ce32: Int, t: Int): Long = ((ce32 and -65536).toLong() shl 32) or ((ce32 and 0xff00).toLong() shl 16) or (t shl 8).toLong()
        private val assertionsEnabled = collationIteratorAssertionsEnabled()
    }
}
private fun ciIndex(size: Int, index: Int): Int { if (index < 0 || index >= size) throw IndexOutOfBoundsException(); return index }
private fun IntArray.ciAt(index: Int): Int = this[ciIndex(size, index)]
private fun LongArray.ciAt(index: Int): Long = this[ciIndex(size, index)]
internal fun CharSequence.ciChar(index: Int): Char {
    if (this is String || this is StringBuilder) ciIndex(length, index)
    return this[index]
}
private fun ciCodePointAt(text: CharSequence, index: Int): Int {
    val lead = text.ciChar(index).code
    if (lead in 0xd800..0xdbff && index + 1 < text.length) {
        val trail = text.ciChar(index + 1).code
        if (trail in 0xdc00..0xdfff) return ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
    }
    return lead
}
private fun ciOffsetByCodePoints(text: CharSequence, offset: Int, count: Int): Int {
    if (offset < 0 || offset > text.length) throw IndexOutOfBoundsException()
    var p = offset; var n = count
    while (n > 0) { val c = ciCodePointAt(text, p); p += UTF16.getCharCount(c); --n }
    while (n < 0) {
        val trail = text.ciChar(--p).code
        if (trail in 0xdc00..0xdfff && p > 0 && text.ciChar(p - 1).code in 0xd800..0xdbff) --p
        ++n
    }
    return p
}
internal expect fun collationIteratorAssertionsEnabled(): Boolean
internal expect fun collationIteratorClassAssertionsEnabled(type: kotlin.reflect.KClass<*>): Boolean
