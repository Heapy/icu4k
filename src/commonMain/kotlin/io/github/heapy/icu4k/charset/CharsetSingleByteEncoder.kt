// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2011, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

internal class CharsetSingleByteEncoder(private val latin1: Boolean = false) : CharsetEncoderRuntime(1, byteArrayOf(0x1a)) {
    init { reset() }
    override fun reset() { super.reset(); fromUnicodeStatus = 1 }
    override fun encodeLoop(source: CharsetCharBuffer, target: BinaryBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
        if (!source.hasRemaining()) return CharsetCoderResult.UNDERFLOW
        if (target.remaining == 0) return CharsetCoderResult.OVERFLOW
        val oldSource = source.position; val oldTarget = target.position
        val result: CharsetCoderResult
        val mask = if (latin1) 0xff00 else 0xff80
        if (fromUChar32 != 0) result = trail(source, fromUChar32.toChar())
        else if (source.hasArray && target.hasArray) {
            val start = oldSource + source.arrayOffset
            val sourceLength = source.limit - oldSource
            val targetLength = target.limit - oldTarget
            val limit = minOf(sourceLength, targetLength) + start
            val offset = oldTarget + target.arrayOffset - start
            var i = start; var c = 0
            val bytes = target.bytes
            while (i < limit) {
                c = source.array[i].code
                if (c and mask != 0) break
                bytes[i + offset] = c.toByte(); i++
            }
            if (c and mask != 0) {
                source.position = i + 1 - source.arrayOffset
                // Source omits subtraction of the target array offset on this error path.
                val position = i + offset
                require(position in 0..target.limit)
                target.position = position
                result = malformedOrUnmappable(source, c)
            } else if (sourceLength <= targetLength) {
                source.position = oldSource + sourceLength; target.position = oldTarget + sourceLength
                result = CharsetCoderResult.UNDERFLOW
            } else {
                source.position = oldSource + targetLength; target.position = oldTarget + targetLength
                result = CharsetCoderResult.OVERFLOW
            }
        } else {
            var current = CharsetCoderResult.UNDERFLOW
            while (source.hasRemaining()) {
                val c = source.get().code
                if (c and mask == 0) {
                    if (target.remaining > 0) target.putCharsetByte(c.toByte()) else { current = CharsetCoderResult.OVERFLOW; break }
                } else { current = malformedOrUnmappable(source, c); break }
            }
            if (current.isOverflow()) source.position--
            result = current
        }
        if (offsets != null) repeat(target.position - oldTarget) { offsets.put(it) }
        return result
    }
    private fun malformedOrUnmappable(source: CharsetCharBuffer, cp: Int): CharsetCoderResult =
        if (cp in 0xd800..0xdfff) trail(source, cp.toChar()) else CharsetCoderResult.unmappableForLength(1)
    private fun trail(source: CharsetCharBuffer, lead: Char): CharsetCoderResult =
        handleSurrogates(source, lead) ?: CharsetCoderResult.unmappableForLength(2)
}
