// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.lang

/** ICU character-sequence utilities. Comparisons use UTF-16 ordering. */
class CharSequences private constructor() {
    fun codePointLength(s: CharSequence): Int {
        var count = 0
        var i = 0
        while (i < s.length) { i += if (codePointAt(s, i) > 0xffff) 2 else 1; ++count }
        return count
    }
    companion object {
        fun matchAfter(a: CharSequence, b: CharSequence, aIndex: Int, bIndex: Int): Int {
            var i = aIndex; var j = bIndex
            while (i < a.length && j < b.length && a[i] == b[j]) { ++i; ++j }
            var result = i - aIndex
            if (result != 0 && !onCharacterBoundary(a, i) && !onCharacterBoundary(b, j)) --result
            return result
        }
        fun equals(codepoint: Int, other: CharSequence?): Boolean = when (other?.length) {
            1 -> codepoint == other[0].code
            2 -> codepoint > 0xffff && codepoint == codePointAt(other, 0)
            else -> false
        }
        fun equals(other: CharSequence?, codepoint: Int): Boolean = equals(codepoint, other)
        fun compare(string: CharSequence, codePoint: Int): Int {
            require(codePoint in 0..0x10ffff)
            val length = string.length
            if (length == 0) return -1
            val offset = codePoint - 0x10000
            if (offset < 0) {
                val result = string[0].code - codePoint
                return if (result != 0) result else length - 1
            }
            val result = string[0].code - ((offset ushr 10) + 0xd800)
            if (result != 0) return result
            if (length > 1) {
                val trailResult = string[1].code - ((offset and 1023) + 0xdc00)
                if (trailResult != 0) return trailResult
            }
            return length - 2
        }
        fun compare(codepoint: Int, a: CharSequence): Int {
            val result = compare(a, codepoint)
            return if (result > 0) -1 else if (result < 0) 1 else 0
        }
        fun getSingleCodePoint(s: CharSequence): Int {
            val length = s.length
            if (length < 1 || length > 2) return Int.MAX_VALUE
            val result = codePointAt(s, 0)
            return if ((result < 0x10000) == (length == 1)) result else Int.MAX_VALUE
        }
        fun <T> equals(a: T?, b: T?): Boolean = a == b
        fun compare(a: CharSequence, b: CharSequence): Int {
            for (i in 0 until minOf(a.length, b.length)) {
                val diff = a[i].code - b[i].code
                if (diff != 0) return diff
            }
            return a.length - b.length
        }
        fun equalsChars(a: CharSequence, b: CharSequence): Boolean = a.length == b.length && compare(a, b) == 0
        fun onCharacterBoundary(s: CharSequence, i: Int): Boolean = i <= 0 || i >= s.length || s[i - 1].code !in 0xd800..0xdbff || s[i].code !in 0xdc00..0xdfff
        fun indexOf(s: CharSequence, codePoint: Int): Int {
            var i = 0
            while (i < s.length) {
                val cp = codePointAt(s, i)
                if (cp == codePoint) return i
                i += if (cp > 0xffff) 2 else 1
            }
            return -1
        }
        fun codePoints(s: CharSequence): IntArray {
            val result = IntArray(s.length)
            var j = 0
            for (i in s.indices) {
                val cp = s[i].code
                if (cp in 0xdc00..0xdfff && i != 0) {
                    val last = result[j - 1].toChar().code
                    if (last in 0xd800..0xdbff) {
                        result[j - 1] = ((last - 0xd800) shl 10) + cp - 0xdc00 + 0x10000
                        continue
                    }
                }
                result[j++] = cp
            }
            return if (j == result.size) result else result.copyOf(j)
        }
        private fun codePointAt(s: CharSequence, i: Int): Int {
            val lead = s[i].code
            return if (lead in 0xd800..0xdbff && i + 1 < s.length && s[i + 1].code in 0xdc00..0xdfff)
                ((lead - 0xd800) shl 10) + s[i + 1].code - 0xdc00 + 0x10000 else lead
        }
    }
}
