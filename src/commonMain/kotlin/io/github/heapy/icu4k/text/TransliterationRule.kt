// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2000-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Utility

/** Mutable position protocol, to be owned by the public Transliterator facade. */
internal open class TransliterationPosition(open var contextStart: Int, open var contextLimit: Int, open var start: Int, open var limit: Int) {
    constructor() : this(0,0,0,0)
    constructor(contextStart: Int,contextLimit: Int,start: Int) : this(contextStart,contextLimit,start,contextLimit)
    constructor(pos: TransliterationPosition) : this() { set(pos) }
    fun set(pos: TransliterationPosition) {contextStart=pos.contextStart;contextLimit=pos.contextLimit;start=pos.start;limit=pos.limit}
    fun validate(length: Int) {
        if(contextStart<0 || start<contextStart || limit<start || contextLimit<limit || length<contextLimit)
            throw IllegalArgumentException("Invalid Position {cs=$contextStart, s=$start, l=$limit, cl=$contextLimit}, len=$length")
    }
    override fun equals(other: Any?): Boolean = other is TransliterationPosition && contextStart==other.contextStart && contextLimit==other.contextLimit && start==other.start && limit==other.limit
    override fun hashCode(): Int {var result=1;for(value in intArrayOf(contextStart,contextLimit,start,limit))result=31*result+value;return result}
    override fun toString(): String = "[cs=$contextStart, s=$start, l=$limit, cl=$contextLimit]"
}

