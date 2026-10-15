// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.lang.UCharacter

internal object DecimalNumberParsing {
    fun digit(c: Char): Int = if (c in '0'..'9') c - '0' else UCharacter.digit(c.code, 10)
    /** Signed decimal int parsing with the source's BMP decimal-digit and overflow behavior. */
    fun int(text: String): Int {
        if (text.isEmpty()) throw NumberFormatException()
        var index = 0
        val negative = text[0] == '-'
        if (negative || text[0] == '+') index++
        if (index == text.length) throw NumberFormatException()
        val limit = if (negative) Int.MIN_VALUE else -Int.MAX_VALUE
        val threshold = limit / 10
        var value = 0
        while (index < text.length) {
            val digit = digit(text[index++])
            if (digit < 0 || value < threshold) throw NumberFormatException()
            value *= 10
            if (value < limit + digit) throw NumberFormatException()
            value -= digit
        }
        return if (negative) value else -value
    }
    /** Signed decimal long parsing with the source's BMP decimal-digit and overflow behavior. */
    fun long(text: String): Long {
        if (text.isEmpty()) throw NumberFormatException()
        var index = 0
        val negative = text[0] == '-'
        if (negative || text[0] == '+') index++
        if (index == text.length) throw NumberFormatException()
        val limit = if (negative) Long.MIN_VALUE else -Long.MAX_VALUE
        val threshold = limit / 10
        var value = 0L
        while (index < text.length) {
            val digit = digit(text[index++])
            if (digit < 0 || value < threshold) throw NumberFormatException()
            value *= 10
            if (value < limit + digit) throw NumberFormatException()
            value -= digit
        }
        return if (negative) value else -value
    }
}
