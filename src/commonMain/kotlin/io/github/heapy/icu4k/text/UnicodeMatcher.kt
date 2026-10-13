// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Protocol for matching a range of a Replaceable, in either direction. */
interface UnicodeMatcher {
    /** Updates offset[0] only on U_MATCH. Partial matches require incremental input. */
    fun matches(text: Replaceable, offset: IntArray, limit: Int, incremental: Boolean): Int
    fun toPattern(escapeUnprintable: Boolean): String
    fun matchesIndexValue(v: Int): Boolean
    fun addMatchSetTo(toUnionTo: UnicodeSet)
    companion object {
        const val U_MISMATCH = 0
        const val U_PARTIAL_MATCH = 1
        const val U_MATCH = 2
        const val ETHER: Char = '\uFFFF'
    }
}
