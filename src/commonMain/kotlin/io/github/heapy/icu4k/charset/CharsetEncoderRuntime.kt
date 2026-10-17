// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2013, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

internal fun interface CharsetEncoderCallback {
    fun call(encoder: CharsetEncoderRuntime, context: Any?, source: CharsetCharBuffer, target: BinaryBuffer, offsets: CharsetIntBuffer?, buffer: CharArray, length: Int, codePoint: Int, error: CharsetCoderResult): CharsetCoderResult
}

/** Relative codec output with NIO exception semantics over the common binary cursor. */
internal fun BinaryBuffer.putCharsetByte(value: Byte) {
    if (isReadOnly) throw CharsetReadOnlyBufferException()
    if (remaining == 0) throw CharsetBufferOverflowException()
    writeByte(value.toInt())
}

/** ICU from-Unicode state, callbacks, replay, overflow and protected NIO-loop adapter. */
internal abstract class CharsetEncoderRuntime(
    val maxBytesPerChar: Int, replacement: ByteArray, val subChar1: Byte = 0,
    val maxCharsPerByte: Float = 1f,
) {
    var errorBuffer = ByteArray(30)
    var errorBufferLength = 0
    var fromUnicodeStatus = 0
    var fromUChar32 = 0
    var useSubChar1 = false
    var useFallback = false
    var preFromUFirstCP = 0
    val preFromUArray = CharArray(19)
    var preFromUBegin = 0
    var preFromULength = 0
    val invalidUCharBuffer = CharArray(2)
    var invalidUCharLength = 0
    var fromUContext: Any? = null
    private var malformedCallback = CharsetEncoderCallbacks.STOP
    private var unmappableCallback = CharsetEncoderCallbacks.STOP
    var malformedInputAction = CharsetCodingErrorAction.REPORT
        private set
    var unmappableCharacterAction = CharsetCodingErrorAction.REPORT
        private set
    private var substitution = replacement.copyOf()
    val replacement: ByteArray get() = substitution.copyOf()
    init { require(replacement.isNotEmpty() && replacement.size <= maxBytesPerChar) }
    fun replaceWith(value: ByteArray): CharsetEncoderRuntime {
        require(value.isNotEmpty() && value.size <= maxBytesPerChar)
        substitution = value.copyOf()
        replacementChanged(substitution)
        return this
    }
    protected open fun replacementChanged(value: ByteArray) {}
    fun isLegalReplacement(value: ByteArray?): Boolean = true
    fun isFallbackUsed(): Boolean = useFallback
    fun setFallbackUsed(value: Boolean) { useFallback = value }
    fun isFromUUseFallback(codePoint: Int): Boolean = isFromUUseFallback(useFallback, codePoint)
    fun onMalformedInput(action: CharsetCodingErrorAction): CharsetEncoderRuntime {
        malformedInputAction = action; malformedCallback = callback(action); return this
    }
    fun onUnmappableCharacter(action: CharsetCodingErrorAction): CharsetEncoderRuntime {
        unmappableCharacterAction = action; unmappableCallback = callback(action); return this
    }
    private fun callback(action: CharsetCodingErrorAction) = when (action) {
        CharsetCodingErrorAction.REPORT -> CharsetEncoderCallbacks.STOP
        CharsetCodingErrorAction.IGNORE -> CharsetEncoderCallbacks.SKIP
        CharsetCodingErrorAction.REPLACE -> CharsetEncoderCallbacks.SUBSTITUTE
    }
    fun setFromUCallback(error: CharsetCoderResult, callback: CharsetEncoderCallback, context: Any?) {
        if (error.isMalformed()) malformedCallback = callback else if (error.isUnmappable()) unmappableCallback = callback
        if (fromUContext == null || fromUContext != context) fromUContext = context
    }
    open fun reset() {
        errorBufferLength = 0; fromUnicodeStatus = 0; fromUChar32 = 0
        preFromUBegin = 0; preFromUFirstCP = -1; preFromULength = 0
    }
    fun fromUCountPending(): Int = when {
        preFromULength > 0 -> charCount(preFromUFirstCP) + preFromULength
        preFromULength < 0 -> -preFromULength
        fromUChar32 > 0 -> 1
        preFromUFirstCP > 0 -> charCount(preFromUFirstCP)
        else -> 0
    }
    fun encodeNioLoop(source: CharsetCharBuffer, target: BinaryBuffer): CharsetCoderResult {
        if (!source.hasRemaining() && errorBufferLength == 0) { fromUChar32 = 0; return CharsetCoderResult.UNDERFLOW }
        source.position += fromUCountPending()
        val result = encode(source, target, null, false)
        source.position -= fromUCountPending()
        return result
    }
    fun flush(target: BinaryBuffer): CharsetCoderResult = encode(CharsetCharBuffer(CharArray(0)), target, null, true)
    abstract fun encodeLoop(source: CharsetCharBuffer, target: BinaryBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult
    fun encode(source: CharsetCharBuffer?, target: BinaryBuffer?, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
        require(source != null && target != null)
        if (errorBufferLength > 0) {
            var i = 0
            val length = errorBufferLength
            do {
                if (target.remaining == 0) {
                    var j = 0
                    do { errorBuffer[j++] = errorBuffer[i++] } while (i < length)
                    errorBufferLength = j.toByte().toInt()
                    return CharsetCoderResult.OVERFLOW
                }
                target.putCharsetByte(errorBuffer[i++]); offsets?.put(-1)
            } while (i < length)
            errorBufferLength = 0
        }
        if (!flush && source.remaining == 0 && preFromULength >= 0) return CharsetCoderResult.UNDERFLOW
        return fromUnicodeWithCallback(source, target, offsets, flush)
    }
    fun fromUnicodeWithCallback(input: CharsetCharBuffer, target: BinaryBuffer, offsets: CharsetIntBuffer?, flushing: Boolean): CharsetCoderResult {
        var source = input; var flush = flushing; var sourceIndex = 0
        val replay = CharsetCharBuffer(CharArray(19))
        var realSource: CharsetCharBuffer? = null; var realFlush = false
        fun writeReplay(length: Int) {
            if (length < 0 || length > preFromUArray.size) throw IndexOutOfBoundsException()
            if (length > replay.remaining) throw CharsetBufferOverflowException()
            repeat(length) { replay.put(preFromUArray[it]) }
        }
        if (preFromULength < 0) {
            realSource = source; realFlush = flush
            writeReplay(-preFromULength); source = replay; source.position = 0; source.limit = -preFromULength
            flush = false; preFromULength = 0
        }
        while (true) {
            var result = encodeLoop(source, target, offsets, flush)
            val sawEnd = result.isUnderflow() && flush && source.remaining == 0 && fromUChar32 == 0
            var called = false
            while (true) {
                if (preFromULength < 0) {
                    check(realSource == null) { "assert failed" }
                    realSource = source; realFlush = flush
                    writeReplay(-preFromULength); source = replay; source.position = 0; source.limit = -preFromULength
                    flush = false; sourceIndex += preFromULength
                    if (sourceIndex < 0) sourceIndex = -1
                    preFromULength = 0
                }
                if (result.isUnderflow()) {
                    if (source.position < source.limit) break
                    else if (realSource != null) {
                        source = realSource; flush = realFlush; sourceIndex = source.position; realSource = null; break
                    } else if (flush && fromUChar32 != 0) {
                        result = CharsetCoderResult.malformedForLength(1); called = false
                    } else {
                        if (flush) { if (!sawEnd) break; reset() }
                        return result
                    }
                }
                if (called || result.isOverflow() || !result.isError()) {
                    if (realSource != null) {
                        val length = source.remaining
                        if (length > 0) {
                            if (length > preFromUArray.size) throw IndexOutOfBoundsException()
                            repeat(length) { preFromUArray[it] = source.get() }
                            preFromULength = (-length).toByte().toInt()
                        }
                    }
                    return result
                }
                val cp = fromUChar32
                require(cp in 0..0x10ffff)
                invalidUCharLength = if (cp < 0x10000) { invalidUCharBuffer[0] = cp.toChar(); 1 } else {
                    invalidUCharBuffer[0] = ((cp ushr 10) + 0xd7c0).toChar()
                    invalidUCharBuffer[1] = ((cp and 1023) + 0xdc00).toChar(); 2
                }
                fromUChar32 = 0
                val cb = if (result.isUnmappable()) unmappableCallback else malformedCallback
                result = cb.call(this, fromUContext, source, target, offsets, invalidUCharBuffer, invalidUCharLength, cp, result)
                called = true
            }
        }
    }
    open fun cbFromUWriteSub(source: CharsetCharBuffer, target: BinaryBuffer, offsets: CharsetIntBuffer?): CharsetCoderResult {
        val sub = if (subChar1 != 0.toByte() && invalidUCharBuffer[0].code <= 255) byteArrayOf(subChar1) else replacement
        return fromUWriteBytes(this, sub, 0, sub.size, target, offsets, source.position)
    }
    fun cbFromUWriteUChars(source: CharsetCharBuffer, target: BinaryBuffer, offsets: CharsetIntBuffer?): CharsetCoderResult {
        var oldPosition = target.position
        val offset = source.position
        val result = encode(source, target, null, false)
        if (offsets != null) while (target.position != oldPosition) { offsets.put(offset); oldPosition++ }
        if (result.isOverflow()) {
            val newTarget = BinaryBuffer(errorBuffer)
            require(errorBufferLength in 0..newTarget.limit)
            newTarget.position = errorBufferLength; errorBufferLength = 0
            encode(source, newTarget, null, false)
            errorBuffer = newTarget.bytes; errorBufferLength = newTarget.position
        }
        return result
    }
    fun handleSurrogates(source: CharsetCharBuffer, lead: Char): CharsetCoderResult? {
        if (lead.code !in 0xd800..0xdbff) { fromUChar32 = lead.code; return CharsetCoderResult.malformedForLength(1) }
        if (!source.hasRemaining()) { fromUChar32 = lead.code; return CharsetCoderResult.UNDERFLOW }
        val trail = source.get()
        if (trail.code !in 0xdc00..0xdfff) { fromUChar32 = lead.code; source.position--; return CharsetCoderResult.malformedForLength(1) }
        fromUChar32 = ((lead.code - 0xd800) shl 10) + trail.code - 0xdc00 + 0x10000
        return null
    }
    fun handleSurrogates(source: CharArray, index: Int, limit: Int, lead: Char): CharsetCoderResult? {
        if (lead.code !in 0xd800..0xdbff) { fromUChar32 = lead.code; return CharsetCoderResult.malformedForLength(1) }
        if (index >= limit) { fromUChar32 = lead.code; return CharsetCoderResult.UNDERFLOW }
        if (index !in source.indices) throw IndexOutOfBoundsException()
        val trail = source[index]
        if (trail.code !in 0xdc00..0xdfff) { fromUChar32 = lead.code; return CharsetCoderResult.malformedForLength(1) }
        fromUChar32 = ((lead.code - 0xd800) shl 10) + trail.code - 0xdc00 + 0x10000
        return null
    }
    companion object {
        private fun charCount(cp: Int) = if (cp >= 0x10000) 2 else 1
        fun isFromUUseFallback(useFallback: Boolean, cp: Int): Boolean = useFallback || (cp >= 0xe000 && (cp <= 0xf8ff || (cp >= 0xf0000 && (cp <= 0xffffd || cp in 0x100000..0x10fffd))))
        fun getMaxBytesForString(length: Int, maxCharSize: Int): Int = (length + 10) * maxCharSize
        fun fromUWriteBytes(encoder: CharsetEncoderRuntime, bytes: ByteArray, begin: Int, length: Int, target: BinaryBuffer, offsets: CharsetIntBuffer?, sourceIndex: Int): CharsetCoderResult {
            var index = begin; val limit = begin + length
            var remaining = length; var count = length
            var result = CharsetCoderResult.UNDERFLOW
            try {
                while (index < limit) {
                    if (index !in bytes.indices) throw IndexOutOfBoundsException()
                    target.putCharsetByte(bytes[index]); index++
                }
                remaining = 0
            } catch (_: CharsetBufferOverflowException) { result = CharsetCoderResult.OVERFLOW }
            if (offsets != null) while (count > remaining) { offsets.put(sourceIndex); --count }
            encoder.errorBufferLength = limit - index
            if (encoder.errorBufferLength > 0) {
                var j = 0
                while (index < limit) {
                    val dest = j++
                    if (index !in bytes.indices || dest !in encoder.errorBuffer.indices) throw IndexOutOfBoundsException()
                    encoder.errorBuffer[dest] = bytes[index++]
                }
                result = CharsetCoderResult.OVERFLOW
            }
            return result
        }
    }
}
