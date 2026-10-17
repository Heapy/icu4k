// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2008, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

/** null selects BOM detection with the source's big-endian default. */
internal class CharsetUtf16Decoder(bigEndian: Boolean? = null, private val version: Int = 0) : CharsetDecoderRuntime(2) {
    private val specified = bigEndian != null
    private val big = bigEndian != false
    private val endianXOR = if (big) 0 else 1
    internal var isBOMReadYet = false
        private set
    internal var actualEndianXOR = 0
        private set
    internal var actualBOM: ByteArray? = null
        private set
    override fun reset() {
        super.reset(); isBOMReadYet = false; actualBOM = null
    }
    override fun decodeLoop(source: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
        if (!isBOMReadYet) {
            while (true) {
                if (source.remaining == 0) return CharsetCoderResult.UNDERFLOW
                toUBytesArray[toULength++] = source.readByte()
                if (toULength == 1) {
                    if ((!specified || big) && toUBytesArray[0] == BOM_BE[0]) { actualBOM = BOM_BE; actualEndianXOR = 0 }
                    else if ((!specified || !big) && toUBytesArray[0] == BOM_LE[0]) { actualBOM = BOM_LE; actualEndianXOR = 1 }
                    else {
                        if (specified && version == 1) { actualBOM = if (big) BOM_BE else BOM_LE; actualEndianXOR = endianXOR }
                        else { actualBOM = null; actualEndianXOR = endianXOR }
                        break
                    }
                } else if (specified && version == 1 && reversedBOM()) return CharsetCoderResult.malformedForLength(2)
                else if (specified && version == 1 && matchingBOM()) { toULength = 0; break }
                else if (specified || toUBytesArray[toULength - 1] != actualBOM!![toULength - 1]) { actualBOM = null; actualEndianXOR = endianXOR; break }
                else if (toULength == 2) { toULength = 0; break }
            }
            isBOMReadYet = true
        }
        if (toUnicodeStatus != 0) decodeTrail(source, target, toUnicodeStatus.toChar())?.let { return it }
        while (true) {
            while (toULength < 2) {
                if (source.remaining == 0) return CharsetCoderResult.UNDERFLOW
                toUBytesArray[toULength++] = source.readByte()
            }
            if (specified && version == 1 && reversedBOM()) return CharsetCoderResult.malformedForLength(2)
            else if (specified && version == 1 && matchingBOM()) { toULength = 0; continue }
            if (!target.hasRemaining()) return CharsetCoderResult.OVERFLOW
            val unit = unit(0)
            if (unit.code !in 0xd800..0xdfff) { toULength = 0; target.put(unit) }
            else decodeTrail(source, target, unit)?.let { return it }
        }
    }
    private fun unit(start: Int): Char = (((toUBytesArray[start xor actualEndianXOR].toInt() and 255) shl 8) or (toUBytesArray[(start + 1) xor actualEndianXOR].toInt() and 255)).toChar()
    private fun reversedBOM(): Boolean = toUBytesArray[toULength - 1] == actualBOM!![toULength - 2] && toUBytesArray[toULength - 2] == actualBOM!![toULength - 1]
    private fun matchingBOM(): Boolean = toUBytesArray[toULength - 1] == actualBOM!![toULength - 1] && toUBytesArray[toULength - 2] == actualBOM!![toULength - 2]
    private fun decodeTrail(source: BinaryBuffer, target: CharsetCharBuffer, lead: Char): CharsetCoderResult? {
        if (lead.code !in 0xd800..0xdbff) { toUnicodeStatus = 0; return CharsetCoderResult.malformedForLength(2) }
        while (toULength < 4) {
            if (source.remaining == 0) { toUnicodeStatus = lead.code; return CharsetCoderResult.UNDERFLOW }
            toUBytesArray[toULength++] = source.readByte()
        }
        val trail = unit(2)
        if (trail.code !in 0xdc00..0xdfff) {
            toULength = 2
            // ByteBuffer.position reports an argument error when this crosses a chunk's origin.
            val position = source.position - 2
            require(position in 0..source.limit)
            source.position = position
            toUnicodeStatus = 0
            return CharsetCoderResult.malformedForLength(2)
        }
        toUnicodeStatus = 0; toULength = 0
        target.put(lead)
        return if (target.hasRemaining()) { target.put(trail); null } else {
            charErrorBufferArray[0] = trail; charErrorBufferLength = 1; CharsetCoderResult.OVERFLOW
        }
    }
    companion object {
        private val BOM_BE = byteArrayOf(0xfe.toByte(), 0xff.toByte())
        private val BOM_LE = byteArrayOf(0xff.toByte(), 0xfe.toByte())
    }
}
