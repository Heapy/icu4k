// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2012-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.text.UCharacterIterator

/** CE cursor over a caller-owned UTF-16 iterator, for text which already passes FCD. */
internal open class IterCollationIterator(d: CollationData?, numeric: Boolean, protected var iter: UCharacterIterator?) : CollationIterator(d, numeric) {
    override fun resetToOffset(newOffset: Int) { reset(); iter!!.setIndex(newOffset) }
    override fun getOffset(): Int = iter!!.getIndex()
    override fun nextCodePoint(): Int = iter!!.nextCodePoint()
    override fun previousCodePoint(): Int = iter!!.previousCodePoint()
    override fun handleNextCE32(): Long {
        val c = iter!!.next()
        if (c < 0) return NO_CP_AND_CE32
        return makeCodePointAndCE32Pair(c, trie!!.getFromU16SingleLead(c.toChar()))
    }
    override fun handleGetTrailSurrogate(): Char {
        val trail = iter!!.next()
        if (!UTF16.isTrailSurrogate(trail) && trail >= 0) iter!!.previous()
        return trail.toChar()
    }
    override fun forwardNumCodePoints(num: Int) { iter!!.moveCodePointIndex(num) }
    override fun backwardNumCodePoints(num: Int) { iter!!.moveCodePointIndex(-num) }
}