internal class TransliterationRule(input: String, anteContextPos: Int, postContextPos: Int,
    output: String, cursorPos: Int, cursorOffset: Int, val segments: Array<UnicodeMatcher>?,
    anchorStart: Boolean, anchorEnd: Boolean, private val data: TransliterationData) {
    private val anteContextLength: Int
    private val keyLength: Int
    private val pattern = input
    private val flags = (if (anchorStart) 1 else 0) or (if (anchorEnd) 2 else 0)
    private val anteContext: StringMatcher?
    private val key: StringMatcher?
    private val postContext: StringMatcher?
    private val output: UnicodeReplacer
    init {
        anteContextLength = if (anteContextPos < 0) 0 else {
            if (anteContextPos > input.length) throw IllegalArgumentException("Invalid ante context")
            anteContextPos
        }
        keyLength = if (postContextPos < 0) input.length - anteContextLength else {
            if (postContextPos < anteContextLength || postContextPos > input.length) throw IllegalArgumentException("Invalid post context")
            postContextPos - anteContextLength
        }
        val cursor = if (cursorPos < 0) output.length else {
            if (cursorPos > output.length) throw IllegalArgumentException("Invalid cursor position")
            cursorPos
        }
        anteContext = if (anteContextLength > 0) StringMatcher(pattern.substring(0, anteContextLength), 0, data) else null
        key = if (keyLength > 0) StringMatcher(pattern.substring(anteContextLength, anteContextLength + keyLength), 0, data) else null
        postContext = if (pattern.length - keyLength - anteContextLength > 0)
            StringMatcher(pattern.substring(anteContextLength + keyLength), 0, data) else null
        this.output = StringReplacer(output, cursor + cursorOffset, data)
    }
    fun getAnteContextLength(): Int = anteContextLength + if (flags and 1 != 0) 1 else 0
    fun getIndexValue(): Int {
        if (anteContextLength == pattern.length) return -1
        val cp = UTF16.charAt(pattern, anteContextLength)
        return if (data.lookupMatcher(cp) == null) cp and 0xff else -1
    }
    fun matchesIndexValue(v: Int): Boolean = (key ?: postContext)?.matchesIndexValue(v) ?: true
    fun masks(other: TransliterationRule): Boolean {
        val left = anteContextLength
        val left2 = other.anteContextLength
        val right = pattern.length - left
        val right2 = other.pattern.length - left2
        if (left == left2 && right == right2 && keyLength <= other.keyLength && other.pattern.regionMatches(0, pattern, 0, pattern.length)) {
            return flags == other.flags || flags and 3 == 0 || other.flags and 3 == 3
        }
        return left <= left2 && (right < right2 || (right == right2 && keyLength <= other.keyLength)) &&
            other.pattern.regionMatches(left2 - left, pattern, 0, pattern.length)
    }
    fun matchAndReplace(text: Replaceable, pos: TransliterationPosition, incremental: Boolean): Int {
        segments?.forEach { (it as StringMatcher).resetMatch() }
        val ref = IntArray(1)
        val anteLimit = posBefore(text, pos.contextStart)
        ref[0] = posBefore(text, pos.start)
        if (anteContext != null && anteContext.matches(text, ref, anteLimit, false) != UnicodeMatcher.U_MATCH) return UnicodeMatcher.U_MISMATCH
        var oText = ref[0]
        val minOText = posAfter(text, oText)
        if (flags and 1 != 0 && oText != anteLimit) return UnicodeMatcher.U_MISMATCH
        ref[0] = pos.start
        if (key != null) {
            val match = key.matches(text, ref, pos.limit, incremental)
            if (match != UnicodeMatcher.U_MATCH) return match
        }
        val keyLimit = ref[0]
        if (postContext != null) {
            if (incremental && keyLimit == pos.limit) return UnicodeMatcher.U_PARTIAL_MATCH
            val match = postContext.matches(text, ref, pos.contextLimit, incremental)
            if (match != UnicodeMatcher.U_MATCH) return match
        }
        oText = ref[0]
        if (flags and 2 != 0) {
            if (oText != pos.contextLimit) return UnicodeMatcher.U_MISMATCH
            if (incremental) return UnicodeMatcher.U_PARTIAL_MATCH
        }
        val newLength = output.replace(text, pos.start, keyLimit, ref)
        val delta = newLength - (keyLimit - pos.start)
        val newStart = ref[0]
        oText += delta
        pos.limit += delta
        pos.contextLimit += delta
        pos.start = maxOf(minOText, minOf(oText, pos.limit, newStart))
        return UnicodeMatcher.U_MATCH
    }
    fun toRule(escapeUnprintable: Boolean): String {
        val rule = StringBuilder()
        val quote = StringBuilder()
        fun literal(s: String) = Utility.appendToRule(rule, s, true, escapeUnprintable, quote)
        fun match(m: StringMatcher?) { if (m != null) literal(m.toPattern(escapeUnprintable)) }
        val braces = anteContext != null || postContext != null
        if (flags and 1 != 0) rule.append('^')
        match(anteContext)
        if (braces) literal("{")
        match(key)
        if (braces) literal("}")
        match(postContext)
        if (flags and 2 != 0) rule.append('$')
        literal(" > ")
        literal(output.toReplacerPattern(escapeUnprintable))
        literal(";")
        return rule.toString()
    }
    override fun toString(): String = "{" + toRule(true) + "}"
    fun addSourceTargetSet(filter: UnicodeSet, sourceSet: UnicodeSet, targetSet: UnicodeSet, revisiting: UnicodeSet) {
        val limit = anteContextLength + keyLength
        val tempSource = UnicodeSet()
        val temp = UnicodeSet()
        var i = anteContextLength
        while (i < limit) {
            val cp = UTF16.charAt(pattern, i)
            i += UTF16.getCharCount(cp)
            val matcher = data.lookupMatcher(cp)
            if (matcher == null) {
                if (!filter.contains(cp)) return
                tempSource.add(cp)
            } else if (matcher is UnicodeSet) {
                if (!filter.containsSome(matcher)) return
                matcher.addMatchSetTo(tempSource)
            } else {
                temp.clear()
                matcher.addMatchSetTo(temp)
                if (!filter.containsSome(temp)) return
                tempSource.addAll(temp)
            }
        }
        sourceSet.addAll(tempSource)
        output.addReplacementSetTo(targetSet)
    }
    companion object {
        fun posBefore(text: Replaceable, pos: Int): Int = if (pos > 0) pos - UTF16.getCharCount(text.char32At(pos - 1)) else pos - 1
        fun posAfter(text: Replaceable, pos: Int): Int = if (pos >= 0 && pos < text.length()) pos + UTF16.getCharCount(text.char32At(pos)) else pos + 1
    }
}
