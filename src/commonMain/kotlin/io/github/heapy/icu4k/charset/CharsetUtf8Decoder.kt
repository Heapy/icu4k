// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

/** ICU UTF-8/CESU-8 streaming algorithm, sharing the common callback/replay runtime. */
internal class CharsetUtf8Decoder(private val cesu8: Boolean = false) : CharsetDecoderRuntime(3) {
    override fun decodeLoop(source: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
        if (source.remaining == 0) return CharsetCoderResult.UNDERFLOW
        if (!target.hasRemaining()) return CharsetCoderResult.OVERFLOW
        var input = source.position
        var output = target.position
        var scalar: Int
        var expected: Int
        var count: Int
        if (mode == 0) {
            val byte = source.getByte(input++)
            toUBytesArray[0] = byte
            expected = UTF8.countBytes(byte)
            scalar = byte.toInt() and masks[expected]
            count = 1
        } else {
            scalar = toUnicodeStatus; expected = mode; count = toULength
            toUnicodeStatus = 0; mode = 0; toULength = 0
        }
        val result: CharsetCoderResult
        outer@ while (true) {
            if (count < expected) {
                if (input >= source.limit) {
                    toUnicodeStatus = scalar; mode = expected; toULength = count
                    result = CharsetCoderResult.UNDERFLOW
                    break
                }
                val byte = source.getByte(input++)
                toUBytesArray[count] = byte
                if (!UTF8.isValidTrail(scalar, byte, count, expected) && !(cesu8 && count == 1 && scalar == 13 && UTF8.isTrail(byte))) {
                    input--; toULength = count
                    result = CharsetCoderResult.malformedForLength(count)
                    break
                }
                scalar = (scalar shl 6) or (byte.toInt() and 63)
                count++
            } else if (count == expected && (!cesu8 || count <= 3)) {
                if (scalar <= 0xffff) target.put(output++, scalar.toChar()) else {
                    scalar -= 0x10000
                    target.put(output++, ((scalar ushr 10) + 0xd800).toChar())
                    if (output >= target.limit) {
                        charErrorBufferArray[charErrorBufferLength++] = ((scalar and 1023) + 0xdc00).toChar()
                        result = CharsetCoderResult.OVERFLOW
                        break
                    }
                    target.put(output++, ((scalar and 1023) + 0xdc00).toChar())
                }
                if (input >= source.limit) { result = CharsetCoderResult.UNDERFLOW; break }
                if (output >= target.limit) { result = CharsetCoderResult.OVERFLOW; break }
                var byte = source.getByte(input++)
                while (UTF8.isSingle(byte)) {
                    target.put(output++, byte.toInt().toChar())
                    if (input >= source.limit) { result = CharsetCoderResult.UNDERFLOW; break@outer }
                    if (output >= target.limit) { result = CharsetCoderResult.OVERFLOW; break@outer }
                    byte = source.getByte(input++)
                }
                toUBytesArray[0] = byte
                expected = UTF8.countBytes(byte)
                scalar = byte.toInt() and masks[expected]
                count = 1
            } else {
                toULength = count
                result = CharsetCoderResult.malformedForLength(count)
                break
            }
        }
        source.position = input
        target.position = output
        // ICU's UTF-8 loop ignores offsets; only its error callbacks fill them.
        return result
    }
    companion object { private val masks = intArrayOf(-1, 0x7f, 0x1f, 15, 7) }
}
