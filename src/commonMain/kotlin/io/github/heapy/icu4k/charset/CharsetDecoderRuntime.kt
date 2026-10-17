// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

internal fun interface CharsetDecoderCallback {
    fun call(decoder: CharsetDecoderRuntime, context: Any?, source: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, buffer: CharArray, length: Int, error: CharsetCoderResult): CharsetCoderResult
}

/** Shared ICU streaming state/error/replay machinery; a concrete decoder supplies decodeLoop. */
internal abstract class CharsetDecoderRuntime(val maxBytesPerChar: Int, val subChar1: Byte = 0, private val maxCharsPerByte: Float = 1f) {
    var toUnicodeStatus = 0
    val toUBytesArray = ByteArray(128)
    var toUBytesBegin = 0
    var toULength = 0
    val charErrorBufferArray = CharArray(128)
    var charErrorBufferLength = 0
    var charErrorBufferBegin = 0
    val invalidCharBuffer = CharArray(128)
    var invalidCharLength = 0
    val preToUArray = ByteArray(31)
    var preToUBegin = 0
    var preToULength = 0
    var preToUFirstLength = 0
    var mode = 0
    var toUContext: Any? = null
    private var malformedCallback = CharsetDecoderCallbacks.STOP
    private var unmappableCallback = CharsetDecoderCallbacks.STOP
    var malformedInputAction = CharsetCodingErrorAction.REPORT
        private set
    var unmappableCharacterAction = CharsetCodingErrorAction.REPORT
        private set
    var replacement = "\ufffd"
        private set
    fun replaceWith(value: String): CharsetDecoderRuntime {
        require(value.isNotEmpty() && value.length <= maxCharsPerByte)
        replacement = value
        return this
    }
    fun onMalformedInput(action: CharsetCodingErrorAction): CharsetDecoderRuntime {
        malformedInputAction = if (action == CharsetCodingErrorAction.REPLACE) CharsetCodingErrorAction.IGNORE else action
        malformedCallback = callback(action)
        return this
    }
    fun onUnmappableCharacter(action: CharsetCodingErrorAction): CharsetDecoderRuntime {
        unmappableCharacterAction = if (action == CharsetCodingErrorAction.REPLACE) CharsetCodingErrorAction.IGNORE else action
        unmappableCallback = callback(action)
        return this
    }
    fun setToUCallback(error: CharsetCoderResult, callback: CharsetDecoderCallback, context: Any?) {
        if (error.isMalformed()) malformedCallback = callback else if (error.isUnmappable()) unmappableCallback = callback
        if (toUContext == null || toUContext != context) toUContext = context
    }
    private fun callback(action: CharsetCodingErrorAction): CharsetDecoderCallback = when (action) {
        CharsetCodingErrorAction.REPLACE -> CharsetDecoderCallbacks.SUBSTITUTE
        CharsetCodingErrorAction.IGNORE -> CharsetDecoderCallbacks.SKIP
        CharsetCodingErrorAction.REPORT -> CharsetDecoderCallbacks.STOP
    }
    fun isFallbackUsed(): Boolean = true
    open fun reset() {
        toUnicodeStatus = 0; toULength = 0
        charErrorBufferLength = 0; charErrorBufferBegin = 0
        preToUBegin = 0; preToULength = 0; preToUFirstLength = 0; mode = 0
    }
    fun toUCountPending(): Int = when {
        preToULength > 0 -> preToULength
        preToULength < 0 -> -preToULength
        toULength > 0 -> toULength
        else -> 0
    }
    /** Source's NIO decodeLoop adapter, which keeps unconverted input visible to its caller. */
    fun decodeNioLoop(source: BinaryBuffer, target: CharsetCharBuffer): CharsetCoderResult {
        if (source.remaining < toUCountPending()) return CharsetCoderResult.UNDERFLOW
        source.position += toUCountPending()
        val result = decode(source, target, null, false)
        source.position -= toUCountPending()
        return result
    }
    fun flush(target: CharsetCharBuffer): CharsetCoderResult = decode(BinaryBuffer(byteArrayOf()), target, null, true)
    abstract fun decodeLoop(source: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult
    fun decode(source: BinaryBuffer?, target: CharsetCharBuffer?, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
        require(source != null && target != null)
        if (charErrorBufferLength > 0) {
            var i = 0
            do {
                if (!target.hasRemaining()) {
                    var j = 0
                    do { charErrorBufferArray[j++] = charErrorBufferArray[i++] } while (i < charErrorBufferLength)
                    charErrorBufferLength = j.toByte().toInt()
                    return CharsetCoderResult.OVERFLOW
                }
                target.put(charErrorBufferArray[i++]); offsets?.put(-1)
            } while (i < charErrorBufferLength)
            charErrorBufferLength = 0
        }
        if (!flush && source.remaining == 0 && toULength == 0 && preToULength >= 0) return CharsetCoderResult.UNDERFLOW
        return toUnicodeWithCallback(source, target, offsets, flush)
    }
    private fun toUnicodeWithCallback(input: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, flushing: Boolean): CharsetCoderResult {
        var source = input
        var flush = flushing
        var sourceIndex = 0
        val replay = BinaryBuffer(ByteArray(31))
        fun writeReplay(length: Int) {
            if (length < 0 || length > preToUArray.size) throw IndexOutOfBoundsException()
            if (length > replay.remaining) throw CharsetBufferOverflowException()
            repeat(length) { replay.writeByte(preToUArray[it].toInt()) }
        }
        var realSource: BinaryBuffer? = null
        var realFlush = false
        var realSourceIndex = 0
        if (preToULength < 0) {
            realSource = source; realFlush = flush; realSourceIndex = sourceIndex
            writeReplay(-preToULength)
            source = replay; source.position = 0; source.limit = -preToULength
            flush = false; sourceIndex = -1; preToULength = 0
        }
        while (true) {
            var result = decodeLoop(source, target, offsets, flush)
            val sawEnd = result.isUnderflow() && flush && source.remaining == 0 && toULength == 0
            var calledCallback = false
            while (true) {
                if (preToULength < 0) {
                    check(realSource == null) { "assert failed" }
                    realSource = source; realFlush = flush; realSourceIndex = sourceIndex
                    writeReplay(-preToULength)
                    replay.position = 0; source = replay; source.limit = -preToULength
                    flush = false; sourceIndex += preToULength
                    if (sourceIndex < 0) sourceIndex = -1
                    preToULength = 0
                }
                if (result.isUnderflow()) {
                    if (source.position < source.limit) break
                    else if (realSource != null) {
                        source = realSource; flush = realFlush; sourceIndex = realSourceIndex; realSource = null
                        break
                    } else if (flush && toULength > 0) {
                        result = CharsetCoderResult.malformedForLength(toULength)
                        calledCallback = false
                    } else {
                        if (flush) {
                            if (!sawEnd) break
                            reset()
                        }
                        return result
                    }
                }
                if (calledCallback || result.isOverflow() || (result.isMalformed() && result.isUnmappable())) {
                    if (realSource != null) {
                        check(preToULength == 0) { "assert failed" }
                        val length = source.remaining
                        if (length > 0) {
                            source.readBytes(length).copyInto(preToUArray, preToUBegin)
                            preToULength = (-length).toByte().toInt()
                        }
                    }
                    return result
                }
                invalidCharLength = toULength
                if (invalidCharLength > 0) repeat(invalidCharLength) { invalidCharBuffer[it] = (toUBytesArray[it].toInt() and 255).toChar() }
                toULength = 0
                result = (if (result.isUnmappable()) unmappableCallback else malformedCallback).call(this, toUContext, source, target, offsets, invalidCharBuffer, invalidCharLength, result)
                calledCallback = true
            }
        }
    }
    companion object {
        fun isToUUseFallback(fallback: Boolean = true): Boolean = true
        fun toUWriteUChars(decoder: CharsetDecoderRuntime, chars: CharArray, start: Int, count: Int, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, sourceIndex: Int): CharsetCoderResult {
            var position = start
            var remaining = count
            fun get(): Char {
                if (position !in chars.indices) throw IndexOutOfBoundsException()
                return chars[position++]
            }
            while (remaining > 0 && target.hasRemaining()) { target.put(get()); offsets?.put(sourceIndex); remaining-- }
            if (remaining > 0) {
                decoder.charErrorBufferLength = 0
                do {
                    val index = decoder.charErrorBufferLength++
                    if (index !in decoder.charErrorBufferArray.indices) throw IndexOutOfBoundsException()
                    decoder.charErrorBufferArray[index] = get()
                } while (--remaining > 0)
                return CharsetCoderResult.OVERFLOW
            }
            return CharsetCoderResult.UNDERFLOW
        }
    }
}
