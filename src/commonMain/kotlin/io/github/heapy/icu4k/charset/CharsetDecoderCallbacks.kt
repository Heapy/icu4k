// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

internal object CharsetDecoderCallbacks {
    val STOP = CharsetDecoderCallback { _, _, _, _, _, _, _, error -> error }
    val SKIP = CharsetDecoderCallback { _, context, _, _, _, _, _, error ->
        if (context == null || ((context as String) == "i" && error.isUnmappable())) CharsetCoderResult.UNDERFLOW else error
    }
    val SUBSTITUTE = CharsetDecoderCallback { decoder, _, source, target, offsets, _, _, _ ->
        val replacement = decoder.replacement.toCharArray()
        val useReplacement = replacement.size != 1 || (replacement[0] != '\u001a' && replacement[0] != '\ufffd')
        val chars = if (useReplacement) replacement else charArrayOf(if (decoder.invalidCharLength == 1 && decoder.subChar1 != 0.toByte()) '\u001a' else '\ufffd')
        CharsetDecoderRuntime.toUWriteUChars(decoder, chars, 0, chars.size, target, offsets, source.position)
    }
    val ESCAPE = CharsetDecoderCallback { decoder, context, _, target, offsets, buffer, length, _ ->
        val chars = CharArray(32)
        var size = 0
        fun append(c: Char) {
            if (size !in chars.indices) throw IndexOutOfBoundsException()
            chars[size++] = c
        }
        repeat(length) { index ->
            if (index !in buffer.indices) throw IndexOutOfBoundsException()
            val byte = buffer[index].code and 255
            val text = when (context as? String) {
                "D" -> "&#$byte;"
                "X" -> "&#x${byte.toString(16).uppercase()};"
                "C" -> "\\x${byte.toString(16).uppercase().padStart(2, '0')}"
                else -> "%X${byte.toString(16).uppercase().padStart(2, '0')}"
            }
            for (c in text) append(c)
        }
        CharsetDecoderRuntime.toUWriteUChars(decoder, chars, 0, size, target, offsets, 0)
    }
}
