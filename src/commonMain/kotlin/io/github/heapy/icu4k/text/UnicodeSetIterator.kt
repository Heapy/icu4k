// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Iterates code points or ranges, then sorted strings. Do not modify the set during iteration. */
class UnicodeSetIterator(private var set: UnicodeSet = UnicodeSet()) {
    @kotlin.jvm.JvmField var codepoint: Int = 0
    @kotlin.jvm.JvmField var codepointEnd: Int = 0
    @kotlin.jvm.JvmField var string: String? = null
    private var endRange = 0
    private var range = 0
    private var endElement = -1
    private var nextElement = 0
    private var stringIterator: Iterator<String>? = null
    init { reset() }
    fun skipToStrings(): UnicodeSetIterator {
        range = endRange; endElement = -1; nextElement = 0
        return this
    }
    fun next(): Boolean {
        if (nextElement <= endElement) {
            codepoint = nextElement++; codepointEnd = codepoint
            return true
        }
        if (range < endRange) {
            loadRange(++range)
            codepoint = nextElement++; codepointEnd = codepoint
            return true
        }
        return nextString()
    }
    fun nextRange(): Boolean {
        if (nextElement <= endElement) {
            codepointEnd = endElement; codepoint = nextElement; nextElement = endElement + 1
            return true
        }
        if (range < endRange) {
            loadRange(++range)
            codepointEnd = endElement; codepoint = nextElement; nextElement = endElement + 1
            return true
        }
        return nextString()
    }
    private fun nextString(): Boolean {
        val iterator = stringIterator ?: return false
        codepoint = IS_STRING
        string = iterator.next()
        if (!iterator.hasNext()) stringIterator = null
        return true
    }
    fun reset(uset: UnicodeSet) { set = uset; reset() }
    fun reset() {
        endRange = set.getRangeCount() - 1
        range = 0; endElement = -1; nextElement = 0
        if (endRange >= 0) loadRange(range)
        stringIterator = if (set.hasStrings()) set.stringIterator() else null
    }
    fun getString(): String? = if (codepoint != IS_STRING) UTF16.valueOf(codepoint) else string
    private fun loadRange(aRange: Int) { nextElement = set.getRangeStart(aRange); endElement = set.getRangeEnd(aRange) }
    companion object { const val IS_STRING = -1 }
}
