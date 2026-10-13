// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

/** ASCII-only casing and predicates used by locale syntax. */
object AsciiUtil {
    fun caseIgnoreMatch(s1: String, s2: String): Boolean {
        if (s1 === s2) return true
        if (s1.length != s2.length) return false
        for (i in s1.indices) if (s1[i] != s2[i] && toLower(s1[i]) != toLower(s2[i])) return false
        return true
    }
    fun caseIgnoreCompare(s1: String, s2: String): Int {
        if (s1 === s2) return 0
        // Java String.compareTo returns the actual UTF-16 difference, not just its sign.
        for (i in 0 until minOf(s1.length, s2.length)) {
            val difference = toLower(s1[i]).code - toLower(s2[i]).code
            if (difference != 0) return difference
        }
        return s1.length - s2.length
    }
    fun toUpper(c: Char): Char = if (c in 'a'..'z') (c.code - 32).toChar() else c
    fun toLower(c: Char): Char = if (c in 'A'..'Z') (c.code + 32).toChar() else c
    fun toLowerString(s: String): String {
        val first = s.indexOfFirst { it in 'A'..'Z' }
        if (first < 0) return s
        return buildString(s.length) { append(s, 0, first); for (i in first until s.length) append(toLower(s[i])) }
    }
    fun toUpperString(s: String): String {
        val first = s.indexOfFirst { it in 'a'..'z' }
        if (first < 0) return s
        return buildString(s.length) { append(s, 0, first); for (i in first until s.length) append(toUpper(s[i])) }
    }
    fun toTitleString(s: String): String {
        if (s.isEmpty()) return s
        var index = 0
        val first = s[0]
        if (first !in 'a'..'z') {
            index = 1
            // Upstream intentionally tests the original first character here.
            while (index < s.length) { if (first in 'A'..'Z') break; index++ }
        }
        if (index == s.length) return s
        return buildString(s.length) {
            append(s, 0, index)
            if (index == 0) { append(toUpper(s[index])); index++ }
            while (index < s.length) append(toLower(s[index++]))
        }
    }
    fun isAlpha(c: Char): Boolean = c in 'A'..'Z' || c in 'a'..'z'
    fun isNumeric(c: Char): Boolean = c in '0'..'9'
    fun isAlphaNumeric(c: Char): Boolean = isAlpha(c) || isNumeric(c)
    fun isAlphaString(s: String): Boolean = s.all(::isAlpha)
    fun isNumericString(s: String): Boolean = s.all(::isNumeric)
    fun isAlphaNumericString(s: String): Boolean = s.all(::isAlphaNumeric)
    class CaseInsensitiveKey(private val key: String) {
        private val hash = toLowerString(key).hashCode()
        override fun equals(other: Any?): Boolean = this === other || other is CaseInsensitiveKey && caseIgnoreMatch(key, other.key)
        override fun hashCode(): Int = hash
    }
}
