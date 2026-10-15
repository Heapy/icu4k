// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2012-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.Normalizer2Impl
import io.github.heapy.icu4k.text.UCharacterIterator
import io.github.heapy.icu4k.text.UTF16

/** Incremental FCD checking over a caller-owned iterator, retaining its raw position. */
internal class FCDIterCollationIterator(data: CollationData?, numeric: Boolean, ui: UCharacterIterator?, startIndex: Int) : IterCollationIterator(data, numeric, ui) {
    private enum class State { ITER_CHECK_FWD, ITER_CHECK_BWD, ITER_IN_FCD_SEGMENT, IN_NORM_ITER_AT_LIMIT, IN_NORM_ITER_AT_START }
    private var state = State.ITER_CHECK_FWD
    private var start = startIndex
    private var pos = 0
    private var limit = 0
    private val nfcImpl: Normalizer2Impl? = data!!.nfcImpl
    private var s: StringBuilder? = null
    private var normalized: StringBuilder? = null
    override fun resetToOffset(newOffset: Int) { super.resetToOffset(newOffset); start = newOffset; state = State.ITER_CHECK_FWD }
    override fun getOffset(): Int = when {
        state <= State.ITER_CHECK_BWD -> iter!!.getIndex()
        state == State.ITER_IN_FCD_SEGMENT -> pos
        pos == 0 -> start
        else -> limit
    }
    override fun nextCodePoint(): Int {
        while (true) {
            if (state == State.ITER_CHECK_FWD) {
                val c = iter!!.next()
                if (c < 0) return c
                if (CollationFCD.hasTccc(c) && (CollationFCD.maybeTibetanCompositeVowel(c) || CollationFCD.hasLccc(iter!!.current()))) {
                    iter!!.previous(); if (!nextSegment()) return Collation.SENTINEL_CP; continue
                }
                if (UTF16.isLeadSurrogate(c)) {
                    val trail = iter!!.next()
                    if (UTF16.isTrailSurrogate(trail)) return toCodePoint(c, trail)
                    else if (trail >= 0) iter!!.previous()
                }
                return c
            } else if (state == State.ITER_IN_FCD_SEGMENT && pos != limit) {
                val c = iter!!.nextCodePoint(); pos += UTF16.getCharCount(c); checkAssertion { c >= 0 }; return c
            } else if (state >= State.IN_NORM_ITER_AT_LIMIT && pos != normalized!!.length) {
                val c = normalizedCodePointAt(pos); pos += UTF16.getCharCount(c); return c
            } else switchToForward()
        }
    }
    override fun previousCodePoint(): Int {
        while (true) {
            if (state == State.ITER_CHECK_BWD) {
                val c = iter!!.previous()
                if (c < 0) { pos = 0; start = 0; state = State.ITER_IN_FCD_SEGMENT; return Collation.SENTINEL_CP }
                if (CollationFCD.hasLccc(c)) {
                    var prev = Collation.SENTINEL_CP
                    val needsSegment = if (CollationFCD.maybeTibetanCompositeVowel(c)) true else { prev = iter!!.previous(); CollationFCD.hasTccc(prev) }
                    if (needsSegment) {
                        iter!!.next(); if (prev >= 0) iter!!.next()
                        if (!previousSegment()) return Collation.SENTINEL_CP
                        continue
                    }
                    if (UTF16.isTrailSurrogate(c)) {
                        if (prev < 0) prev = iter!!.previous()
                        if (UTF16.isLeadSurrogate(prev)) return toCodePoint(prev, c)
                    }
                    if (prev >= 0) iter!!.next()
                }
                return c
            } else if (state == State.ITER_IN_FCD_SEGMENT && pos != start) {
                val c = iter!!.previousCodePoint(); pos -= UTF16.getCharCount(c); checkAssertion { c >= 0 }; return c
            } else if (state >= State.IN_NORM_ITER_AT_LIMIT && pos != 0) {
                val c = normalizedCodePointBefore(pos); pos -= UTF16.getCharCount(c); return c
            } else switchToBackward()
        }
    }
    override fun handleNextCE32(): Long {
        var c: Int
        while (true) {
            if (state == State.ITER_CHECK_FWD) {
                c = iter!!.next()
                if (c < 0) return NO_CP_AND_CE32
                if (CollationFCD.hasTccc(c) && (CollationFCD.maybeTibetanCompositeVowel(c) || CollationFCD.hasLccc(iter!!.current()))) {
                    iter!!.previous(); if (!nextSegment()) return Collation.FALLBACK_CE32.toLong(); continue
                }
                break
            } else if (state == State.ITER_IN_FCD_SEGMENT && pos != limit) {
                c = iter!!.next(); ++pos; checkAssertion { c >= 0 }; break
            } else if (state >= State.IN_NORM_ITER_AT_LIMIT && pos != normalized!!.length) {
                c = normalized!!.ciChar(pos++).code; break
            } else switchToForward()
        }
        return makeCodePointAndCE32Pair(c, trie!!.getFromU16SingleLead(c.toChar()))
    }
    override fun handleGetTrailSurrogate(): Char {
        if (state <= State.ITER_IN_FCD_SEGMENT) {
            val trail = iter!!.next()
            if (UTF16.isTrailSurrogate(trail)) { if (state == State.ITER_IN_FCD_SEGMENT) ++pos }
            else if (trail >= 0) iter!!.previous()
            return trail.toChar()
        } else {
            checkAssertion { pos < normalized!!.length }
            val trail = normalized!!.ciChar(pos)
            if (trail.code in 0xdc00..0xdfff) ++pos
            return trail
        }
    }
    override fun forwardNumCodePoints(num: Int) { var n = num; while (n > 0 && nextCodePoint() >= 0) --n }
    override fun backwardNumCodePoints(num: Int) { var n = num; while (n > 0 && previousCodePoint() >= 0) --n }
    private fun switchToForward() {
        checkAssertion { state == State.ITER_CHECK_BWD || (state == State.ITER_IN_FCD_SEGMENT && pos == limit) || (state >= State.IN_NORM_ITER_AT_LIMIT && pos == normalized!!.length) }
        if (state == State.ITER_CHECK_BWD) {
            pos = iter!!.getIndex(); start = pos
            state = if (pos == limit) State.ITER_CHECK_FWD else State.ITER_IN_FCD_SEGMENT
        } else {
            if (state != State.ITER_IN_FCD_SEGMENT) {
                if (state == State.IN_NORM_ITER_AT_START) iter!!.moveIndex(limit - start)
                start = limit
            }
            state = State.ITER_CHECK_FWD
        }
    }
    private fun nextSegment(): Boolean {
        checkAssertion { state == State.ITER_CHECK_FWD }
        pos = iter!!.getIndex()
        if (s == null) s = StringBuilder() else s!!.setLength(0)
        var prevCC = 0
        while (true) {
            var c = iter!!.nextCodePoint()
            if (c < 0) break
            val fcd16 = nfcImpl!!.getFCD16(c); val leadCC = fcd16 shr 8
            if (leadCC == 0 && s!!.length != 0) { iter!!.previousCodePoint(); break }
            appendCodePoint(c)
            if (leadCC != 0 && (prevCC > leadCC || CollationFCD.isFCD16OfTibetanCompositeVowel(fcd16))) {
                while (true) {
                    c = iter!!.nextCodePoint(); if (c < 0) break
                    if (nfcImpl.getFCD16(c) <= 0xff) { iter!!.previousCodePoint(); break }
                    appendCodePoint(c)
                }
                normalize(s!!); start = pos; limit = pos + s!!.length; state = State.IN_NORM_ITER_AT_LIMIT; pos = 0; return true
            }
            prevCC = fcd16 and 0xff
            if (prevCC == 0) break
        }
        limit = pos + s!!.length; checkAssertion { pos != limit }
        iter!!.moveIndex(-s!!.length); state = State.ITER_IN_FCD_SEGMENT; return true
    }
    private fun switchToBackward() {
        checkAssertion { state == State.ITER_CHECK_FWD || (state == State.ITER_IN_FCD_SEGMENT && pos == start) || (state >= State.IN_NORM_ITER_AT_LIMIT && pos == 0) }
        if (state == State.ITER_CHECK_FWD) {
            pos = iter!!.getIndex(); limit = pos
            state = if (pos == start) State.ITER_CHECK_BWD else State.ITER_IN_FCD_SEGMENT
        } else {
            if (state != State.ITER_IN_FCD_SEGMENT) {
                if (state == State.IN_NORM_ITER_AT_LIMIT) iter!!.moveIndex(start - limit)
                limit = start
            }
            state = State.ITER_CHECK_BWD
        }
    }
    private fun previousSegment(): Boolean {
        checkAssertion { state == State.ITER_CHECK_BWD }
        pos = iter!!.getIndex()
        if (s == null) s = StringBuilder() else s!!.setLength(0)
        var nextCC = 0
        while (true) {
            var c = iter!!.previousCodePoint(); if (c < 0) break
            var fcd16 = nfcImpl!!.getFCD16(c); val trailCC = fcd16 and 0xff
            if (trailCC == 0 && s!!.length != 0) { iter!!.nextCodePoint(); break }
            appendCodePoint(c)
            if (trailCC != 0 && ((nextCC != 0 && trailCC > nextCC) || CollationFCD.isFCD16OfTibetanCompositeVowel(fcd16))) {
                while (fcd16 > 0xff) {
                    c = iter!!.previousCodePoint(); if (c < 0) break
                    fcd16 = nfcImpl.getFCD16(c)
                    if (fcd16 == 0) { iter!!.nextCodePoint(); break }
                    appendCodePoint(c)
                }
                reverseCollected(); normalize(s!!); limit = pos; start = pos - s!!.length; state = State.IN_NORM_ITER_AT_START; pos = normalized!!.length; return true
            }
            nextCC = fcd16 shr 8
            if (nextCC == 0) break
        }
        start = pos - s!!.length; checkAssertion { pos != start }
        iter!!.moveIndex(s!!.length); state = State.ITER_IN_FCD_SEGMENT; return true
    }
    private fun normalize(text: CharSequence) {
        if (normalized == null) normalized = StringBuilder()
        nfcImpl!!.decompose(text, normalized!!)
    }
    private fun appendCodePoint(c: Int) {
        // StringBuilder.appendCodePoint accepts surrogate code points and validates only the range.
        if (c < 0 || c > 0x10ffff) throw IllegalArgumentException("Not a valid Unicode code point: 0x${c.toUInt().toString(16).uppercase()}")
        if (c < 0x10000) s!!.append(c.toChar()) else { s!!.append(UTF16.getLeadSurrogate(c)); s!!.append(UTF16.getTrailSurrogate(c)) }
    }
    private fun reverseCollected() {
        val b = s!!
        var left = 0; var right = b.length - 1
        while (left < right) { val c = b[left]; b[left++] = b[right]; b[right--] = c }
        var i = 0
        while (i < b.length - 1) {
            if (b[i].code in 0xdc00..0xdfff && b[i + 1].code in 0xd800..0xdbff) { val c = b[i]; b[i] = b[i + 1]; b[i + 1] = c; ++i }
            ++i
        }
    }
    private fun normalizedCodePointAt(index: Int): Int {
        val c = normalized!!.ciChar(index).code
        if (c in 0xd800..0xdbff && index + 1 < normalized!!.length) {
            val trail = normalized!!.ciChar(index + 1).code
            if (trail in 0xdc00..0xdfff) return toCodePoint(c, trail)
        }
        return c
    }
    private fun normalizedCodePointBefore(index: Int): Int {
        val c = normalized!!.ciChar(index - 1).code
        if (c in 0xdc00..0xdfff && index > 1) {
            val lead = normalized!!.ciChar(index - 2).code
            if (lead in 0xd800..0xdbff) return toCodePoint(lead, c)
        }
        return c
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
    private companion object {
        private val assertionsEnabled = collationIteratorClassAssertionsEnabled(FCDIterCollationIterator::class)
        private fun toCodePoint(lead: Int, trail: Int): Int = ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
    }
}
