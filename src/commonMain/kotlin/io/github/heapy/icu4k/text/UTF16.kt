// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/**
 * ICU UTF-16 operations. Offsets are code-unit offsets unless named offset32.
 * Unpaired surrogates are preserved and count as one code point.
 * Java String/StringBuffer overloads share a common CharSequence implementation;
 * mutable operations use Kotlin StringBuilder.
 */
object UTF16 {
    const val SINGLE_CHAR_BOUNDARY = 1
    const val LEAD_SURROGATE_BOUNDARY = 2
    const val TRAIL_SURROGATE_BOUNDARY = 5
    const val CODEPOINT_MIN_VALUE = 0
    const val CODEPOINT_MAX_VALUE = 0x10ffff
    const val SUPPLEMENTARY_MIN_VALUE = 0x10000
    const val LEAD_SURROGATE_MIN_VALUE = 0xd800
    const val LEAD_SURROGATE_MAX_VALUE = 0xdbff
    const val TRAIL_SURROGATE_MIN_VALUE = 0xdc00
    const val TRAIL_SURROGATE_MAX_VALUE = 0xdfff
    const val SURROGATE_MIN_VALUE = LEAD_SURROGATE_MIN_VALUE
    const val SURROGATE_MAX_VALUE = TRAIL_SURROGATE_MAX_VALUE

    fun isSurrogate(codePoint: Int): Boolean = codePoint in SURROGATE_MIN_VALUE..SURROGATE_MAX_VALUE
    fun isLeadSurrogate(codePoint: Int): Boolean = codePoint in LEAD_SURROGATE_MIN_VALUE..LEAD_SURROGATE_MAX_VALUE
    fun isTrailSurrogate(codePoint: Int): Boolean = codePoint in TRAIL_SURROGATE_MIN_VALUE..TRAIL_SURROGATE_MAX_VALUE
    fun getCharCount(char32: Int): Int = if (char32 < SUPPLEMENTARY_MIN_VALUE) 1 else 2
    fun getLeadSurrogate(char32: Int): Char = if (char32 >= SUPPLEMENTARY_MIN_VALUE) {
        (0xd7c0 + (char32 shr 10)).toChar()
    } else '\u0000'
    fun getTrailSurrogate(char32: Int): Char = if (char32 >= SUPPLEMENTARY_MIN_VALUE) {
        (TRAIL_SURROGATE_MIN_VALUE + (char32 and 0x3ff)).toChar()
    } else char32.toChar()

    private fun pair(lead: Char, trail: Char): Int =
        ((lead.code - LEAD_SURROGATE_MIN_VALUE) shl 10) + trail.code - TRAIL_SURROGATE_MIN_VALUE + SUPPLEMENTARY_MIN_VALUE

    fun charAt(source: CharSequence, offset16: Int): Int {
        val ch = source[offset16]
        if (isLeadSurrogate(ch.code) && offset16 + 1 < source.length && isTrailSurrogate(source[offset16 + 1].code)) {
            return pair(ch, source[offset16 + 1])
        }
        if (isTrailSurrogate(ch.code) && offset16 > 0 && isLeadSurrogate(source[offset16 - 1].code)) {
            return pair(source[offset16 - 1], ch)
        }
        return ch.code
    }

    fun charAt(source: Replaceable, offset16: Int): Int = charAt(object : CharSequence {
        override val length: Int get() = source.length()
        override fun get(index: Int): Char = source.charAt(index)
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            buildString { for (i in startIndex until endIndex) append(source.charAt(i)) }
    }, offset16)

    fun bounds(source: CharSequence, offset16: Int): Int {
        val ch = source[offset16]
        if (isLeadSurrogate(ch.code) && offset16 + 1 < source.length && isTrailSurrogate(source[offset16 + 1].code)) {
            return LEAD_SURROGATE_BOUNDARY
        }
        if (isTrailSurrogate(ch.code) && offset16 > 0 && isLeadSurrogate(source[offset16 - 1].code)) {
            return TRAIL_SURROGATE_BOUNDARY
        }
        return SINGLE_CHAR_BOUNDARY
    }

    fun valueOf(char32: Int): String {
        require(char32 in CODEPOINT_MIN_VALUE..CODEPOINT_MAX_VALUE) { "Illegal codepoint" }
        return if (char32 < SUPPLEMENTARY_MIN_VALUE) char32.toChar().toString()
        else charArrayOf(getLeadSurrogate(char32), getTrailSurrogate(char32)).concatToString()
    }

    fun valueOf(source: CharSequence, offset16: Int): String = valueOf(charAt(source, offset16))

    fun findOffsetFromCodePoint(source: CharSequence, offset32: Int): Int = moveCodePointOffset(source, 0, offset32).also {
        if (offset32 < 0) throw IndexOutOfBoundsException("Negative code-point offset: $offset32")
    }

