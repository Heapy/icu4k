// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UCharacterDirection

/** Source static reordering algorithms, shared by the paragraph resolver and writer. */
internal object BidiReorderingCore {
    const val MAX_EXPLICIT_LEVEL = 125
    const val MAP_NOWHERE = -1
    const val LTR = 0
    const val RTL = 1
    const val NEUTRAL = 3
    const val KEEP_BASE_COMBINING = 1
    const val DO_MIRRORING = 2
    const val INSERT_LRM_FOR_NUMERIC = 4
    const val REMOVE_BIDI_CONTROLS = 8
    const val OUTPUT_REVERSE = 16
    const val REORDER_RUNS_ONLY = 3
    const val REORDER_INVERSE_NUMBERS_AS_L = 4
    const val REORDER_INVERSE_LIKE_DIRECT = 5
    const val REORDER_INVERSE_FOR_NUMBERS_SPECIAL = 6
    const val OPTION_INSERT_MARKS = 1
    const val OPTION_REMOVE_CONTROLS = 2
    const val LRM_BEFORE = 1
    const val LRM_AFTER = 2
    const val RLM_BEFORE = 4
    const val RLM_AFTER = 8
    fun normalizeReorderingOptions(options: Int): Int =
        if ((options and OPTION_REMOVE_CONTROLS) != 0) options and OPTION_INSERT_MARKS.inv() else options
    fun isBidiControlChar(c: Int): Boolean = (c and -4) == 0x200c || c in 0x202a..0x202e || c in 0x2066..0x2069
    fun baseDirection(paragraph: CharSequence?): Byte {
        if (paragraph == null || paragraph.isEmpty()) return NEUTRAL.toByte()
        val length = paragraph.length
        var i = 0
        while (i < length) {
            val c = codePointAtForward(paragraph, i)
            when (UCharacter.getDirectionality(c).toInt()) {
                UCharacterDirection.LEFT_TO_RIGHT -> return LTR.toByte()
                UCharacterDirection.RIGHT_TO_LEFT, UCharacterDirection.RIGHT_TO_LEFT_ARABIC -> return RTL.toByte()
            }
            i = offsetOne(paragraph, i)
        }
        return NEUTRAL.toByte()
    }
    private fun codePointAtForward(text: CharSequence, index: Int): Int {
        val first = text[index]
        if (UTF16.isLeadSurrogate(first.code) && index + 1 < text.length) {
            val second = text[index + 1]
            if (UTF16.isTrailSurrogate(second.code)) return ((first.code - 0xd800) shl 10) + second.code - 0xdc00 + 0x10000
        }
        return first.code
    }
    private fun offsetOne(text: CharSequence, index: Int): Int {
        if (index < 0 || index > text.length) throw IndexOutOfBoundsException("index ( $index) out of range 0, ${text.length}")
        val limit = text.length
        var next = index
        var ch = text[next++]
        while (UTF16.isLeadSurrogate(ch.code) && next < limit) {
            ch = text[next++]
            if (!UTF16.isTrailSurrogate(ch.code)) return next - 1
        }
        return next
    }
    fun requiresBidi(text: CharArray?, start: Int, limit: Int): Boolean {
        val mask = (1 shl UCharacterDirection.RIGHT_TO_LEFT) or (1 shl UCharacterDirection.RIGHT_TO_LEFT_ARABIC) or
            (1 shl UCharacterDirection.RIGHT_TO_LEFT_EMBEDDING) or (1 shl UCharacterDirection.RIGHT_TO_LEFT_OVERRIDE) or
            (1 shl UCharacterDirection.ARABIC_NUMBER)
        for (i in start until limit) if (((1 shl UCharacter.getDirection(bidiCharAt(text, i).code)) and mask) != 0) return true
        return false
    }
    private class Reorder(val map: IntArray, val min: Int, val max: Int)
    private fun prepareReorder(levels: ByteArray?): Reorder? {
        if (levels == null || levels.isEmpty()) return null
        var min = MAX_EXPLICIT_LEVEL + 1
        var max = 0
        for (i in levels.lastIndex downTo 0) {
            val level = levels[i].toInt()
            if (level < 0 || level > MAX_EXPLICIT_LEVEL + 1) return null
            if (level < min) min = level
            if (level > max) max = level
        }
        return Reorder(IntArray(levels.size) { it }, min, max)
    }
    fun reorderLogical(levels: ByteArray?): IntArray? {
        val prepared = prepareReorder(levels) ?: return null
        val map = prepared.map
        val min = prepared.min
        var max = prepared.max
        if (min == max && (min and 1) == 0) return map
        val lowestOdd = min or 1
        do {
            var start = 0
            while (true) {
                while (start < levels!!.size && levels[start] < max) ++start
                if (start >= levels.size) break
                var limit = start
                while (++limit < levels.size && levels[limit] >= max) { }
                val sum = start + limit - 1
                do { map[start] = sum - map[start] } while (++start < limit)
                if (limit == levels.size) break else start = limit + 1
            }
        } while (--max >= lowestOdd)
        return map
    }
    fun reorderVisual(levels: ByteArray?): IntArray? {
        val prepared = prepareReorder(levels) ?: return null
        val map = prepared.map
        val min = prepared.min
        var max = prepared.max
        if (min == max && (min and 1) == 0) return map
        val lowestOdd = min or 1
        do {
            var start = 0
            while (true) {
                while (start < levels!!.size && levels[start] < max) ++start
                if (start >= levels.size) break
                var limit = start
                while (++limit < levels.size && levels[limit] >= max) { }
                var end = limit - 1
                while (start < end) {
                    val temp = map[start]; map[start] = map[end]; map[end] = temp
                    ++start; --end
                }
                if (limit == levels.size) break else start = limit + 1
            }
        } while (--max >= lowestOdd)
        return map
    }
    fun invertMap(source: IntArray?): IntArray? {
        val src = source ?: return null
        var length = -1
        var count = 0
        for (entry in src) {
            if (entry > length) length = entry
            if (entry >= 0) ++count
        }
        ++length
        val dest = IntArray(length)
        // Source counts entries, including duplicates, before deciding whether to fill holes.
        if (count < length) dest.fill(MAP_NOWHERE)
        for (i in src.indices) if (src[i] >= 0) dest[src[i]] = i
        return dest
    }
    @Suppress("UNCHECKED_CAST")
    fun <T> reorderVisually(levels: ByteArray?, levelStart: Int, objects: Array<T>?, objectStart: Int, count: Int) {
        val reorderLevels = ByteArray(count)
        levels!!.copyInto(reorderLevels, 0, levelStart, levelStart + count)
        val map = reorderVisual(reorderLevels)
        val temp = arrayOfNulls<Any?>(count)
        objects!!.copyInto(temp, 0, objectStart, objectStart + count)
        for (i in 0 until count) objects[objectStart + i] = temp[map!![i]] as T
    }
}

internal expect fun bidiCharAt(data: CharArray?, index: Int): Char
