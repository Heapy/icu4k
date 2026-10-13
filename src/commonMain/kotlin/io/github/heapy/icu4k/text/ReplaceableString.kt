// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** A [Replaceable] without metadata. The builder constructor shares storage. */
open class ReplaceableString(private val buffer: StringBuilder) : Replaceable {
    constructor(text: String) : this(StringBuilder(text))
    constructor() : this(StringBuilder())

    override fun toString(): String = buffer.toString()
    fun substring(start: Int, limit: Int): String = buffer.substring(start, limit)
    override fun length(): Int = buffer.length
    override fun charAt(offset: Int): Char = buffer[offset]
    override fun char32At(offset: Int): Int = UTF16.charAt(buffer, offset)

    override fun getChars(srcStart: Int, srcLimit: Int, dst: CharArray, dstStart: Int) {
        // ICU treats empty copies as no-ops, without validating the other offsets.
        if (srcStart == srcLimit) return
        checkRange(srcStart, srcLimit)
        val count = srcLimit - srcStart
        if (dstStart < 0 || dstStart > dst.size - count) throw IndexOutOfBoundsException("Invalid destination range")
        for (i in 0 until count) dst[dstStart + i] = buffer[srcStart + i]
    }

    override fun replace(start: Int, limit: Int, text: String) {
        // StringBuffer clamps an oversized limit to its current length.
        if (start < 0 || start > buffer.length || limit < start) throw IndexOutOfBoundsException("Invalid replacement range")
        buffer.deleteRange(start, minOf(limit, buffer.length))
        buffer.insert(start, text)
    }

    override fun replace(start: Int, limit: Int, chars: CharArray, charsStart: Int, charsLen: Int) {
        if (charsStart < 0 || charsLen < 0 || charsStart > chars.size - charsLen) throw IndexOutOfBoundsException("Invalid character range")
        replace(start, limit, chars.concatToString(charsStart, charsStart + charsLen))
    }

    override fun copy(start: Int, limit: Int, dest: Int) {
        if (start == limit && start in 0..buffer.length) return
        checkRange(start, limit)
        val snapshot = buffer.substring(start, limit)
        replace(dest, dest, snapshot)
    }

    override fun hasMetaData(): Boolean = false

    private fun checkRange(start: Int, limit: Int) {
        if (start < 0 || limit < start || limit > buffer.length) throw IndexOutOfBoundsException("Invalid source range")
    }
}
