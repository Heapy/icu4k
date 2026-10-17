// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

/** Extension-table lookup shared by incremental and single-character MBCS decoding. */
internal object MBCSToUnicodeExtension {
    private const val VALUE_MASK = 0xffffff
    private const val MIN_CODE_POINT = 0x1f0000
    private const val ROUNDTRIP = 1 shl 23

    /** Does not consume either cursor. A negative length requests more input. */
    fun match(
        extension: BinaryBuffer?, sisoState: Int, pre: ByteArray, preBegin: Int,
        preLength: Int, source: BinaryBuffer?, valueOut: IntArray, flush: Boolean,
    ): Int {
        // asIntBuffer() starts at the current position; ARRAY(), below, uses absolute pointers.
        if (extension == null || extension.slice(extension.position + 8, 4).readInt() <= 0) return 0
        val table = extension.slice(extension.slice(4, 4).readInt())
        fun word(index: Int) = table.slice(index * 4, 4).readInt()
        var sourceLength = source?.remaining ?: 0
        var finalInput = flush
        if (sisoState == 0) {
            if (preLength > 1) return 0
            sourceLength = if (preLength == 1) 0 else minOf(sourceLength, 1)
            finalInput = true
        }
        var index = 0
        var i = 0
        var j = 0
        var matchLength = 0
        var matchValue = 0
        fun allowed(length: Int) = sisoState < 0 || (sisoState == 0) == (length == 1)
        while (true) {
            val header = word(index)
            val length = header ushr 24
            val default = header and VALUE_MASK
            // ICU4J's isToUUseFallback(boolean) always returns true.
            if (default != 0 && allowed(i + j)) {
                matchValue = default
                matchLength = i + j
            }
            val byte = when {
                i < preLength -> pre[preBegin + i++].toInt() and 255
                j < sourceLength -> source!!.getByte(source.position + j++).toInt() and 255
                else -> {
                    if (!finalInput && i + j <= 31) return -(i + j)
                    break
                }
            }
            val value = find(length, byte) { word(index + 1 + it) }
            if (value == 0) break
            if (value < MIN_CODE_POINT) index = value
            else {
                if (allowed(i + j)) {
                    matchValue = value
                    matchLength = i + j
                }
                break
            }
        }
        if (matchLength == 0) return 0
        valueOut[0] = matchValue and ROUNDTRIP.inv()
        return matchLength
    }

    /** Section entries are sorted unsigned words; the header has already been skipped. */
    internal inline fun find(length: Int, byte: Int, wordAt: (Int) -> Int): Int {
        var start = wordAt(0) ushr 24
        var limit = wordAt(length - 1) ushr 24
        if (byte < start || limit < byte) return 0
        if (length == limit - start + 1) return wordAt(byte - start) and VALUE_MASK
        val word0 = byte.toLong() shl 24
        val word = word0 or VALUE_MASK.toLong()
        start = 0
        limit = length
        while (true) {
            val distance = limit - start
            if (distance <= 1) break
            if (distance <= 4) {
                if (word0 <= (wordAt(start).toLong() and 0xffffffffL)) break
                ++start
                if (start < limit && word0 <= (wordAt(start).toLong() and 0xffffffffL)) break
                ++start
                if (start < limit && word0 <= (wordAt(start).toLong() and 0xffffffffL)) break
                ++start
                break
            }
            val middle = (start + limit) / 2
            if (word < (wordAt(middle).toLong() and 0xffffffffL)) limit = middle else start = middle
        }
        if (start < limit) {
            val found = wordAt(start)
            if (byte == found ushr 24) return found and VALUE_MASK
        }
        return 0
    }

    /** Decode a successful, roundtrip-masked match into its UTF-16 result. */
    fun characters(extension: BinaryBuffer, value: Int): CharArray {
        if (value <= 0x2fffff) {
            val cp = value - MIN_CODE_POINT
            return if (cp <= 0xffff) charArrayOf(cp.toChar()) else charArrayOf(
                ((cp ushr 10) + 0xd7c0).toChar(), ((cp and 1023) + 0xdc00).toChar(),
            )
        }
        val chars = extension.slice(extension.slice(12, 4).readInt() + (value and 0x3ffff) * 2)
        return CharArray((value ushr 18) - 12) { chars.readUnsignedShort().toChar() }
    }
}
