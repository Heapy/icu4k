// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2012-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.UTF16

/** Exact ICU bitmap fast path; supplementary surrogate flags are deliberately conservative. */
internal object CollationFCD {
    private val assertionsEnabled = collationFCDAssertionsEnabled()
    fun hasLccc(c: Int): Boolean {
        if (assertionsEnabled && c > 0xffff) throw AssertionError()
        return c >= 0x300 && hasBit(CollationFCDData.lcccIndex, CollationFCDData.lcccBits, c)
    }
    fun hasTccc(c: Int): Boolean {
        if (assertionsEnabled && c > 0xffff) throw AssertionError()
        return c >= 0xc0 && hasBit(CollationFCDData.tcccIndex, CollationFCDData.tcccBits, c)
    }
    fun mayHaveLccc(codePoint: Int): Boolean {
        if (codePoint < 0x300) return false
        val c = if (codePoint > 0xffff) UTF16.getLeadSurrogate(codePoint).code else codePoint
        return hasBit(CollationFCDData.lcccIndex, CollationFCDData.lcccBits, c)
    }
    fun maybeTibetanCompositeVowel(c: Int): Boolean = (c and 0x1fff01) == 0xf01
    fun isFCD16OfTibetanCompositeVowel(fcd16: Int): Boolean = fcd16 == 0x8182 || fcd16 == 0x8184
    private fun hasBit(index: ByteArray, bits: IntArray, c: Int): Boolean {
        val block = c shr 5
        if (block < 0 || block >= index.size) throw IndexOutOfBoundsException()
        val i = index[block].toInt()
        if (i == 0) return false
        if (i < 0 || i >= bits.size) throw IndexOutOfBoundsException()
        return (bits[i] and (1 shl (c and 0x1f))) != 0
    }
}
internal expect fun collationFCDAssertionsEnabled(): Boolean