    fun findCodePointOffset(source: CharSequence, offset16: Int): Int {
        checkOffset(offset16, source.length)
        var index = 0
        var count = 0
        while (index < offset16) {
            val width = forwardWidth(source, index)
            if (index + width > offset16) break
            index += width
            count++
        }
        return count
    }

    fun countCodePoint(source: CharSequence?): Int = if (source == null) 0 else findCodePointOffset(source, source.length)

    fun moveCodePointOffset(source: CharSequence, offset16: Int, shift32: Int): Int {
        checkOffset(offset16, source.length)
        var index = offset16
        var remaining = shift32
        while (remaining > 0) {
            if (index == source.length) throw IndexOutOfBoundsException("Code-point shift: $shift32")
            index += forwardWidth(source, index)
            remaining--
        }
        while (remaining < 0) {
            if (index == 0) throw IndexOutOfBoundsException("Code-point shift: $shift32")
            index--
            if (isTrailSurrogate(source[index].code) && index > 0 && isLeadSurrogate(source[index - 1].code)) index--
            remaining++
        }
        return index
    }

    private fun forwardWidth(source: CharSequence, index: Int): Int =
        if (isLeadSurrogate(source[index].code) && index + 1 < source.length && isTrailSurrogate(source[index + 1].code)) 2 else 1

    private fun checkOffset(offset: Int, length: Int) {
        if (offset < 0 || offset > length) throw IndexOutOfBoundsException("Offset $offset, length $length")
    }

    fun append(target: StringBuilder, char32: Int): StringBuilder = target.append(valueOf(char32))
    fun appendCodePoint(target: StringBuilder, cp: Int): StringBuilder = append(target, cp)

    fun setCharAt(target: StringBuilder, offset16: Int, char32: Int) {
        val boundary = bounds(target, offset16)
        val start = offset16 - (boundary shr 2)
        val replacement = valueOf(char32)
        target.deleteRange(start, offset16 + (boundary and 3))
        target.insert(start, replacement)
    }

    fun insert(target: StringBuilder, offset16: Int, char32: Int): StringBuilder {
        val text = valueOf(char32)
        checkOffset(offset16, target.length)
        val index = if (offset16 < target.length && bounds(target, offset16) == TRAIL_SURROGATE_BOUNDARY) offset16 + 1 else offset16
        return target.insert(index, text)
    }

    fun delete(target: StringBuilder, offset16: Int): StringBuilder {
        val boundary = bounds(target, offset16)
        return target.deleteRange(offset16 - (boundary shr 2), offset16 + (boundary and 3))
    }

    fun indexOf(source: String, char32: Int, fromIndex: Int = 0): Int =
        searchCodePoint(source, char32, fromIndex, backwards = false)

    fun lastIndexOf(source: String, char32: Int, fromIndex: Int = source.length - 1): Int =
        searchCodePoint(source, char32, fromIndex, backwards = true)

    private fun searchCodePoint(source: String, char32: Int, fromIndex: Int, backwards: Boolean): Int {
        val text = valueOf(char32)
        var from = fromIndex
        while (true) {
            val found = if (backwards) source.lastIndexOf(text, from) else source.indexOf(text, from)
            if (found < 0 || !isSurrogate(char32)) return found
            // Preserve ICU's surrogate search semantics, including consecutive leads.
            val followedByTrail = isLeadSurrogate(char32) && found + 1 < source.length && isTrailSurrogate(source[found + 1].code)
            val precededByLead = found > 0 && isLeadSurrogate(source[found - 1].code)
            if (!followedByTrail && !precededByLead) return found
            from = found + if (backwards) -1 else 1
        }
    }

    fun replace(source: String, oldChar32: Int, newChar32: Int): String {
        require(oldChar32 in 1..CODEPOINT_MAX_VALUE && newChar32 in 1..CODEPOINT_MAX_VALUE) { "Illegal replacement codepoint" }
        val replacement = valueOf(newChar32)
        var match = indexOf(source, oldChar32)
        if (match < 0) return source
        val width = getCharCount(oldChar32)
        return buildString {
            var copiedUntil = 0
            while (match >= 0) {
                append(source, copiedUntil, match)
                append(replacement)
                copiedUntil = match + width
                match = indexOf(source, oldChar32, copiedUntil)
            }
            append(source, copiedUntil, source.length)
        }
    }

    /** Returns a new builder, as ICU does; does not mutate [source]. */
    fun reverse(source: CharSequence): StringBuilder {
        val result = StringBuilder(source.length)
        var index = source.length
        while (index > 0) {
            val end = index
            index = moveCodePointOffset(source, index, -1)
            result.append(source, index, end)
        }
        return result
    }

    fun hasMoreCodePointsThan(source: CharSequence?, number: Int): Boolean {
        if (number < 0) return true
        if (source == null || source.length <= number) return false
        if (source.length / 2 > number) return true
        var index = 0
        var remaining = number
        while (index < source.length) {
            if (remaining == 0) return true
            index += forwardWidth(source, index)
            remaining--
        }
        return false
    }

