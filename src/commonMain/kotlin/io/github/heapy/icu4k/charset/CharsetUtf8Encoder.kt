// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2013, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

internal class CharsetUtf8Encoder(private val cesu8: Boolean = false) :
    CharsetEncoderRuntime(3, byteArrayOf(0xef.toByte(), 0xbf.toByte(), 0xbd.toByte())) {
    var sourceIndex = 0
        private set
    var targetIndex = 0
        private set
    init { reset() }
    override fun encodeLoop(source: CharsetCharBuffer, target: BinaryBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
        if (!source.hasRemaining()) return CharsetCoderResult.UNDERFLOW
        if (target.remaining == 0) return CharsetCoderResult.OVERFLOW
        val array = source.hasArray && target.hasArray
        val input = source.array
        val output = if (array) target.bytes else null
        var s = source.arrayOffset + source.position
        var t = target.arrayOffset + target.position
        val sourceLimit = source.arrayOffset + source.limit
        val targetLimit = target.arrayOffset + target.limit
        fun remainingSource() = if (array) s < sourceLimit else source.hasRemaining()
        fun remainingTarget() = if (array) t < targetLimit else target.remaining > 0
        fun read(): Int = if (array) input[s++].code else source.get().code
        fun put(value: Byte) { if (array) output!![t++] = value else target.putCharsetByte(value) }
        fun queue(value: Byte) {
            val index = errorBufferLength++
            if (index !in errorBuffer.indices) throw IndexOutOfBoundsException()
            errorBuffer[index] = value
        }
        fun pair(lead: Int): CharsetCoderResult? {
            val result = if (array) {
                sourceIndex = s; targetIndex = t
                handleSurrogates(input, sourceIndex, sourceLimit, lead.toChar())
            } else handleSurrogates(source, lead.toChar())
            if (result != null) return result
            if (array) { sourceIndex++; s = sourceIndex }
            val cp = fromUChar32; fromUChar32 = 0
            put((0xf0 or (cp ushr 18)).toByte())
            if (array) targetIndex = t
            if (!remainingTarget()) {
                queue(tail(cp, 12)); queue(tail(cp, 6)); queue(tail(cp, 0)); return CharsetCoderResult.OVERFLOW
            }
            put(tail(cp, 12)); if (array) targetIndex = t
            if (!remainingTarget()) { queue(tail(cp, 6)); queue(tail(cp, 0)); return CharsetCoderResult.OVERFLOW }
            put(tail(cp, 6)); if (array) targetIndex = t
            if (!remainingTarget()) { queue(tail(cp, 0)); return CharsetCoderResult.OVERFLOW }
            put(tail(cp, 0)); if (array) targetIndex = t
            return null
        }
        fun finish(result: CharsetCoderResult): CharsetCoderResult {
            if (array) { source.position = s - source.arrayOffset; target.position = t - target.arrayOffset }
            return result
        }
        if (fromUChar32 != 0) {
            val result = pair(fromUChar32)
            if (result != null) return finish(result)
        }
        while (true) {
            if (!remainingSource()) return finish(CharsetCoderResult.UNDERFLOW)
            if (!remainingTarget()) return finish(CharsetCoderResult.OVERFLOW)
            val cp = read()
            when {
                cp <= 0x7f -> put(cp.toByte())
                cp <= 0x7ff -> {
                    put((0xc0 or (cp ushr 6)).toByte())
                    if (!remainingTarget()) { queue(tail(cp, 0)); return finish(CharsetCoderResult.OVERFLOW) }
                    put(tail(cp, 0))
                }
                cp !in 0xd800..0xdfff || cesu8 -> {
                    put((0xe0 or (cp ushr 12)).toByte())
                    if (!remainingTarget()) { queue(tail(cp, 6)); queue(tail(cp, 0)); return finish(CharsetCoderResult.OVERFLOW) }
                    put(tail(cp, 6))
                    if (!remainingTarget()) { queue(tail(cp, 0)); return finish(CharsetCoderResult.OVERFLOW) }
                    put(tail(cp, 0))
                }
                else -> { val result = pair(cp); if (result != null) return finish(result) }
            }
        }
    }
    private fun tail(cp: Int, shift: Int) = (0x80 or ((cp ushr shift) and 63)).toByte()
}
