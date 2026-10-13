// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Common parsing of Java floating-point literals used by ICU property aliases. */
internal object ICUNumberParsing {
    private val decimal = Regex("[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)[fFdD]?")
    private val hexadecimal = Regex("[+-]?0[xX](?:[0-9a-fA-F]+(?:\\.[0-9a-fA-F]*)?|\\.[0-9a-fA-F]+)[pP][+-]?[0-9]+[fFdD]?")
    fun parseDouble(input: String): Double {
        val s = input.trim { it <= ' ' }
        val unsigned = if (s.startsWith('+') || s.startsWith('-')) s.substring(1) else s
        if (unsigned == "NaN") return Double.NaN
        if (unsigned == "Infinity") return if (s.startsWith('-')) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        if (hexadecimal.matches(s)) return parseHex(s)
        if (!decimal.matches(s)) throw NumberFormatException("For input string: \"$input\"")
        val number = if (s.last() in "fFdD") s.dropLast(1) else s
        return number.toDouble()
    }
    private fun digit(c: Char): Int = when (c) {
        in '0'..'9' -> c.code - '0'.code
        in 'a'..'f' -> c.code - 'a'.code + 10
        else -> c.code - 'A'.code + 10
    }
    private fun parseHex(s: String): Double {
        val negative = s[0] == '-'
        val sign = if (negative) Long.MIN_VALUE else 0L
        val start = if (s[0] == '+' || negative) 3 else 2
        val p = s.indexOfAny(charArrayOf('p', 'P'), start)
        val mantissa = s.substring(start, p)
        val dot = mantissa.indexOf('.')
        val fractionDigits = if (dot < 0) 0 else mantissa.length - dot - 1
        val digits = mantissa.replace(".", "")
        val first = digits.indexOfFirst { it != '0' }
        if (first < 0) return Double.fromBits(sign)
        var eIndex = p + 1
        val eNegative = s[eIndex] == '-'
        if (s[eIndex] == '+' || eNegative) ++eIndex
        var exponent = 0L
        while (eIndex < s.length && s[eIndex] in '0'..'9') {
            exponent = minOf(1_000_000_000_000L, exponent * 10 + s[eIndex++].code - '0'.code)
        }
        if (eNegative) exponent = -exponent
        val firstDigit = digit(digits[first])
        val firstBits = when { firstDigit >= 8 -> 4; firstDigit >= 4 -> 3; firstDigit >= 2 -> 2; else -> 1 }
        val totalBits = (digits.length - first - 1).toLong() * 4 + firstBits
        var binaryExponent = exponent - fractionDigits.toLong() * 4 + totalBits - 1
        if (binaryExponent > 1023) return Double.fromBits(sign or 0x7ff0000000000000L)
        if (binaryExponent < -1075) return Double.fromBits(sign)
        fun bitAt(index: Int): Int {
            if (index.toLong() >= totalBits) return 0
            val offset = index + 4 - firstBits
            return (digit(digits[first + offset / 4]) shr (3 - offset % 4)) and 1
        }
        val count = if (binaryExponent >= -1022) 53 else (binaryExponent + 1075).toInt()
        var significand = 0L
        for (i in 0 until count) significand = (significand shl 1) or bitAt(i).toLong()
        if (bitAt(count) != 0) {
            var sticky = false
            var i = count.toLong() + 1
            while (i < totalBits) {
                val offset = i + 4 - firstBits
                if ((digit(digits[first + (offset / 4).toInt()]) shr (3 - (offset % 4).toInt())) and 1 != 0) { sticky = true; break }
                ++i
            }
            if (sticky || significand and 1L != 0L) ++significand
        }
        if (binaryExponent < -1022) return Double.fromBits(sign or significand)
        if (significand == 1L shl 53) { significand = significand ushr 1; ++binaryExponent }
        if (binaryExponent > 1023) return Double.fromBits(sign or 0x7ff0000000000000L)
        return Double.fromBits(sign or ((binaryExponent + 1023) shl 52) or (significand and 0x000fffffffffffffL))
    }
}