    fun newString(codePoints: IntArray, offset: Int, count: Int): String {
        if (offset < 0 || count < 0 || offset > codePoints.size - count) throw IndexOutOfBoundsException("Invalid code-point range")
        return buildString { for (index in offset until offset + count) append(valueOf(codePoints[index])) }
    }

    fun getSingleCodePoint(s: CharSequence?): Int = when {
        s == null || s.isEmpty() || s.length > 2 -> -1
        s.length == 1 -> s[0].code
        isLeadSurrogate(s[0].code) && isTrailSurrogate(s[1].code) -> pair(s[0], s[1])
        else -> -1
    }

    fun compareCodePoint(codePoint: Int, s: CharSequence?): Int {
        if (s == null || s.isEmpty()) return 1
        // charAt also looks backwards; here the offset is always zero.
        val diff = codePoint - charAt(s, 0)
        return if (diff != 0) diff else if (s.length == getCharCount(codePoint)) 0 else -1
    }

    // Array views keep pairing bounded by the requested slice, without copying it.
    private class ArraySlice(val array: CharArray, val start: Int, val limit: Int) : CharSequence {
        init {
            if (start < 0 || limit < start || limit > array.size) throw IndexOutOfBoundsException("Invalid array slice")
        }
        override val length: Int get() = limit - start
        override fun get(index: Int): Char {
            if (index < 0 || index >= length) throw IndexOutOfBoundsException("Slice offset: $index")
            return array[start + index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            if (startIndex < 0 || endIndex < startIndex || endIndex > length) throw IndexOutOfBoundsException("Invalid subsequence")
            return ArraySlice(array, start + startIndex, start + endIndex)
        }
    }

    fun charAt(source: CharArray, start: Int, limit: Int, offset16: Int): Int = charAt(ArraySlice(source, start, limit), offset16)
    fun bounds(source: CharArray, start: Int, limit: Int, offset16: Int): Int = bounds(ArraySlice(source, start, limit), offset16)
    fun valueOf(source: CharArray, start: Int, limit: Int, offset16: Int): String = valueOf(ArraySlice(source, start, limit), offset16)
    fun findOffsetFromCodePoint(source: CharArray, start: Int, limit: Int, offset32: Int): Int = findOffsetFromCodePoint(ArraySlice(source, start, limit), offset32)
    fun findCodePointOffset(source: CharArray, start: Int, limit: Int, offset16: Int): Int = findCodePointOffset(ArraySlice(source, start, limit), offset16)
    fun countCodePoint(source: CharArray, start: Int, limit: Int): Int = countCodePoint(ArraySlice(source, start, limit))
    fun moveCodePointOffset(source: CharArray, start: Int, limit: Int, offset16: Int, shift32: Int): Int = moveCodePointOffset(ArraySlice(source, start, limit), offset16, shift32)
    fun hasMoreCodePointsThan(source: CharArray, start: Int, limit: Int, number: Int): Boolean = hasMoreCodePointsThan(ArraySlice(source, start, limit), number)

    fun append(target: CharArray, limit: Int, char32: Int): Int {
        val text = valueOf(char32)
        if (limit < 0 || limit > target.size - text.length) throw IndexOutOfBoundsException("Insufficient array capacity")
        text.toCharArray().copyInto(target, limit)
        return limit + text.length
    }

    fun insert(target: CharArray, limit: Int, offset16: Int, char32: Int): Int {
        val text = valueOf(char32)
        val view = ArraySlice(target, 0, limit)
        checkOffset(offset16, limit)
        val index = if (offset16 < limit && bounds(view, offset16) == TRAIL_SURROGATE_BOUNDARY) offset16 + 1 else offset16
        if (limit > target.size - text.length) throw IndexOutOfBoundsException("Insufficient array capacity")
        target.copyInto(target, index + text.length, index, limit)
        text.toCharArray().copyInto(target, index)
        return limit + text.length
    }

    fun delete(target: CharArray, limit: Int, offset16: Int): Int {
        val boundary = bounds(target, 0, limit, offset16)
        val start = offset16 - (boundary shr 2)
        val end = offset16 + (boundary and 3)
        target.copyInto(target, start, end, limit)
        val newLimit = limit - (end - start)
        target[newLimit] = '\u0000'
        return newLimit
    }

    fun setCharAt(target: CharArray, limit: Int, offset16: Int, char32: Int): Int {
        val boundary = bounds(target, 0, limit, offset16)
        val start = offset16 - (boundary shr 2)
        val end = offset16 + (boundary and 3)
        val text = valueOf(char32)
        val newLimit = limit - (end - start) + text.length
        if (newLimit > target.size) throw IndexOutOfBoundsException("Insufficient array capacity")
        target.copyInto(target, start + text.length, end, limit)
        text.toCharArray().copyInto(target, start)
        if (newLimit != limit && newLimit < target.size) target[newLimit] = '\u0000'
        return newLimit
    }
}
