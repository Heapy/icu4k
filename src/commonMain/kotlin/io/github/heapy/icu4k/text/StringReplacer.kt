// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2000-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Utility

internal class StringReplacer private constructor(private val output: String, private val cursorPos: Int,
    private val hasCursor: Boolean, private val data: TransliterationData) : UnicodeReplacer {
    private var isComplex = true
    constructor(output: String, cursorPos: Int, data: TransliterationData) : this(output, cursorPos, true, data)
    constructor(output: String, data: TransliterationData) : this(output, 0, false, data)

    override fun replace(text: Replaceable, start: Int, limit: Int, cursor: IntArray): Int {
        val outLen: Int
        var newStart = 0
        if (!isComplex) {
            text.replace(start, limit, output)
            outLen = output.length
            newStart = cursorPos
        } else {
            val buf = StringBuilder()
            isComplex = false
            val tempStart = text.length()
            var destStart = tempStart
            if (start > 0) {
                val len = UTF16.getCharCount(text.char32At(start - 1))
                text.copy(start - len, start, tempStart)
                destStart += len
            } else {
                text.replace(tempStart, tempStart, "\uFFFF")
                ++destStart
            }
            var destLimit = destStart
            var tempExtra = 0
            var offset = 0
            while (offset < output.length) {
                if (offset == cursorPos) newStart = buf.length + destLimit - destStart
                val cp = UTF16.charAt(output, offset)
                val nextIndex = offset + UTF16.getCharCount(cp)
                if (nextIndex == output.length) {
                    tempExtra = UTF16.getCharCount(text.char32At(limit))
                    text.copy(limit, limit + tempExtra, destLimit)
                }
                val replacer = data.lookupReplacer(cp)
                if (replacer == null) UTF16.append(buf, cp)
                else {
                    isComplex = true
                    if (buf.isNotEmpty()) {
                        text.replace(destLimit, destLimit, buf.toString())
                        destLimit += buf.length
                        buf.setLength(0)
                    }
                    destLimit += replacer.replace(text, destLimit, destLimit, cursor)
                }
                offset = nextIndex
            }
            if (buf.isNotEmpty()) {
                text.replace(destLimit, destLimit, buf.toString())
                destLimit += buf.length
            }
            if (offset == cursorPos) newStart = destLimit - destStart
            outLen = destLimit - destStart
            text.copy(destStart, destLimit, start)
            text.replace(tempStart + outLen, destLimit + tempExtra + outLen, "")
            text.replace(start + outLen, limit + outLen, "")
        }
        if (hasCursor) {
            when {
                cursorPos < 0 -> {
                    newStart = start
                    var n = cursorPos
                    while (n < 0 && newStart > 0) { newStart -= UTF16.getCharCount(text.char32At(newStart - 1)); ++n }
                    newStart += n
                }
                cursorPos > output.length -> {
                    newStart = start + outLen
                    var n = cursorPos - output.length
                    while (n > 0 && newStart < text.length()) { newStart += UTF16.getCharCount(text.char32At(newStart)); --n }
                    newStart += n
                }
                else -> newStart += start
            }
            cursor[0] = newStart
        }
        return outLen
    }
    override fun toReplacerPattern(escapeUnprintable: Boolean): String {
        val rule = StringBuilder()
        val quote = StringBuilder()
        var cursor = cursorPos
        if (hasCursor && cursor < 0) {
            while (cursor++ < 0) Utility.appendToRule(rule, '@'.code, true, escapeUnprintable, quote)
        }
        for (i in output.indices) {
            if (hasCursor && i == cursor) Utility.appendToRule(rule, '|'.code, true, escapeUnprintable, quote)
            val c = output[i]
            val replacer = data.lookupReplacer(c.code)
            if (replacer == null) Utility.appendToRule(rule, c.code, false, escapeUnprintable, quote)
            else Utility.appendToRule(rule, " " + replacer.toReplacerPattern(escapeUnprintable) + " ", true, escapeUnprintable, quote)
        }
        if (hasCursor && cursor > output.length) {
            cursor -= output.length
            while (cursor-- > 0) Utility.appendToRule(rule, '@'.code, true, escapeUnprintable, quote)
            Utility.appendToRule(rule, '|'.code, true, escapeUnprintable, quote)
        }
        Utility.appendToRule(rule, -1, true, escapeUnprintable, quote)
        return rule.toString()
    }
    override fun addReplacementSetTo(toUnionTo: UnicodeSet) {
        var i = 0
        while (i < output.length) {
            val cp = UTF16.charAt(output, i)
            val replacer = data.lookupReplacer(cp)
            if (replacer == null) toUnionTo.add(cp) else replacer.addReplacementSetTo(toUnionTo)
            i += UTF16.getCharCount(cp)
        }
    }
}
