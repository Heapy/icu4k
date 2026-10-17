// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2011, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

/** ICU's ASCII decoder and its Latin-1 core-loop specialization. */
internal class CharsetSingleByteDecoder(private val latin1: Boolean = false) : CharsetDecoderRuntime(1) {
    override fun decodeLoop(source: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
        if (source.remaining == 0) return CharsetCoderResult.UNDERFLOW
        if (!target.hasRemaining()) return CharsetCoderResult.OVERFLOW
        val oldSource = source.position
        val oldTarget = target.position
        val result: CharsetCoderResult
        if (source.hasArray && target.hasArray) {
            val input = source.bytes
            val output = target.array
            val start = oldSource + source.arrayOffset
            val sourceLength = source.limit - oldSource
            val targetLength = target.limit - oldTarget
            val limit = minOf(sourceLength, targetLength) + start
            val offset = oldTarget + target.arrayOffset - start
            var index = start
            var byte = 0
            while (index < limit) {
                byte = input[index].toInt() and 255
                if (!latin1 && (byte and 128) != 0) break
                output[index + offset] = byte.toChar()
                index++
            }
            if (!latin1 && (byte and 128) != 0) {
                // Preserve the source's absolute array-index cursor assignments on malformed input.
                val sourcePosition = index + 1
                require(sourcePosition in 0..source.limit)
                source.position = sourcePosition
                val targetPosition = index + offset
                require(targetPosition in 0..target.limit)
                target.position = targetPosition
                result = malformed(byte)
            } else if (sourceLength <= targetLength) {
                source.position = oldSource + sourceLength
                target.position = oldTarget + sourceLength
                result = CharsetCoderResult.UNDERFLOW
            } else {
                source.position = oldSource + targetLength
                target.position = oldTarget + targetLength
                result = CharsetCoderResult.OVERFLOW
            }
        } else {
            var current = CharsetCoderResult.UNDERFLOW
            while (source.remaining > 0) {
                val byte = source.readUnsignedByte()
                if (latin1 || (byte and 128) == 0) {
                    if (target.hasRemaining()) target.put(byte.toChar()) else { current = CharsetCoderResult.OVERFLOW; break }
                } else { current = malformed(byte); break }
            }
            if (current.isOverflow()) source.position--
            result = current
        }
        if (offsets != null) repeat(target.position - oldTarget) { offsets.put(it) }
        return result
    }
    private fun malformed(byte: Int): CharsetCoderResult {
        toUBytesArray[0] = byte.toByte()
        toULength = 1
        return CharsetCoderResult.malformedForLength(1)
    }
}
