// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2013, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

/** ICU UTF-16/32 encoder loops share BOM/pending flow and retain distinct unit serialization. */
internal class CharsetUnicodeEncoder(
    private val utf32: Boolean = false,
    bigEndian: Boolean? = null,
    private val version: Int = 0,
) : CharsetEncoderRuntime(if (utf32) 4 else 2, replacement(utf32, bigEndian != false)) {
    val temp = ByteArray(4)
    private val endianXor = if (bigEndian != false) 0 else if (utf32) 3 else 1
    private val bom = if (utf32) if (bigEndian != false) byteArrayOf(0, 0, -2, -1) else byteArrayOf(-1, -2, 0, 0)
        else if (bigEndian != false) byteArrayOf(-2, -1) else byteArrayOf(-1, -2)
    private val initialStatus = if (bigEndian == null || (!utf32 && version == 1)) 1 else 0
    // Unlike UTF-8/ASCII, the source constructors do not call implReset().
    init { fromUnicodeStatus = initialStatus }
    override fun reset() { super.reset(); fromUnicodeStatus = initialStatus }
    override fun encodeLoop(source: CharsetCharBuffer, target: BinaryBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
        if (fromUnicodeStatus == 1) {
            if (target.remaining == 0) return CharsetCoderResult.OVERFLOW
            fromUnicodeStatus = 0
            val result = fromUWriteBytes(this, bom, 0, bom.size, target, offsets, -1)
            if (result.isOverflow()) return result
        }
        if (fromUChar32 != 0) {
            if (target.remaining == 0) return CharsetCoderResult.OVERFLOW
            val result = encodeChar(source, target, offsets, fromUChar32.toChar())
            if (result != null) return result
        }
        while (true) {
            if (!source.hasRemaining()) return CharsetCoderResult.UNDERFLOW
            if (target.remaining == 0) return CharsetCoderResult.OVERFLOW
            val result = encodeChar(source, target, offsets, source.get())
            if (result != null) return result
        }
    }
    private fun encodeChar(source: CharsetCharBuffer, target: BinaryBuffer, offsets: CharsetIntBuffer?, ch: Char): CharsetCoderResult? {
        val index = source.position - 1
        val surrogate = ch.code in 0xd800..0xdfff
        if (surrogate) {
            val result = handleSurrogates(source, ch)
            if (result != null) return result
        }
        val length: Int
        if (utf32) {
            val cp = if (surrogate) fromUChar32 else ch.code
            if (surrogate) fromUChar32 = 0
            // The high byte remains zero; source never rewrites it, including after reset.
            temp[1 xor endianXor] = (cp ushr 16).toByte()
            temp[2 xor endianXor] = (cp ushr 8).toByte()
            temp[3 xor endianXor] = cp.toByte()
            length = 4
        } else {
            temp[0 xor endianXor] = (ch.code ushr 8).toByte(); temp[1 xor endianXor] = ch.code.toByte()
            if (surrogate) {
                val trail = ((fromUChar32 and 1023) + 0xdc00)
                fromUChar32 = 0
                temp[2 xor endianXor] = (trail ushr 8).toByte(); temp[3 xor endianXor] = trail.toByte()
                length = 4
            } else length = 2
        }
        val result = fromUWriteBytes(this, temp, 0, length, target, offsets, index)
        return if (result.isUnderflow()) null else result
    }
    companion object {
        private fun replacement(utf32: Boolean, big: Boolean): ByteArray =
            if (utf32) if (big) byteArrayOf(0, 0, -1, -3) else byteArrayOf(-3, -1, 0, 0)
            else if (big) byteArrayOf(-1, -3) else byteArrayOf(-3, -1)
    }
}
