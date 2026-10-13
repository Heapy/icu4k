// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Selects code points for matching and text transformations. */
abstract class UnicodeFilter protected constructor() : UnicodeMatcher {
    abstract fun contains(c: Int): Boolean
    override fun matches(text: Replaceable, offset: IntArray, limit: Int, incremental: Boolean): Int {
        if (offset[0] < limit) {
            val c = text.char32At(offset[0])
            if (contains(c)) { offset[0] += UTF16.getCharCount(c); return UnicodeMatcher.U_MATCH }
        }
        if (offset[0] > limit && contains(text.char32At(offset[0]))) {
            --offset[0]
            if (offset[0] >= 0) offset[0] -= UTF16.getCharCount(text.char32At(offset[0])) - 1
            return UnicodeMatcher.U_MATCH
        }
        return if (incremental && offset[0] == limit) UnicodeMatcher.U_PARTIAL_MATCH else UnicodeMatcher.U_MISMATCH
    }
}
