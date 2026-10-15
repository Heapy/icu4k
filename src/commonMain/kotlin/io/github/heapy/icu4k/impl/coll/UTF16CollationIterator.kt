// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2010-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

/** UTF-16 CE cursor; canonical FCD normalization belongs to the subsequent subclass. */
internal open class UTF16CollationIterator : CollationIterator {
    protected var seq: CharSequence? = null
    protected var start = 0
    protected var pos = 0
    protected var limit = 0
    constructor(d: CollationData?) : super(d)
    constructor(d: CollationData?, numeric: Boolean, s: CharSequence?, p: Int) : super(d, numeric) {
        seq = s; start = 0; pos = p; limit = s!!.length
    }
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        other as UTF16CollationIterator
        return pos - start == other.pos - other.start
    }
    override fun hashCode(): Int { if (hashAssertionsEnabled) throw AssertionError("hashCode not designed"); return 42 }
    override fun resetToOffset(newOffset: Int) { reset(); pos = start + newOffset }
    override fun getOffset(): Int = pos - start
    open fun setText(numeric: Boolean, s: CharSequence?, p: Int) { reset(numeric); seq = s; start = 0; pos = p; limit = s!!.length }
    override fun nextCodePoint(): Int {
        if (pos == limit) return Collation.SENTINEL_CP
        val c = unit(pos++).code
        if (c in 0xd800..0xdbff && pos != limit) {
            val trail = unit(pos).code
            if (trail in 0xdc00..0xdfff) { ++pos; return ((c - 0xd800) shl 10) + trail - 0xdc00 + 0x10000 }
        }
        return c
    }
    override fun previousCodePoint(): Int {
        if (pos == start) return Collation.SENTINEL_CP
        val c = unit(--pos).code
        if (c in 0xdc00..0xdfff && pos != start) {
            val lead = unit(pos - 1).code
            if (lead in 0xd800..0xdbff) { --pos; return ((lead - 0xd800) shl 10) + c - 0xdc00 + 0x10000 }
        }
        return c
    }
    override fun handleNextCE32(): Long {
        if (pos == limit) return NO_CP_AND_CE32
        val c = unit(pos++)
        return makeCodePointAndCE32Pair(c.code, trie!!.getFromU16SingleLead(c))
    }
    override fun handleGetTrailSurrogate(): Char {
        if (pos == limit) return '\u0000'
        val trail = unit(pos)
        if (trail.code in 0xdc00..0xdfff) ++pos
        return trail
    }
    override fun forwardNumCodePoints(num: Int) {
        var n = num
        while (n > 0 && pos != limit) {
            val c = unit(pos++).code; --n
            if (c in 0xd800..0xdbff && pos != limit && unit(pos).code in 0xdc00..0xdfff) ++pos
        }
    }
    override fun backwardNumCodePoints(num: Int) {
        var n = num
        while (n > 0 && pos != start) {
            val c = unit(--pos).code; --n
            if (c in 0xdc00..0xdfff && pos != start && unit(pos - 1).code in 0xd800..0xdbff) --pos
        }
    }
    private fun unit(index: Int): Char = seq!!.ciChar(index)
    private companion object { private val hashAssertionsEnabled = utf16CollationIteratorAssertionsEnabled() }
}
internal expect fun utf16CollationIteratorAssertionsEnabled(): Boolean
