// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2009, Google, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.text.UnicodeSet

/** ICU pattern/rule tokenizer; Common Kotlin uses StringBuilder for the source StringBuffer. */
open class PatternTokenizer {
    private var ignorableCharacters = UnicodeSet()
    private var syntaxCharacters = UnicodeSet()
    private var extraQuotingCharacters = UnicodeSet()
    private var escapeCharacters = UnicodeSet()
    private var usingSlash = false
    private var usingQuote = false
    private var needingQuoteCharacters: UnicodeSet? = null
    private var start = 0
    private var limit = 0
    private var pattern: String? = null
    open fun getIgnorableCharacters(): UnicodeSet = ignorableCharacters.clone()
    open fun setIgnorableCharacters(value: UnicodeSet?): PatternTokenizer { ignorableCharacters = value!!.clone(); needingQuoteCharacters = null; return this }
    open fun getSyntaxCharacters(): UnicodeSet = syntaxCharacters.clone()
    open fun setSyntaxCharacters(value: UnicodeSet?): PatternTokenizer { syntaxCharacters = value!!.clone(); needingQuoteCharacters = null; return this }
    open fun getExtraQuotingCharacters(): UnicodeSet = extraQuotingCharacters.clone()
    open fun setExtraQuotingCharacters(value: UnicodeSet?): PatternTokenizer { extraQuotingCharacters = value!!.clone(); needingQuoteCharacters = null; return this }
    open fun getEscapeCharacters(): UnicodeSet = escapeCharacters.clone()
    open fun setEscapeCharacters(value: UnicodeSet?): PatternTokenizer { escapeCharacters = value!!.clone(); return this }
    open fun isUsingQuote(): Boolean = usingQuote
    open fun setUsingQuote(value: Boolean): PatternTokenizer { usingQuote = value; needingQuoteCharacters = null; return this }
    open fun isUsingSlash(): Boolean = usingSlash
    open fun setUsingSlash(value: Boolean): PatternTokenizer { usingSlash = value; needingQuoteCharacters = null; return this }
    open fun getLimit(): Int = limit
    open fun setLimit(value: Int): PatternTokenizer { limit = value; return this }
    open fun getStart(): Int = start
    open fun setStart(value: Int): PatternTokenizer { start = value; return this }
    open fun setPattern(value: CharSequence?): PatternTokenizer = setPattern(value!!.toString())
    open fun setPattern(value: String?): PatternTokenizer {
        if (value == null) throw IllegalArgumentException("Inconsistent arguments")
        start = 0; limit = value.length; pattern = value
        return this
    }
    open fun quoteLiteral(value: CharSequence?): String = quoteLiteral(value!!.toString())
    open fun quoteLiteral(value: String?): String {
        if (needingQuoteCharacters == null) {
            needingQuoteCharacters = UnicodeSet().addAll(syntaxCharacters).addAll(ignorableCharacters).addAll(extraQuotingCharacters)
            if (usingSlash) needingQuoteCharacters!!.add(BACK_SLASH.code)
            if (usingQuote) needingQuoteCharacters!!.add(SINGLE_QUOTE.code)
        }
        val text = value!!
        val result = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val cp = UTF16.charAt(text,i); i += UTF16.getCharCount(cp)
            if (escapeCharacters.contains(cp)) {
                if (quoted) { result.append(SINGLE_QUOTE); quoted = false }
                appendEscaped(result,cp); continue
            }
            if (needingQuoteCharacters!!.contains(cp)) {
                if (quoted) { UTF16.append(result,cp); if (usingQuote && cp == SINGLE_QUOTE.code) result.append(SINGLE_QUOTE); continue }
                if (usingSlash) { result.append(BACK_SLASH); UTF16.append(result,cp); continue }
                if (usingQuote) {
                    if (cp == SINGLE_QUOTE.code) { result.append(SINGLE_QUOTE).append(SINGLE_QUOTE); continue }
                    result.append(SINGLE_QUOTE); UTF16.append(result,cp); quoted = true; continue
                }
                appendEscaped(result,cp); continue
            }
            if (quoted) { result.append(SINGLE_QUOTE); quoted = false }
            UTF16.append(result,cp)
        }
        if (quoted) result.append(SINGLE_QUOTE)
        return result.toString()
    }
    private fun appendEscaped(result: StringBuilder,cp: Int) {
        result.append(if (cp <= 0xffff) "\\u" else "\\U").append(Utility.hex(cp.toLong(),if (cp <= 0xffff) 4 else 8))
    }
    open fun normalize(): String {
        val oldStart = start
        val result = StringBuilder(); val buffer = StringBuilder()
        while (true) {
            buffer.setLength(0)
            val status = next(buffer)
            if (status == DONE) { start = oldStart; return result.toString() }
            if (status != SYNTAX) result.append(quoteLiteral(buffer)) else result.append(buffer)
        }
    }
    open fun next(buffer: StringBuilder?): Int {
        if (start >= limit) return DONE
        var status = UNKNOWN
        var lastQuote = UNKNOWN
        var quoteStatus = NONE
        var hexCount = 0
        var hexValue = 0
        var i = start
        while (i < limit) {
            val text = pattern!!
            if (i !in text.indices) throw IndexOutOfBoundsException()
            val cp = UTF16.charAt(text,i)
            val width = UTF16.getCharCount(cp)
            when (quoteStatus) {
                SLASH_START -> when (cp) {
                    'u'.code -> { quoteStatus = HEX; hexCount = 4; hexValue = 0; i += width; continue }
                    'U'.code -> { quoteStatus = HEX; hexCount = 8; hexValue = 0; i += width; continue }
                    else -> {
                        if (usingSlash) { UTF16.append(buffer,cp); quoteStatus = NONE; i += width; continue }
                        else { buffer!!.append(BACK_SLASH); quoteStatus = NONE }
                    }
                }
                HEX -> {
                    hexValue = (hexValue shl 4) + cp
                    hexValue -= when (cp) {
                        in '0'.code..'9'.code -> '0'.code
                        in 'a'.code..'f'.code -> 'a'.code - 10
                        in 'A'.code..'F'.code -> 'A'.code - 10
                        else -> { start = i; return BROKEN_ESCAPE }
                    }
                    hexCount--
                    if (hexCount == 0) { quoteStatus = NONE; UTF16.append(buffer,hexValue) }
                    i += width; continue
                }
                AFTER_QUOTE -> {
                    if (cp == lastQuote) { UTF16.append(buffer,cp); quoteStatus = NORMAL_QUOTE; i += width; continue }
                    quoteStatus = NONE
                }
                START_QUOTE -> {
                    if (cp == lastQuote) { UTF16.append(buffer,cp); quoteStatus = NONE; i += width; continue }
                    UTF16.append(buffer,cp); quoteStatus = NORMAL_QUOTE; i += width; continue
                }
                NORMAL_QUOTE -> {
                    if (cp == lastQuote) { quoteStatus = AFTER_QUOTE; i += width; continue }
                    UTF16.append(buffer,cp); i += width; continue
                }
            }
            if (ignorableCharacters.contains(cp)) { i += width; continue }
            if (syntaxCharacters.contains(cp)) {
                if (status == UNKNOWN) { UTF16.append(buffer,cp); start = i + width; return SYNTAX }
                start = i; return status
            }
            status = LITERAL
            if (cp == BACK_SLASH.code) { quoteStatus = SLASH_START; i += width; continue }
            else if (usingQuote && cp == SINGLE_QUOTE.code) { lastQuote = cp; quoteStatus = START_QUOTE; i += width; continue }
            UTF16.append(buffer,cp); i += width
        }
        start = limit
        when (quoteStatus) {
            HEX -> status = BROKEN_ESCAPE
            SLASH_START -> if (usingSlash) status = BROKEN_ESCAPE else buffer!!.append(BACK_SLASH)
            START_QUOTE, NORMAL_QUOTE -> status = BROKEN_QUOTE
        }
        return status
    }
    companion object {
        const val SINGLE_QUOTE = '\''
        const val BACK_SLASH = '\\'
        const val DONE = 0; const val SYNTAX = 1; const val LITERAL = 2; const val BROKEN_QUOTE = 3; const val BROKEN_ESCAPE = 4; const val UNKNOWN = 5
        private const val AFTER_QUOTE = -1; private const val NONE = 0; private const val START_QUOTE = 1; private const val NORMAL_QUOTE = 2; private const val SLASH_START = 3; private const val HEX = 4
    }
}
