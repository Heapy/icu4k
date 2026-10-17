// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2013, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

internal object CharsetEncoderCallbacks {
    // Keep ICU4J's pinned callback list, independently of evolving Unicode property data.
    fun isDefaultIgnorable(cp: Int): Boolean = cp == 0xad || cp == 0x34f || cp == 0x61c || cp == 0x115f || cp == 0x1160 ||
        cp in 0x17b4..0x17b5 || cp in 0x180b..0x180f || cp in 0x200b..0x200f || cp in 0x202a..0x202e ||
        cp in 0x2060..0x206f || cp == 0x3164 || cp in 0xfe00..0xfe0f || cp == 0xfeff || cp == 0xffa0 ||
        cp in 0xfff0..0xfff8 || cp in 0x1bca0..0x1bca3 || cp in 0x1d173..0x1d17a || cp in 0xe0000..0xe0fff
    val STOP = CharsetEncoderCallback { _, _, _, _, _, _, _, cp, error ->
        if (error.isUnmappable() && isDefaultIgnorable(cp)) CharsetCoderResult.UNDERFLOW else error
    }
    val SKIP = CharsetEncoderCallback { _, context, _, _, _, _, _, _, error ->
        if (context == null || ((context as String) == "i" && error.isUnmappable())) CharsetCoderResult.UNDERFLOW else error
    }
    val SUBSTITUTE = CharsetEncoderCallback { encoder, context, source, target, offsets, _, _, cp, error ->
        when {
            error.isUnmappable() && isDefaultIgnorable(cp) -> CharsetCoderResult.UNDERFLOW
            context == null -> encoder.cbFromUWriteSub(source, target, offsets)
            (context as String) == "i" && error.isUnmappable() -> encoder.cbFromUWriteSub(source, target, offsets)
            else -> error
        }
    }
    val ESCAPE = CharsetEncoderCallback { encoder, context, _, target, offsets, buffer, length, cp, error ->
        if (error.isUnmappable() && isDefaultIgnorable(cp)) CharsetCoderResult.UNDERFLOW
        else {
            val chars = CharArray(32)
            var size = 0
            fun put(index: Int, c: Char) { if (index !in chars.indices) throw IndexOutOfBoundsException(); chars[index] = c }
            fun append(c: Char) { put(size++, c) }
            fun get(index: Int): Char { if (index !in buffer.indices) throw IndexOutOfBoundsException(); return buffer[index] }
            fun digits(number: Int, radix: Int, width: Int): Int {
                var n = number; var count = 0
                do {
                    val digit = n % radix
                    put(size + count++, (0x30 + digit + if (digit <= 9) 0 else 7).toChar())
                    n /= radix
                } while (n != 0 && size + count < chars.size)
                while (count < width) put(size + count++, '0')
                for (i in 0 until count / 2) {
                    val end = size + count - 1 - i
                    val c = chars[end]; chars[end] = chars[size + i]; chars[size + i] = c
                }
                return count
            }
            fun defaultEscape(prefix: Char, kind: Char) {
                repeat(length) { index -> append(prefix); append(kind); size += digits(get(index).code, 16, 4) }
            }
            when (context as? String) {
                "J" -> defaultEscape('\\', 'u')
                "C" -> {
                    append('\\')
                    if (length == 2) { append('U'); size = digits(cp, 16, 8) }
                    else { append('u'); size += digits(get(0).code, 16, 4) }
                }
                "D", "X" -> {
                    append('&'); append('#')
                    val hex = context == "X"
                    if (hex) append('x')
                    size += digits(if (length == 2) cp else get(0).code, if (hex) 16 else 10, 0)
                    append(';')
                }
                "U" -> {
                    append('{'); append('U'); append('+')
                    size += digits(if (length == 2) cp else get(0).code, 16, 4); append('}')
                }
                "S" -> { append('\\'); size += digits(cp, 16, 0); append(' ') }
                else -> defaultEscape('%', 'U')
            }
            encoder.cbFromUWriteUChars(CharsetCharBuffer(chars).apply { limit = size }, target, offsets)
        }
    }
}
