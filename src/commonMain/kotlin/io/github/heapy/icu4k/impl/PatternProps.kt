// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Immutable Unicode Pattern_Syntax and Pattern_White_Space properties. */
internal object PatternProps {
    fun isSyntax(c: Int): Boolean = when {
        c < 0 -> false
        c <= 0xff -> latin1[c] == 3
        c < 0x2010 -> false
        c <= 0x3030 -> (syntax2000[index2000[(c - 0x2000) shr 5]] shr (c and 31)) and 1 != 0
        c in 0xfd3e..0xfe46 -> c <= 0xfd3f || c >= 0xfe45
        else -> false
    }
    fun isSyntaxOrWhiteSpace(c: Int): Boolean = when {
        c < 0 -> false
        c <= 0xff -> latin1[c] != 0
        c < 0x200e -> false
        c <= 0x3030 -> (syntaxOrWhiteSpace2000[index2000[(c - 0x2000) shr 5]] shr (c and 31)) and 1 != 0
        c in 0xfd3e..0xfe46 -> c <= 0xfd3f || c >= 0xfe45
        else -> false
    }
    fun isWhiteSpace(c: Int): Boolean = when {
        c < 0 -> false
        c <= 0xff -> latin1[c] == 5
        c in 0x200e..0x2029 -> c <= 0x200f || c >= 0x2028
        else -> false
    }
    fun skipWhiteSpace(s: CharSequence, i: Int): Int {
        var index = i
        while (index < s.length && isWhiteSpace(s[index].code)) ++index
        return index
    }
    fun trimWhiteSpace(s: String): String {
        if (s.isEmpty() || (!isWhiteSpace(s[0].code) && !isWhiteSpace(s[s.lastIndex].code))) return s
        var start = 0
        var limit = s.length
        while (start < limit && isWhiteSpace(s[start].code)) ++start
        if (start < limit) while (isWhiteSpace(s[limit - 1].code)) --limit
        return s.substring(start, limit)
    }
    fun trimSpaceChar(s: String): String {
        if (s.isEmpty() || (!isSpaceChar(s[0].code) && !isSpaceChar(s[s.lastIndex].code))) return s
        var start = 0
        var limit = s.length
        while (start < limit && isSpaceChar(s[start].code)) ++start
        // Preserve upstream's Pattern_White_Space predicate for the trailing end.
        if (start < limit) while (true) {
            if (limit == 0) throw IndexOutOfBoundsException()
            if (!isWhiteSpace(s[limit - 1].code)) break
            --limit
        }
        return s.substring(start, limit)
    }
    private fun isSpaceChar(c: Int): Boolean = c == 0x20 || c == 0xa0 || c == 0x1680 || c in 0x2000..0x200a || c in 0x2028..0x2029 || c == 0x202f || c == 0x205f || c == 0x3000
    fun isIdentifier(s: CharSequence): Boolean = isIdentifier(s, 0, s.length)
    fun isIdentifier(s: CharSequence, start: Int, limit: Int): Boolean {
        if (start >= limit) return false
        var index = start
        do { if (isSyntaxOrWhiteSpace(s[index++].code)) return false } while (index < limit)
        return true
    }
    fun skipIdentifier(s: CharSequence, i: Int): Int {
        var index = i
        while (index < s.length && !isSyntaxOrWhiteSpace(s[index].code)) ++index
        return index
    }
    private val latin1 = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 5, 5, 5, 5, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        5, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3,
        3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 0,
        3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 0,
        0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 3, 3, 3, 3, 3, 3, 3, 0, 3, 0, 3, 3, 0, 3, 0,
        3, 3, 0, 0, 0, 0, 3, 0, 0, 0, 0, 3, 0, 0, 0, 3,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val index2000 = intArrayOf(
        2, 3, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 6, 7, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        8, 9,
    )
    private val syntax2000 = intArrayOf(
        0, -1, -65536, 2147418367, 2146435070, -65536, 4194303, -1048576, -242, 65537,
    )
    private val syntaxOrWhiteSpace2000 = intArrayOf(
        0, -1, -16384, 2147419135, 2146435070, -65536, 4194303, -1048576, -242, 65537,
    )
}
