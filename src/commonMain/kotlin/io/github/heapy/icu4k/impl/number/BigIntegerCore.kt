// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.math.IcuDecimalValue

/** Immutable decimal-magnitude backing for the portable integer interchange boundary. */
internal class BigIntegerCore(value: String?, radix: Int = 10) : Comparable<BigIntegerCore> {
    val sign: Int
    val magnitude: String

    init {
        val text = value!! // JDK reads length before validating radix.
        if (radix !in 2..36) throw NumberFormatException("Radix out of range")
        if (text.isEmpty()) throw NumberFormatException("Zero length BigInteger")
        val minus = text.lastIndexOf('-')
        val plus = text.lastIndexOf('+')
        var cursor = 0
        var parsedSign = 1
        if (minus >= 0) {
            if (minus != 0 || plus >= 0) throw NumberFormatException("Illegal embedded sign character")
            parsedSign = -1
            cursor = 1
        } else if (plus >= 0) {
            if (plus != 0) throw NumberFormatException("Illegal embedded sign character")
            cursor = 1
        }
        if (cursor == text.length) throw NumberFormatException("Zero length BigInteger")
        while (cursor < text.length && UCharacter.digit(text[cursor].code, radix) == 0) cursor++
        var result = "0"
        if (cursor < text.length) {
            // Match JDK Integer.parseInt group boundaries, including invalid-digit diagnostics.
            var groupSize = 1
            var groupBase = radix.toLong()
            while (groupBase * radix <= Int.MAX_VALUE) { groupBase *= radix; groupSize++ }
            var count = (text.length - cursor) % groupSize
            if (count == 0) count = groupSize
            while (cursor < text.length) {
                val group = text.substring(cursor, cursor + count)
                var small = 0L
                var multiplier = 1L
                for (ch in group) {
                    val digit = UCharacter.digit(ch.code, radix)
                    if (digit < 0) {
                        val suffix = if (radix == 10) "" else " under radix $radix"
                        throw NumberFormatException("For input string: \"$group\"$suffix")
                    }
                    small = small * radix + digit
                    multiplier *= radix
                }
                result = multiplyAdd(result, multiplier, small)
                cursor += count
                count = groupSize
            }
        }
        magnitude = result
        sign = if (result == "0") 0 else parsedSign
    }

    override fun toString(): String = if (sign < 0) "-$magnitude" else magnitude
    fun toString(radix: Int): String {
        val base = if (radix in 2..36) radix else 10
        if (base == 10 || sign == 0) return toString()
        var remaining = magnitude
        val reversed = StringBuilder()
        while (remaining != "0") {
            val (quotient, remainder) = divideSmall(remaining, base.toLong())
            reversed.append("0123456789abcdefghijklmnopqrstuvwxyz"[remainder.toInt()])
            remaining = quotient
        }
        if (sign < 0) reversed.append('-')
        return reversed.reverse().toString()
    }
    override fun compareTo(other: BigIntegerCore): Int {
        if (sign != other.sign) return sign.compareTo(other.sign)
        val size = magnitude.length.compareTo(other.magnitude.length)
        return sign * (if (size != 0) size else magnitude.compareTo(other.magnitude).coerceIn(-1, 1))
    }
    override fun equals(other: Any?): Boolean = other is BigIntegerCore && sign == other.sign && magnitude == other.magnitude
    override fun hashCode(): Int {
        var remaining = magnitude
        val words = ArrayList<Int>()
        while (remaining != "0") {
            val (quotient, remainder) = divideSmall(remaining, 4294967296L)
            words.add(remainder.toInt())
            remaining = quotient
        }
        var hash = 0
        for (i in words.indices.reversed()) hash = 31 * hash + words[i]
        return sign * hash
    }
    fun toLong(): Long {
        var result = 0L
        for (ch in magnitude) result = result * 10 + (ch - '0')
        return if (sign < 0) -result else result
    }
    fun toInt(): Int {
        var result = 0
        for (ch in magnitude) result = result * 10 + (ch - '0')
        return if (sign < 0) -result else result
    }
    fun toDouble(): Double = toString().toDouble()
    fun toFloat(): Float = IcuDecimalValue.parse(toString()).floatValue()

    private fun multiplyAdd(value: String, multiplier: Long, addend: Long): String {
        val reversed = StringBuilder()
        var carry = addend
        for (i in value.indices.reversed()) {
            carry += (value[i] - '0') * multiplier
            reversed.append(('0'.code + (carry % 10).toInt()).toChar())
            carry /= 10
        }
        while (carry > 0) {
            reversed.append(('0'.code + (carry % 10).toInt()).toChar())
            carry /= 10
        }
        return reversed.reverse().toString().trimStart('0').ifEmpty { "0" }
    }
    private fun divideSmall(value: String, divisor: Long): Pair<String, Long> {
        val quotient = StringBuilder()
        var remainder = 0L
        for (ch in value) {
            remainder = remainder * 10 + (ch - '0')
            val digit = (remainder / divisor).toInt()
            if (digit != 0 || quotient.isNotEmpty()) quotient.append(('0'.code + digit).toChar())
            remainder %= divisor
        }
        return quotient.toString().ifEmpty { "0" } to remainder
    }
}
