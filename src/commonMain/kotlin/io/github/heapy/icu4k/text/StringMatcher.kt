// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2000-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Utility

internal class StringMatcher(private val pattern: String, private val segmentNumber: Int,
    private val data: TransliterationData) : UnicodeMatcher, UnicodeReplacer {
    private var matchStart = -1
    private var matchLimit = -1
    constructor(pattern: String, start: Int, limit: Int, segmentNumber: Int, data: TransliterationData) :
        this(transliterationSubstring(pattern, start, limit), segmentNumber, data)

    override fun matches(text: Replaceable, offset: IntArray, limit: Int, incremental: Boolean): Int {
        val cursor = intArrayOf(offset[0])
        if (limit < cursor[0]) {
            for (i in pattern.lastIndex downTo 0) {
                val keyChar = pattern[i]
                val sub = data.lookupMatcher(keyChar.code)
                if (sub == null) {
                    if (cursor[0] > limit && keyChar == text.charAt(cursor[0])) --cursor[0]
                    else return UnicodeMatcher.U_MISMATCH
                } else {
                    val match = sub.matches(text, cursor, limit, incremental)
                    if (match != UnicodeMatcher.U_MATCH) return match
                }
            }
            if (matchStart < 0) { matchStart = cursor[0] + 1; matchLimit = offset[0] + 1 }
        } else {
            for (keyChar in pattern) {
                if (incremental && cursor[0] == limit) return UnicodeMatcher.U_PARTIAL_MATCH
                val sub = data.lookupMatcher(keyChar.code)
                if (sub == null) {
                    if (cursor[0] < limit && keyChar == text.charAt(cursor[0])) ++cursor[0]
                    else return UnicodeMatcher.U_MISMATCH
                } else {
                    val match = sub.matches(text, cursor, limit, incremental)
                    if (match != UnicodeMatcher.U_MATCH) return match
                }
            }
            matchStart = offset[0]; matchLimit = cursor[0]
        }
        offset[0] = cursor[0]
        return UnicodeMatcher.U_MATCH
    }
    override fun toPattern(escapeUnprintable: Boolean): String {
        val result = StringBuilder()
        val quote = StringBuilder()
        if (segmentNumber > 0) result.append('(')
        for (c in pattern) {
            val matcher = data.lookupMatcher(c.code)
            if (matcher == null) Utility.appendToRule(result, c.code, false, escapeUnprintable, quote)
            else Utility.appendToRule(result, matcher.toPattern(escapeUnprintable), true, escapeUnprintable, quote)
        }
        if (segmentNumber > 0) result.append(')')
        Utility.appendToRule(result, -1, true, escapeUnprintable, quote)
        return result.toString()
    }
    override fun matchesIndexValue(v: Int): Boolean {
        if (pattern.isEmpty()) return true
        val c = UTF16.charAt(pattern, 0)
        return data.lookupMatcher(c)?.matchesIndexValue(v) ?: (c and 0xff == v)
    }
    override fun addMatchSetTo(toUnionTo: UnicodeSet) {
        var i = 0
        while (i < pattern.length) {
            val cp = UTF16.charAt(pattern, i)
            val matcher = data.lookupMatcher(cp)
            if (matcher == null) toUnionTo.add(cp) else matcher.addMatchSetTo(toUnionTo)
            i += UTF16.getCharCount(cp)
        }
    }
    override fun replace(text: Replaceable, start: Int, limit: Int, cursor: IntArray): Int {
        var outLen = 0
        if (matchStart >= 0 && matchStart != matchLimit) {
            text.copy(matchStart, matchLimit, limit)
            outLen = matchLimit - matchStart
        }
        text.replace(start, limit, "")
        return outLen
    }
    override fun toReplacerPattern(escapeUnprintable: Boolean): String {
        // Source appendNumber negates Int.MIN_VALUE and indexes DIGITS with -8.
        if (segmentNumber == Int.MIN_VALUE) throw IndexOutOfBoundsException("Index -8 out of bounds for length 36")
        return "$" + segmentNumber.toString()
    }
    fun resetMatch() { matchStart = -1; matchLimit = -1 }
    override fun addReplacementSetTo(toUnionTo: UnicodeSet) {}
}
