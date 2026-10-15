// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2010-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.Normalizer2Impl
import io.github.heapy.icu4k.text.UTF16

/** Checks raw UTF-16 incrementally and decomposes only segments that fail FCD. */
internal class FCDUTF16CollationIterator : UTF16CollationIterator {
    private var rawSeq: CharSequence? = null
    private var segmentStart = 0
    private var segmentLimit = 0
    private var rawLimit = 0
    private val nfcImpl: Normalizer2Impl?
    private var normalized: StringBuilder? = null
    // Positive/negative checks raw text forward/backward; zero consumes a bounded segment.
    private var checkDir = 0

    constructor(d: CollationData?) : super(d) { nfcImpl = d!!.nfcImpl }
    constructor(d: CollationData?, numeric: Boolean, s: CharSequence?, p: Int) : super(d, numeric, s, p) {
        rawSeq = s; segmentStart = p; rawLimit = s!!.length; nfcImpl = d!!.nfcImpl; checkDir = 1
    }
    override fun equals(other: Any?): Boolean {
        // Preserve ICU4J's virtual call through its base-typed receiver (including recursion).
        if (other !is CollationIterator || !(this as CollationIterator).equals(other) || other !is FCDUTF16CollationIterator) return false
        if (checkDir != other.checkDir) return false
        if (checkDir == 0 && (seq === rawSeq) != (other.seq === other.rawSeq)) return false
        return if (checkDir != 0 || seq === rawSeq) pos == other.pos
        else segmentStart == other.segmentStart && pos - start == other.pos - other.start
    }
    override fun hashCode(): Int { if (assertionsEnabled) throw AssertionError("hashCode not designed"); return 42 }
    override fun resetToOffset(newOffset: Int) {
        reset(); seq = rawSeq; pos = newOffset; segmentStart = pos; start = pos; limit = rawLimit; checkDir = 1
    }
    override fun getOffset(): Int = if (checkDir != 0 || seq === rawSeq) pos else if (pos == start) segmentStart else segmentLimit
    override fun setText(numeric: Boolean, s: CharSequence?, p: Int) {
        super.setText(numeric, s, p)
        rawSeq = s; segmentStart = p; val length = s!!.length; limit = length; rawLimit = length; checkDir = 1
    }
    override fun nextCodePoint(): Int {
        val c = nextUnit()
        if (c < 0) return Collation.SENTINEL_CP
        if (c in 0xd800..0xdbff && pos != limit) {
            val trail = unit(pos)
            if (trail in 0xdc00..0xdfff) { ++pos; return ((c - 0xd800) shl 10) + trail - 0xdc00 + 0x10000 }
        }
        return c
    }
    override fun previousCodePoint(): Int {
        var c: Int
        while (true) {
            if (checkDir < 0) {
                if (pos == start) return Collation.SENTINEL_CP
                c = unit(--pos)
                if (CollationFCD.hasLccc(c) && (CollationFCD.maybeTibetanCompositeVowel(c) || (pos != start && CollationFCD.hasTccc(unit(pos - 1))))) {
                    ++pos; previousSegment(); c = unit(--pos)
                }
                break
            } else if (checkDir == 0 && pos != start) { c = unit(--pos); break }
            else switchToBackward()
        }
        if (c in 0xdc00..0xdfff && pos != start) {
            val lead = unit(pos - 1)
            if (lead in 0xd800..0xdbff) { --pos; return ((lead - 0xd800) shl 10) + c - 0xdc00 + 0x10000 }
        }
        return c
    }
    override fun handleNextCE32(): Long {
        val c = nextUnit()
        if (c < 0) return NO_CP_AND_CE32
        return makeCodePointAndCE32Pair(c, trie!!.getFromU16SingleLead(c.toChar()))
    }
    // Shared by the two source forward loops; does not combine surrogate pairs.
    private fun nextUnit(): Int {
        while (true) {
            if (checkDir > 0) {
                if (pos == limit) return Collation.SENTINEL_CP
                var c = unit(pos++)
                if (CollationFCD.hasTccc(c) && (CollationFCD.maybeTibetanCompositeVowel(c) || (pos != limit && CollationFCD.hasLccc(unit(pos))))) {
                    --pos; nextSegment(); c = unit(pos++)
                }
                return c
            } else if (checkDir == 0 && pos != limit) return unit(pos++)
            else switchToForward()
        }
    }
    override fun forwardNumCodePoints(num: Int) { var n = num; while (n > 0 && nextCodePoint() >= 0) --n }
    override fun backwardNumCodePoints(num: Int) { var n = num; while (n > 0 && previousCodePoint() >= 0) --n }
    private fun switchToForward() {
        checkAssertion { (checkDir < 0 && seq === rawSeq) || (checkDir == 0 && pos == limit) }
        if (checkDir < 0) {
            segmentStart = pos; start = pos
            if (pos == segmentLimit) { limit = rawLimit; checkDir = 1 } else checkDir = 0
        } else {
            if (seq !== rawSeq) { seq = rawSeq; segmentStart = segmentLimit; start = segmentLimit; pos = segmentLimit }
            limit = rawLimit; checkDir = 1
        }
    }
    private fun nextSegment() {
        checkAssertion { checkDir > 0 && seq === rawSeq && pos != limit }
        var p = pos; var prevCC = 0
        while (true) {
            var q = p
            var c = codePointAt(p); p += UTF16.getCharCount(c)
            val fcd16 = nfcImpl!!.getFCD16(c); val leadCC = fcd16 shr 8
            if (leadCC == 0 && q != pos) { segmentLimit = q; limit = q; break }
            if (leadCC != 0 && (prevCC > leadCC || CollationFCD.isFCD16OfTibetanCompositeVowel(fcd16))) {
                do {
                    q = p
                    if (p == rawLimit) break
                    c = codePointAt(p); p += UTF16.getCharCount(c)
                } while (nfcImpl.getFCD16(c) > 0xff)
                normalize(pos, q); pos = start; break
            }
            prevCC = fcd16 and 0xff
            if (p == rawLimit || prevCC == 0) { segmentLimit = p; limit = p; break }
        }
        checkAssertion { pos != limit }; checkDir = 0
    }
    private fun switchToBackward() {
        checkAssertion { (checkDir > 0 && seq === rawSeq) || (checkDir == 0 && pos == start) }
        if (checkDir > 0) {
            segmentLimit = pos; limit = pos
            if (pos == segmentStart) { start = 0; checkDir = -1 } else checkDir = 0
        } else {
            if (seq !== rawSeq) { seq = rawSeq; segmentLimit = segmentStart; limit = segmentStart; pos = segmentStart }
            start = 0; checkDir = -1
        }
    }
    private fun previousSegment() {
        checkAssertion { checkDir < 0 && seq === rawSeq && pos != start }
        var p = pos; var nextCC = 0
        while (true) {
            var q = p
            var c = codePointBefore(p); p -= UTF16.getCharCount(c)
            var fcd16 = nfcImpl!!.getFCD16(c); val trailCC = fcd16 and 0xff
            if (trailCC == 0 && q != pos) { segmentStart = q; start = q; break }
            if (trailCC != 0 && ((nextCC != 0 && trailCC > nextCC) || CollationFCD.isFCD16OfTibetanCompositeVowel(fcd16))) {
                do {
                    q = p
                    if (fcd16 <= 0xff || p == 0) break
                    c = codePointBefore(p); p -= UTF16.getCharCount(c)
                    fcd16 = nfcImpl.getFCD16(c)
                } while (fcd16 != 0)
                normalize(q, pos); pos = limit; break
            }
            nextCC = fcd16 shr 8
            if (p == 0 || nextCC == 0) { segmentStart = p; start = p; break }
        }
        checkAssertion { pos != start }; checkDir = 0
    }
    private fun normalize(from: Int, to: Int) {
        if (normalized == null) normalized = StringBuilder()
        nfcImpl!!.decompose(rawSeq!!, from, to, normalized!!, to - from)
        segmentStart = from; segmentLimit = to; seq = normalized; start = 0; limit = normalized!!.length
    }
    private fun unit(index: Int): Int = seq!!.ciChar(index).code
    private fun codePointAt(index: Int): Int {
        val lead = unit(index)
        if (lead in 0xd800..0xdbff && index + 1 < seq!!.length) {
            val trail = unit(index + 1)
            if (trail in 0xdc00..0xdfff) return ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
        }
        return lead
    }
    private fun codePointBefore(index: Int): Int {
        val trail = unit(index - 1)
        if (trail in 0xdc00..0xdfff && index - 1 > 0) {
            val lead = unit(index - 2)
            if (lead in 0xd800..0xdbff) return ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
        }
        return trail
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
    private companion object { private val assertionsEnabled = collationIteratorClassAssertionsEnabled(FCDUTF16CollationIterator::class) }
}
