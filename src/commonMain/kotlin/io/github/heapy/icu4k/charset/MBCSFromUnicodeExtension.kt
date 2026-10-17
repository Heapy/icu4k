// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

/** MBCS extension trie, longest-match lookup and stateful byte output. */
internal object MBCSFromUnicodeExtension {
    private fun word(b: BinaryBuffer, index: Int): Int {
        if (index < 0 || index > b.limit - 4) throw IndexOutOfBoundsException()
        val p = if (b.order == BinaryBuffer.ByteOrder.BIG_ENDIAN) index else index + 3
        val step = if (b.order == BinaryBuffer.ByteOrder.BIG_ENDIAN) 1 else -1
        return ((b.getByte(p).toInt() and 255) shl 24) or ((b.getByte(p + step).toInt() and 255) shl 16) or
            ((b.getByte(p + 2 * step).toInt() and 255) shl 8) or (b.getByte(p + 3 * step).toInt() and 255)
    }
    private fun unit(b: BinaryBuffer, index: Int): Int {
        if (index < 0 || index > b.limit - 2) throw IndexOutOfBoundsException()
        val a = b.getByte(index).toInt() and 255; val c = b.getByte(index + 1).toInt() and 255
        return if (b.order == BinaryBuffer.ByteOrder.BIG_ENDIAN) (a shl 8) or c else (c shl 8) or a
    }
    /** Result >=2 includes a fixed two-unit bias; 1 requests subChar1; negative needs more input. */
    fun match(extension: BinaryBuffer?, firstCP: Int, pre: CharArray?, preBegin: Int, preLength: Int,
              source: CharsetCharBuffer?, valueOut: IntArray, useFallback: Boolean, flush: Boolean): Int {
        val b = extension ?: return 0
        var index = firstCP ushr 10
        if (index >= word(b, b.position + 44)) return 0
        val stage12 = word(b, 40); val stage3 = word(b, 52); val stage3b = word(b, 60)
        index = unit(b, stage3 + 2 * ((unit(b, stage12 + 2 * (unit(b, stage12 + 2 * index) + ((firstCP ushr 4) and 63))) shl 2) + (firstCP and 15)))
        var value = word(b, stage3b + 4 * index)
        if (value == 0) return 0
        var matchValue = 0; var matchLength = 0
        // Source deliberately uses TO_U_IS_PARTIAL for this first test, not FROM_U_IS_PARTIAL.
        if (value >= 0 && value < 0x1f0000) {
            index = value
            val chars = word(b, 20); val values = word(b, 24)
            var i = 0; var j = 0
            while (true) {
                val length = unit(b, chars + 2 * index)
                value = word(b, values + 4 * index)
                if (value != 0 && (value < 0 || CharsetEncoderRuntime.isFromUUseFallback(useFallback, firstCP))) {
                    matchValue = value; matchLength = 2 + i + j
                }
                val c = when {
                    i < preLength -> {
                        val offset = preBegin + i++
                        if (pre == null || offset !in pre.indices) throw IndexOutOfBoundsException()
                        pre[offset].code
                    }
                    source != null && j < source.remaining -> source.get(source.position + j++).code
                    else -> {
                        if (!flush && i + j <= 19) return -(2 + i + j)
                        break
                    }
                }
                val found = find(length, c) { unit(b, chars + 2 * (index + 1 + it)) }
                if (found < 0) break
                value = word(b, values + 4 * (index + 1 + found))
                if (value ushr 24 == 0) index = value
                else {
                    if (value < 0 || CharsetEncoderRuntime.isFromUUseFallback(useFallback, firstCP)) {
                        matchValue = value; matchLength = 2 + i + j
                    }
                    break
                }
            }
            if (matchLength == 0) return 0
        } else {
            if (value >= 0 && !CharsetEncoderRuntime.isFromUUseFallback(useFallback, firstCP)) return 0
            matchValue = value; matchLength = 2
        }
        if (matchValue and 0x60000000 != 0) return 0
        if (matchValue == 0x80000001.toInt()) return 1
        valueOut[0] = matchValue and Int.MAX_VALUE
        return matchLength
    }
    internal inline fun find(length: Int, char: Int, unitAt: (Int) -> Int): Int {
        var start = 0; var limit = length
        while (true) {
            val distance = limit - start
            if (distance <= 1) break
            if (distance <= 4) {
                if (char <= unitAt(start)) break
                ++start; if (start < limit && char <= unitAt(start)) break
                ++start; if (start < limit && char <= unitAt(start)) break
                ++start; break
            }
            val middle = (start + limit) / 2
            if (char < unitAt(middle)) limit = middle else start = middle
        }
        return if (start < limit && char == unitAt(start)) start else -1
    }
    fun simpleMatch(extension: BinaryBuffer?, cp: Int, valueOut: IntArray, useFallback: Boolean): Int {
        val value = IntArray(1)
        val match = match(extension, cp, null, 0, 0, null, value, useFallback, true)
        if (match >= 2) {
            val roundtrip = value[0] < 0
            val length = (value[0] ushr 24) and 31
            if (length <= 3) { valueOut[0] = value[0] and 0xffffff; return if (roundtrip) length else -length }
        }
        return 0
    }
    fun write(encoder: CharsetEncoderRuntime, extension: BinaryBuffer?, value: Int, target: BinaryBuffer, offsets: CharsetIntBuffer?, sourceIndex: Int): CharsetCoderResult {
        var length = (value ushr 24) and 31
        val data = value and 0xffffff
        val buffer = ByteArray(32)
        var output: ByteArray; var begin: Int
        if (length <= 3) {
            for (i in 0 until length) buffer[i + 1] = (data ushr (8 * (length - 1 - i))).toByte()
            output = buffer; begin = 1
        } else {
            val b = extension!!
            val view = b.slice(word(b, 32))
            require(data in 0..view.limit)
            view.position = data
            if (length > view.remaining) throw CharsetBufferUnderflowException()
            output = view.readBytes(length); begin = 0
        }
        val previous = encoder.fromUnicodeStatus
        if (previous != 0) {
            val shift = when {
                previous > 1 && length == 1 -> { encoder.fromUnicodeStatus = 1; 0xf }
                previous == 1 && length > 1 -> { encoder.fromUnicodeStatus = 2; 0xe }
                else -> 0
            }
            if (shift != 0) {
                buffer[0] = shift.toByte()
                if (output !== buffer || begin != 1) output.copyInto(buffer, 1, begin, begin + length)
                output = buffer; begin = 0; ++length
            }
        }
        return CharsetEncoderRuntime.fromUWriteBytes(encoder, output, begin, length, target, offsets, sourceIndex)
    }
}
