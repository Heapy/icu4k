// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Utility

internal class Quantifier(matcher: UnicodeMatcher?, private val minCount: Int, private val maxCount: Int) : UnicodeMatcher {
    private val matcher: UnicodeMatcher
    init {
        if (matcher == null || minCount < 0 || maxCount < 0 || minCount > maxCount) throw IllegalArgumentException()
        this.matcher = matcher
    }
    override fun matches(text: Replaceable, offset: IntArray, limit: Int, incremental: Boolean): Int {
        val start = offset[0]
        var count = 0
        while (count < maxCount) {
            val pos = offset[0]
            val match = matcher.matches(text, offset, limit, incremental)
            if (match == UnicodeMatcher.U_MATCH) {
                ++count
                if (pos == offset[0]) break
            } else if (incremental && match == UnicodeMatcher.U_PARTIAL_MATCH) return UnicodeMatcher.U_PARTIAL_MATCH
            else break
        }
        if (incremental && offset[0] == limit) return UnicodeMatcher.U_PARTIAL_MATCH
        if (count >= minCount) return UnicodeMatcher.U_MATCH
        offset[0] = start
        return UnicodeMatcher.U_MISMATCH
    }
    override fun toPattern(escapeUnprintable: Boolean): String {
        val pattern = matcher.toPattern(escapeUnprintable)
        if (minCount == 0) {
            if (maxCount == 1) return "$pattern?"
            if (maxCount == MAX) return "$pattern*"
        } else if (minCount == 1 && maxCount == MAX) return "$pattern+"
        return pattern + "{" + Utility.hex(minCount.toLong(),1) + "," + (if(maxCount == MAX) "" else Utility.hex(maxCount.toLong(),1)) + "}"
    }
    override fun matchesIndexValue(v: Int): Boolean = minCount == 0 || matcher.matchesIndexValue(v)
    override fun addMatchSetTo(toUnionTo: UnicodeSet) { if (maxCount > 0) matcher.addMatchSetTo(toUnionTo) }
    companion object { const val MAX = Int.MAX_VALUE }
}
