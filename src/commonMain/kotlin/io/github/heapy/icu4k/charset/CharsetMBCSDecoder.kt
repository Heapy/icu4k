// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

/** Table-driven ICU MBCS decoder, including extensions, SI/SO and GB18030 ranges. */
internal class CharsetMBCSDecoder(val converter: MBCSConverterData) :
    CharsetDecoderRuntime(converter.maxBytesPerChar, converter.subChar1) {
    private val table get() = converter.sharedData.mbcs
    private val states get() = if (converter.options and 0x10 != 0) table.swapLFNLStateTable!! else table.stateTable
    private fun state(entry: Int) = (entry ushr 24) and 127
    private fun action(entry: Int) = (entry ushr 20) and 15
    private fun direct(entry: Int) = entry < 0x80100000.toInt()
    private fun value16(entry: Int) = (entry and 65535).toChar()
    private fun siso() = when (table.outputType.toInt()) { 12 -> mode.toByte().toInt(); 0xdb -> 1; else -> -1 }

    private fun writeCodePoint(codePoint: Int, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, sourceIndex: Int): CharsetCoderResult {
        var c = codePoint
        val begin = target.position
        if (target.hasRemaining()) {
            if (c <= 65535) { target.put(c.toChar()); c = -1 }
            else {
                target.put(((c ushr 10) + 0xd7c0).toChar())
                c = ((c and 1023) + 0xdc00)
                if (target.hasRemaining()) { target.put(c.toChar()); c = -1 }
            }
            if (offsets != null) {
                offsets.put(sourceIndex)
                if (begin + 1 < target.position) offsets.put(sourceIndex)
            }
        }
        if (c >= 0) {
            if (c <= 65535) { charErrorBufferArray[0] = c.toChar(); charErrorBufferLength = 1 }
            else {
                charErrorBufferArray[0] = ((c ushr 10) + 0xd7c0).toChar()
                charErrorBufferArray[1] = ((c and 1023) + 0xdc00).toChar()
                charErrorBufferLength = 2
            }
            return CharsetCoderResult.OVERFLOW
        }
        return CharsetCoderResult.UNDERFLOW
    }
    private fun writeExtension(value: Int, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, sourceIndex: Int): CharsetCoderResult =
        if (value <= 0x2fffff) writeCodePoint(value - 0x1f0000, target, offsets, sourceIndex)
        else MBCSToUnicodeExtension.characters(table.extIndexes!!, value).let {
            toUWriteUChars(this, it, 0, it.size, target, offsets, sourceIndex)
        }

    private fun continueExtension(source: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, sourceIndex: Int, flush: Boolean): CharsetCoderResult {
        val value = IntArray(1)
        var match = MBCSToUnicodeExtension.match(table.extIndexes, siso(), preToUArray, preToUBegin, preToULength, source, value, flush)
        if (match > 0) {
            if (match >= preToULength) { source.position += match - preToULength; preToULength = 0 }
            else {
                val length = preToULength - match
                preToUArray.copyInto(preToUArray, preToUBegin, preToUBegin + match, preToUBegin + match + length)
                preToULength = (-length).toByte().toInt()
            }
            return writeExtension(value[0], target, offsets, sourceIndex)
        } else if (match < 0) {
            var sourcePosition = source.position
            match = -match
            for (j in preToULength until match) preToUArray[j] = source.getByte(sourcePosition++)
            source.position = sourcePosition
            preToULength = match.toByte().toInt()
            return CharsetCoderResult.UNDERFLOW
        }
        preToUArray.copyInto(toUBytesArray, toUBytesBegin, preToUBegin, preToUBegin + preToUFirstLength)
        toULength = preToUFirstLength
        val length = preToULength - preToUFirstLength
        if (length > 0) preToUArray.copyInto(preToUArray, preToUBegin, preToUBegin + preToUFirstLength, preToUBegin + preToUFirstLength + length)
        preToULength = (-length).toByte().toInt()
        return CharsetCoderResult.unmappableForLength(preToUFirstLength)
    }
    private data class Conversion(val length: Int, val result: CharsetCoderResult)
    private fun unassigned(length: Int, source: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, sourceIndex: Int, flush: Boolean): Conversion {
        if (table.extIndexes != null) {
            val value = IntArray(1)
            var match = MBCSToUnicodeExtension.match(table.extIndexes, siso(), toUBytesArray, toUBytesBegin, length, source, value, flush)
            if (match > 0) {
                val position = source.position + match - length
                require(position in 0..source.limit)
                source.position = position
                return Conversion(0, writeExtension(value[0], target, offsets, sourceIndex))
            } else if (match < 0) {
                preToUFirstLength = length.toByte().toInt()
                for (j in 0 until length) preToUArray[j] = toUBytesArray[toUBytesBegin + j]
                var sourcePosition = source.position
                match = -match
                for (j in length until match) preToUArray[j] = source.getByte(sourcePosition++)
                source.position = sourcePosition
                preToULength = match.toByte().toInt()
                return Conversion(0, CharsetCoderResult.UNDERFLOW)
            }
        }
        if (length == 4 && converter.options and 0x8000 != 0) {
            val linear = linear(toUBytesArray[0].toInt(), toUBytesArray[1].toInt(), toUBytesArray[2].toInt(), toUBytesArray[3].toInt())
            for (range in gb18030Ranges) if (linear in range[1]..range[2])
                return Conversion(0, writeCodePoint(range[0] + linear - range[1], target, offsets, sourceIndex))
        }
        return Conversion(length, CharsetCoderResult.unmappableForLength(length))
    }

    override fun decodeLoop(source: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
        var result = CharsetCoderResult.UNDERFLOW
        if (preToULength > 0) {
            result = continueExtension(source, target, offsets, -1, flush)
            if (result.isError() || preToULength < 0) return result
        }
        if (table.countStates.toInt() == 1) return if (table.hasSupplementary()) singleUnicode(source, target, offsets, flush) else singleBMP(source, target, offsets, flush)
        var sourcePosition = source.position
        val sourceStart = sourcePosition
        val states = states
        val units = table.unicodeCodeUnits
        var offset = toUnicodeStatus
        var byteIndex = toULength
        val bytes = toUBytesArray
        var state = mode.toByte().toInt()
        if (state == 0) state = table.dbcsOnlyState.toInt()
        var sourceIndex = if (byteIndex == 0) 0 else -1
        var nextSourceIndex = 0
        var entry = 0
        while (sourcePosition < source.limit) {
            if (!target.hasRemaining()) { result = CharsetCoderResult.OVERFLOW; break }
            if (byteIndex == 0) {
                do {
                    entry = states[state][source.getByte(sourcePosition).toInt() and 255]
                    if (entry >= 0) {
                        state = entry ushr 24
                        offset = entry and 0xffffff
                        ++sourcePosition
                        if (sourcePosition < source.limit) entry = states[state][source.getByte(sourcePosition).toInt() and 255]
                        if (sourcePosition < source.limit && entry < 0 && action(entry) == 4 && units[offset + (entry and 65535)].code < 0xfffe) {
                            ++sourcePosition
                            target.put(units[offset + (entry and 65535)])
                            if (offsets != null) { offsets.put(sourceIndex); nextSourceIndex += 2; sourceIndex = nextSourceIndex }
                            state = state(entry); offset = 0
                        } else {
                            ++nextSourceIndex; bytes[0] = source.getByte(sourcePosition - 1); byteIndex = 1; break
                        }
                    } else if (direct(entry)) {
                        ++sourcePosition; target.put(value16(entry))
                        if (offsets != null) { offsets.put(sourceIndex); sourceIndex = ++nextSourceIndex }
                        state = state(entry)
                    } else break
                } while (sourcePosition < source.limit && target.hasRemaining())
                if (sourcePosition >= source.limit) break
                if (!target.hasRemaining()) { result = CharsetCoderResult.OVERFLOW; break }
                ++nextSourceIndex; bytes[byteIndex++] = source.getByte(sourcePosition++)
            } else {
                ++nextSourceIndex
                bytes[byteIndex++] = source.getByte(sourcePosition++)
                entry = states[state][bytes[byteIndex - 1].toInt() and 255]
            }
            if (entry >= 0) { state = entry ushr 24; offset += entry and 0xffffff; continue }
            mode = state
            state = state(entry)
            when (action(entry)) {
                4 -> {
                    offset += entry and 65535
                    val c = units[offset]
                    if (c.code < 0xfffe) { target.put(c); offsets?.put(sourceIndex); byteIndex = 0 }
                    else if (c.code == 0xfffe) {
                        entry = fallback(offset)
                        if (entry != 0xfffe) { target.put(entry.toChar()); offsets?.put(sourceIndex); byteIndex = 0 }
                    } else result = CharsetCoderResult.malformedForLength(byteIndex)
                }
                0 -> { target.put(value16(entry)); offsets?.put(sourceIndex); byteIndex = 0 }
                5 -> {
                    offset += entry and 65535
                    val c = units[offset++].code
                    when {
                        c < 0xd800 -> { target.put(c.toChar()); offsets?.put(sourceIndex); byteIndex = 0 }
                        c <= 0xdfff -> {
                            target.put((c and 0xdbff).toChar()); offsets?.put(sourceIndex); byteIndex = 0
                            if (target.hasRemaining()) { target.put(units[offset]); offsets?.put(sourceIndex) }
                            else { charErrorBufferArray[0] = units[offset]; charErrorBufferLength = 1; result = CharsetCoderResult.OVERFLOW; offset = 0; break }
                        }
                        (c and 0xfffe) == 0xe000 -> { target.put(units[offset]); offsets?.put(sourceIndex); byteIndex = 0 }
                        c == 0xffff -> result = CharsetCoderResult.malformedForLength(byteIndex)
                    }
                }
                1, 3 -> {
                    entry = entry and 0xfffff
                    target.put((0xd800 or (entry shr 10)).toChar()); offsets?.put(sourceIndex); byteIndex = 0
                    val c = (0xdc00 or (entry and 1023)).toChar()
                    if (target.hasRemaining()) { target.put(c); offsets?.put(sourceIndex) }
                    else { charErrorBufferArray[0] = c; charErrorBufferLength = 1; result = CharsetCoderResult.OVERFLOW; offset = 0; break }
                }
                8 -> if (table.dbcsOnlyState.toInt() == 0) byteIndex = 0 else { state = mode.toByte().toInt(); result = CharsetCoderResult.malformedForLength(byteIndex) }
                2 -> { target.put(value16(entry)); offsets?.put(sourceIndex); byteIndex = 0 }
                6 -> Unit
                7 -> result = CharsetCoderResult.malformedForLength(byteIndex)
                else -> byteIndex = 0
            }
            offset = 0
            if (byteIndex == 0) sourceIndex = nextSourceIndex
            else if (result.isError()) {
                if (byteIndex > 1) {
                    var i = 1
                    while (i < byteIndex && !isSingleOrLead(states, state, table.dbcsOnlyState.toInt() != 0, bytes[i].toInt() and 255)) ++i
                    if (i < byteIndex) {
                        val distance = (byteIndex - i).toByte().toInt()
                        val fromThisBuffer = sourcePosition - sourceStart
                        byteIndex = i
                        if (distance <= fromThisBuffer) sourcePosition -= distance
                        else {
                            preToULength = (fromThisBuffer - distance).toByte().toInt()
                            for (n in 0 until -preToULength) preToUArray[n] = bytes[i + n]
                            sourcePosition = sourceStart
                        }
                    }
                }
                break
            } else {
                val begin = sourcePosition
                source.position = sourcePosition
                val converted = unassigned(byteIndex, source, target, offsets, sourceIndex, flush)
                byteIndex = converted.length; result = converted.result
                sourcePosition = source.position
                nextSourceIndex += sourcePosition - begin; sourceIndex = nextSourceIndex
                if (result.isError() || result.isOverflow()) break
            }
        }
        toUnicodeStatus = offset; mode = state; toULength = byteIndex; source.position = sourcePosition
        return result
    }

    private fun singleBMP(source: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
        var result = CharsetCoderResult.UNDERFLOW
        var sourcePosition = source.position
        val states = states
        var sourceIndex = 0
        var lastSource = sourcePosition
        var capacity = minOf(target.remaining, source.remaining)
        while (capacity > 0 && sourcePosition < source.limit) {
            val entry = states[0][source.getByte(sourcePosition++).toInt() and 255]
            if (direct(entry) || action(entry) == 2) { target.put(value16(entry)); --capacity; continue }
            when (action(entry)) {
                6 -> Unit
                7 -> result = CharsetCoderResult.malformedForLength(sourcePosition - lastSource)
                else -> continue
            }
            if (offsets != null) {
                var count = sourcePosition - lastSource
                while (--count > 0) offsets.put(sourceIndex++)
            }
            if (result.isError()) break
            lastSource = sourcePosition
            toUBytesArray[0] = source.getByte(sourcePosition - 1)
            source.position = sourcePosition
            val converted = unassigned(1, source, target, offsets, sourceIndex, flush)
            toULength = converted.length; result = converted.result
            sourcePosition = source.position
            sourceIndex += 1 + sourcePosition - lastSource
            if (result.isError()) break
            capacity = minOf(target.remaining, source.remaining)
        }
        if (!result.isError() && sourcePosition < source.limit && !target.hasRemaining()) result = CharsetCoderResult.OVERFLOW
        if (offsets != null) {
            var count = sourcePosition - lastSource
            while (count-- > 0) offsets.put(sourceIndex++)
        }
        source.position = sourcePosition
        return result
    }
    private fun singleUnicode(source: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
        var result = CharsetCoderResult.UNDERFLOW
        var sourcePosition = source.position
        val states = states
        var sourceIndex = 0
        while (sourcePosition < source.limit) {
            if (!target.hasRemaining()) { result = CharsetCoderResult.OVERFLOW; break }
            var entry = states[0][source.getByte(sourcePosition++).toInt() and 255]
            if (direct(entry)) { target.put(value16(entry)); offsets?.put(sourceIndex); ++sourceIndex; continue }
            when (action(entry)) {
                1, 3 -> {
                    entry = entry and 0xfffff
                    target.put((0xd800 or (entry ushr 10)).toChar()); offsets?.put(sourceIndex)
                    val c = (0xdc00 or (entry and 1023)).toChar()
                    if (target.hasRemaining()) { target.put(c); offsets?.put(sourceIndex) }
                    else { charErrorBufferArray[0] = c; charErrorBufferLength = 1; result = CharsetCoderResult.OVERFLOW; break }
                    ++sourceIndex; continue
                }
                2 -> { target.put(value16(entry)); offsets?.put(sourceIndex); ++sourceIndex; continue }
                6 -> Unit
                7 -> result = CharsetCoderResult.malformedForLength(1)
                else -> { ++sourceIndex; continue }
            }
            if (result.isError()) break
            val begin = sourcePosition
            toUBytesArray[0] = source.getByte(sourcePosition - 1); source.position = sourcePosition
            val converted = unassigned(1, source, target, offsets, sourceIndex, flush)
            toULength = converted.length; result = converted.result
            sourcePosition = source.position
            sourceIndex += 1 + sourcePosition - begin
            if (result.isError()) break
        }
        source.position = sourcePosition
        return result
    }
    /** Source's single-character table lookup; deliberately excludes swaplfnl and GB18030 ranges. */
    fun simpleGetNextUChar(source: BinaryBuffer, useFallback: Boolean): Int {
        val states = table.stateTable
        val units = table.unicodeCodeUnits
        var offset = 0
        var state = table.dbcsOnlyState.toInt()
        var position = source.position
        val length = source.limit - position
        var c: Int
        while (true) {
            val entry = states[state][source.getByte(position++).toInt() and 255]
            if (entry >= 0) {
                state = entry ushr 24; offset += entry and 0xffffff
                if (position == source.limit) return 0xffff
            } else {
                c = when (action(entry)) {
                    4 -> {
                        offset += entry and 65535
                        val value = units[offset].code
                        if (value == 0xfffe) fallback(offset) else value
                    }
                    0, 2 -> entry and 65535
                    5 -> {
                        offset += entry and 65535
                        val value = units[offset++].code
                        when {
                            value < 0xd800 -> value
                            value <= 0xdfff -> ((value and 1023) shl 10) + units[offset].code + 0x10000 - 0xdc00
                            (value and 0xfffe) == 0xe000 -> units[offset].code
                            value == 0xffff -> return 0xffff
                            else -> 0xfffe
                        }
                    }
                    1, 3 -> 0x10000 + (entry and 0xfffff)
                    6 -> 0xfffe
                    else -> return 0xffff
                }
                break
            }
        }
        if (position != source.limit) return 0xffff
        if (c == 0xfffe && table.extIndexes != null) {
            if (source.limit > position + length) source.limit = position + length
            return simpleExtension(source, useFallback)
        }
        return c
    }
    private fun simpleExtension(source: BinaryBuffer, useFallback: Boolean): Int {
        if (source.remaining <= 0) return 0xffff
        val input: ByteArray
        val position: Int
        val length: Int
        if (source.isReadOnly) {
            val saved = source.position
            input = source.readBytes(minOf(source.remaining, 31))
            source.position = saved
            position = 0; length = input.size
        } else {
            // ICU4J uses array() with the cursor/limit directly, without adding arrayOffset.
            input = source.bytes; position = source.position; length = source.limit
        }
        val value = IntArray(1)
        val match = MBCSToUnicodeExtension.match(table.extIndexes, -1, input, position, length, null, value, true)
        if (match == source.remaining && value[0] <= 0x2fffff) return value[0] - 0x1f0000
        return 0xfffe
    }
    private fun fallback(offset: Int): Int {
        var limit = table.countToUFallbacks
        if (limit > 0) {
            var start = 0
            while (start < limit - 1) {
                val i = (start + limit) ushr 1
                if (offset < table.toUFallbacks[i].offset) limit = i else start = i
            }
            if (offset == table.toUFallbacks[start].offset) return table.toUFallbacks[start].codePoint
        }
        return 0xfffe
    }
    private fun hasValidTrailBytes(states: Array<IntArray>, state: Int): Boolean {
        val row = states[state]
        for (b in intArrayOf(0xa1, 0x41)) if (row[b] < 0 && action(row[b]) != 7) return true
        for (entry in row) if (entry < 0 && action(entry) != 7) return true
        for (entry in row) if (entry >= 0 && hasValidTrailBytes(states, entry ushr 24)) return true
        return false
    }
    private fun isSingleOrLead(states: Array<IntArray>, state: Int, dbcsOnly: Boolean, byte: Int): Boolean {
        val entry = states[state][byte]
        if (entry >= 0) return hasValidTrailBytes(states, entry ushr 24)
        val action = action(entry)
        return !(action == 8 && dbcsOnly) && action != 7
    }
    companion object {
        private fun linear(a: Int, b: Int, c: Int, d: Int) = ((((a and 255) * 10 + (b and 255)) * 126 + (c and 255)) * 10 + (d and 255))
        private fun linear(packed: Long) = linear((packed ushr 24).toInt(), (packed ushr 16).toInt(), (packed ushr 8).toInt(), packed.toInt())
        private val gb18030Ranges = arrayOf(
            intArrayOf(0x10000, linear(0x90308130L), linear(0xe3329a35L)),
            intArrayOf(0x9fa6, linear(0x82358f33L), linear(0x8336c738L)),
            intArrayOf(0x0452, linear(0x8130d330L), linear(0x8135f436L)),
            intArrayOf(0x1e40, linear(0x8135f438L), linear(0x8136a531L)),
            intArrayOf(0xe865, linear(0x8336d030L), linear(0x84308534L)),
            intArrayOf(0x2643, linear(0x8137a839L), linear(0x8138fd38L)),
            intArrayOf(0xfa2a, linear(0x84309c38L), linear(0x84318537L)),
            intArrayOf(0x3ce1, linear(0x8231d438L), linear(0x8232af32L)),
            intArrayOf(0x361b, linear(0x8230a633L), linear(0x8230f237L)),
            intArrayOf(0x49b8, linear(0x8234a131L), linear(0x8234e733L)),
            intArrayOf(0x4160, linear(0x8232c937L), linear(0x8232f837L)),
            intArrayOf(0x478e, linear(0x8233e838L), linear(0x82349638L)),
            intArrayOf(0x44d7, linear(0x8233a339L), linear(0x8233c931L)),
            intArrayOf(0xffe6, linear(0x8431a234L), linear(0x8431a439L)),
        )
    }
}
