// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.text.UnicodeSet

/** Mutable parser window; char access is relative to its offset, not bounded by its logical end. */
open class StringSegment(input: String?, private val foldCase: Boolean) : CharSequence {
    private val str = input
    private var start = 0
    private var end = input!!.length
    open fun getOffset(): Int = start
    open fun setOffset(offset: Int) { assertStringSegment { offset <= end }; start = offset }
    open fun adjustOffset(delta: Int) {
        assertStringSegment { start + delta >= 0 }
        assertStringSegment { start + delta <= end }
        start += delta
    }
    open fun adjustOffsetByCodePoint() { start += stringSegmentCharCount(getCodePoint()) }
    open fun setLength(length: Int) {
        assertStringSegment { length >= 0 }
        assertStringSegment { start + length <= str!!.length }
        end = start + length
    }
    open fun resetLength() { end = str!!.length }
    override val length: Int get() = end - start
    override fun get(index: Int): Char = str!![index + start]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = str!!.subSequence(startIndex + start, endIndex + start)
    open fun getCodePoint(): Int {
        assertStringSegment { start < end }
        val lead = str!![start]
        if (lead.isHighSurrogate() && start + 1 < end) {
            val trail = str[start + 1]
            if (trail.isLowSurrogate()) return ((lead.code - 0xd800) shl 10) + trail.code - 0xdc00 + 0x10000
        }
        return lead.code
    }
    open fun codePointAt(index: Int): Int = stringSegmentCodePointAt(str!!, start + index)
    open fun startsWith(otherCp: Int): Boolean = codePointsEqual(getCodePoint(), otherCp, foldCase)
    open fun startsWith(uniset: UnicodeSet?): Boolean {
        val cp = getCodePoint()
        if (cp == -1) return false
        return uniset!!.contains(cp)
    }
    open fun startsWith(other: CharSequence?): Boolean {
        if (other == null || other.length == 0 || length == 0) return false
        return codePointsEqual(stringSegmentCodePointAt(this, 0), stringSegmentCodePointAt(other, 0), foldCase)
    }
    open fun getCommonPrefixLength(other: CharSequence?): Int = prefixLength(other, foldCase)
    open fun getCaseSensitivePrefixLength(other: CharSequence?): Int = prefixLength(other, false)
    private fun prefixLength(other: CharSequence?, fold: Boolean): Int {
        assertStringSegment { other!!.length != 0 }
        var offset = 0
        while (offset < minOf(length, other!!.length)) {
            val a = stringSegmentCodePointAt(this, offset)
            val b = stringSegmentCodePointAt(other, offset)
            if (!codePointsEqual(a, b, fold)) break
            offset += stringSegmentCharCount(a)
        }
        return offset
    }
    open fun contentEquals(other: CharSequence?): Boolean = Utility.charSequenceEquals(this, other)
    override fun toString(): String = str!!.substring(0, start) + "[" + str.substring(start, end) + "]" + str.substring(end)
    open fun asString(): String = str!!.substring(start, end)
    private fun codePointsEqual(a: Int, b: Int, fold: Boolean): Boolean {
        if (a == b) return true
        if (!fold) return false
        return UCharacter.foldCase(a, true) == UCharacter.foldCase(b, true)
    }
}
internal fun stringSegmentCharCount(cp: Int): Int = if (cp >= 0x10000) 2 else 1
internal fun stringSegmentCodePointAt(text: CharSequence, index: Int): Int {
    val lead = text[index]
    if (lead.isHighSurrogate() && index + 1 < text.length) {
        val trail = text[index + 1]
        if (trail.isLowSurrogate()) return ((lead.code - 0xd800) shl 10) + trail.code - 0xdc00 + 0x10000
    }
    return lead.code
}
internal expect fun assertStringSegment(condition: () -> Boolean)
