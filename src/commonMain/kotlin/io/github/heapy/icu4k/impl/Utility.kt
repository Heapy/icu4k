// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UTF16

/** ICU utilities; currently the original escape decoder. */
internal object Utility {
    fun escape(source: String): String = buildString {
        var i = 0
        while (i < source.length) {
            val c = UTF16.charAt(source, i)
            i += UTF16.getCharCount(c)
            if (c in 0x20..0x7f) {
                if (c == '\\'.code) append("\\\\") else append(c.toChar())
            } else {
                append(if (c <= 0xffff) "\\u" else "\\U")
                append(hex(c.toLong(), if (c <= 0xffff) 4 else 8))
            }
        }
    }
    fun hex(ch: Long): String = hex(ch, 4)
    fun hex(number: Long, places: Int): String {
        if (number == Long.MIN_VALUE) return "-8000000000000000"
        val negative = number < 0
        val value = if (negative) -number else number
        var result = value.toString(16).uppercase()
        if (result.length < places) {
            if (places > 16) throw IndexOutOfBoundsException("Hex padding width: $places")
            result = "0000000000000000".substring(result.length, places) + result
        }
        return if (negative) "-$result" else result
    }
    fun isUnprintable(c: Int): Boolean = c !in 0x20..0x7e
    fun shouldAlwaysBeEscaped(c: Int): Boolean = when {
        c < 0x20 -> true
        c <= 0x7e -> false
        c <= 0x9f -> true
        c < 0xd800 -> false
        c <= 0xdfff || c in 0xfdd0..0xfdef || c and 0xfffe == 0xfffe -> true
        else -> c > 0x10ffff
    }
    fun <T : Appendable> escape(result: T, c: Int): T {
        result.append('\\').append(if (c and -65536 != 0) 'U' else 'u')
        for (shift in (if (c and -65536 != 0) 28 else 12) downTo 0 step 4) result.append("0123456789ABCDEF"[(c shr shift) and 15])
        return result
    }
    fun escapeUnprintable(result: Appendable, c: Int): Boolean {
        if (!isUnprintable(c)) return false
        escape(result, c)
        return true
    }

    fun unescapeAndLengthAt(s: CharSequence, offset: Int): Int = unescapeAndLengthAt(s, offset, s.length)
    private fun unescapeAndLengthAt(s: CharSequence, initialOffset: Int, length: Int): Int {
        var offset = initialOffset
        var result = 0
        var n = 0
        var minDig = 0
        var maxDig = 0
        var bitsPerDigit = 4
        var braces = false
        if (offset < 0 || offset >= length) return -1
        val start = offset
        var c = s[offset++].code
        when (c) {
            'u'.code -> { minDig = 4; maxDig = 4 }
            'U'.code -> { minDig = 8; maxDig = 8 }
            'x'.code -> {
                minDig = 1
                if (offset < length && s[offset] == '{') { ++offset; braces = true; maxDig = 8 }
                else maxDig = 2
            }
            else -> {
                val digit = digit8(c)
                if (digit >= 0) { minDig = 1; maxDig = 3; n = 1; bitsPerDigit = 3; result = digit }
            }
        }
        if (minDig != 0) {
            while (offset < length && n < maxDig) {
                c = s[offset].code
                val digit = if (bitsPerDigit == 3) digit8(c) else digit16(c)
                if (digit < 0) break
                result = (result shl bitsPerDigit) or digit
                ++offset; ++n
            }
            if (n < minDig) return -1
            if (braces) { if (c != '}'.code) return -1; ++offset }
            if (result < 0 || result >= 0x110000) return -1
            if (offset < length && result in 0xd800..0xdbff) {
                var ahead = offset + 1
                c = s[offset].code
                if (c == '\\'.code && ahead < length) {
                    val decoded = unescapeAndLengthAt(s, ahead, minOf(ahead + 11, length))
                    if (decoded >= 0) { c = decoded shr 8; ahead += decoded and 255 }
                }
                if (c in 0xdc00..0xdfff) { offset = ahead; result = toCodePoint(result, c) }
            }
            return (result shl 8) or (offset - start)
        }
        for (i in unescapeMap.indices step 2) {
            if (c == unescapeMap[i]) return (unescapeMap[i + 1] shl 8) or (offset - start)
            if (c < unescapeMap[i]) break
        }
        if (c == 'c'.code && offset < length) {
            c = s[offset].code
            if (c in 0xd800..0xdbff && offset + 1 < s.length && s[offset + 1].code in 0xdc00..0xdfff) c = toCodePoint(c, s[offset + 1].code)
            return ((c and 31) shl 8) or (offset + (if (c > 0xffff) 2 else 1) - start)
        }
        if (c in 0xd800..0xdbff && offset < length && s[offset].code in 0xdc00..0xdfff) c = toCodePoint(c, s[offset++].code)
        return (c shl 8) or (offset - start)
    }
    fun cpFromCodePointAndLength(value: Int): Int = value shr 8
    fun lengthFromCodePointAndLength(value: Int): Int = value and 255
    fun unescape(s: CharSequence): String = decode(s, false)
    fun unescapeLeniently(s: CharSequence): String = decode(s, true)
    private fun decode(s: CharSequence, lenient: Boolean): String {
        var buffer: StringBuilder? = null
        var i = 0
        while (i < s.length) {
            val c = s[i++]
            if (c == '\\') {
                if (buffer == null) buffer = StringBuilder(s.length).append(s, 0, i - 1)
                val decoded = unescapeAndLengthAt(s, i)
                if (decoded < 0) {
                    if (!lenient) throw IllegalArgumentException("Invalid escape sequence " + s.subSequence(i - 1, minOf(i + 9, s.length)))
                    buffer.append(c)
                } else {
                    buffer.append(UTF16.valueOf(decoded shr 8))
                    i += decoded and 255
                }
            } else buffer?.append(c)
        }
        return buffer?.toString() ?: s.toString()
    }
    private fun toCodePoint(lead: Int, trail: Int): Int = ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
    private fun digit8(c: Int): Int = if (c in 48..55) c - 48 else -1
    private fun digit16(c: Int): Int = when (c) { in 48..57 -> c - 48; in 65..70 -> c - 55; in 97..102 -> c - 87; else -> -1 }
    private val unescapeMap = intArrayOf(97, 7, 98, 8, 101, 27, 102, 12, 110, 10, 114, 13, 116, 9, 118, 11)
}
