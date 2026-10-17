// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2008, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

internal class CharsetUtf32Decoder(bigEndian: Boolean? = null) : CharsetDecoderRuntime(4) {
    private val specified = bigEndian != null
    private val big = bigEndian != false
    private val endianXOR = if (big) 0 else 3
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
                    else if ((!specified || !big) && toUBytesArray[0] == BOM_LE[0]) { actualBOM = BOM_LE; actualEndianXOR = 3 }
                    else { actualBOM = null; actualEndianXOR = endianXOR; break }
                } else if (toUBytesArray[toULength - 1] != actualBOM!![toULength - 1]) { actualBOM = null; actualEndianXOR = endianXOR; break }
                else if (toULength == 4) { toULength = 0; break }
            }
            isBOMReadYet = true
        }
        while (true) {
            while (toULength < 4) {
                if (source.remaining == 0) return CharsetCoderResult.UNDERFLOW
                toUBytesArray[toULength++] = source.readByte()
            }
            if (!target.hasRemaining()) return CharsetCoderResult.OVERFLOW
            var scalar = 0
            for (i in 0..3) scalar = (scalar shl 8) or (toUBytesArray[i xor actualEndianXOR].toInt() and 255)
            if (scalar !in 0..0x10ffff || scalar in 0xd800..0xdfff) return CharsetCoderResult.malformedForLength(toULength)
            toULength = 0
            if (scalar <= 0xffff) target.put(scalar.toChar()) else {
                target.put(((scalar ushr 10) + 0xd7c0).toChar())
                val trail = ((scalar and 1023) + 0xdc00).toChar()
                if (target.hasRemaining()) target.put(trail) else {
                    charErrorBufferArray[0] = trail; charErrorBufferLength = 1
                    return CharsetCoderResult.OVERFLOW
                }
            }
        }
    }
    companion object {
        private val BOM_BE = byteArrayOf(0, 0, 0xfe.toByte(), 0xff.toByte())
        private val BOM_LE = byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0, 0)
    }
}
