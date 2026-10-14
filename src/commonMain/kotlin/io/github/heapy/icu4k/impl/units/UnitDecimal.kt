// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

/** Arbitrary-precision decimal arithmetic for the common unit converter. Scale is retained like BigDecimal. */
internal class UnitDecimal private constructor(val sign: Int, val magnitude: String, val scale: Int) : Comparable<UnitDecimal> {
    constructor(text: String) : this(parse(text))
    private constructor(parsed: UnitDecimal) : this(parsed.sign, parsed.magnitude, parsed.scale)
    val precision: Int get() = magnitude.length
    fun negate(): UnitDecimal = UnitDecimal(-sign, magnitude, scale)
    fun abs(): UnitDecimal = if (sign < 0) negate() else this
    fun add(other: UnitDecimal): UnitDecimal {
        val targetScale = maxOf(scale, other.scale)
        val a = zeros(magnitude, targetScale.toLong() - scale)
        val b = zeros(other.magnitude, targetScale.toLong() - other.scale)
        if (sign == 0) return of(other.sign, b, targetScale)
        if (other.sign == 0) return of(sign, a, targetScale)
        if (sign == other.sign) return of(sign, addNatural(a, b), targetScale)
        val comparison = compareNatural(a, b)
        return if (comparison >= 0) of(sign, subtractNatural(a, b), targetScale) else of(other.sign, subtractNatural(b, a), targetScale)
    }
    fun subtract(other: UnitDecimal): UnitDecimal = add(other.negate())
    fun multiply(other: UnitDecimal): UnitDecimal = of(sign * other.sign, multiplyNatural(magnitude, other.magnitude), checkedScale(scale.toLong() + other.scale, sign * other.sign == 0))
    fun round(precision: Int = 34): UnitDecimal {
        require(precision > 0)
        if (this.precision <= precision) return this
        val drop = this.precision - precision
        var result = magnitude.substring(0, precision)
        val discarded = magnitude.substring(precision)
        if (roundUp(result, discarded, "5" + "0".repeat(drop - 1))) result = addNatural(result, "1")
        var newScale = checkedScale(scale.toLong() - drop, sign == 0)
        if (result.length > precision) { result = result.dropLast(1); newScale = checkedScale(newScale.toLong() - 1, false) }
        return of(sign, result, newScale)
    }
    fun divide(other: UnitDecimal, precision: Int = 34): UnitDecimal {
        require(precision > 0)
        if (other.sign == 0) throw ArithmeticException(if (sign == 0) "Division undefined" else "Division by zero")
        val preferred = scale.toLong() - other.scale
        if (sign == 0) return of(0, "0", checkedScale(preferred, true))
        var exponent = magnitude.length - other.magnitude.length
        val comparison = if (exponent >= 0) compareNatural(magnitude, zeros(other.magnitude, exponent.toLong())) else compareNatural(zeros(magnitude, -exponent.toLong()), other.magnitude)
        if (comparison < 0) exponent--
        val shift = precision.toLong() - 1 - exponent
        val numerator = if (shift >= 0) zeros(magnitude, shift) else magnitude
        val denominator = if (shift < 0) zeros(other.magnitude, -shift) else other.magnitude
        val (quotient, remainder) = divideNatural(numerator, denominator)
        var result = quotient
        if (roundUp(result, multiplyNatural(remainder, "2"), denominator)) result = addNatural(result, "1")
        var resultScale = preferred + shift
        if (result.length > precision) { result = result.dropLast(1); resultScale-- }
        if (remainder == "0") while (result.endsWith('0') && resultScale > preferred) { result = result.dropLast(1); resultScale-- }
        return of(sign * other.sign, result, checkedScale(resultScale, false))
    }
    fun pow(exponent: Int): UnitDecimal {
        if (exponent !in 0..999999999) throw ArithmeticException("Invalid operation")
        var n = exponent; var base = this; var result = ONE
        while (n != 0) { if ((n and 1) != 0) result = result.multiply(base); n = n ushr 1; if (n != 0) base = base.multiply(base) }
        return result
    }
    fun powRounded(exponent: Int, precision: Int = 34): UnitDecimal {
        if (exponent !in -999999999..999999999 || precision <= 0) throw ArithmeticException("Invalid operation")
        if (exponent == 0) return ONE
        val n = kotlin.math.abs(exponent)
        val exponentDigits = n.toString().length
        if (exponentDigits > precision) throw ArithmeticException("Invalid operation")
        val working = precision + exponentDigits + 1
        var result = ONE
        var bit = 1 shl (31 - n.countLeadingZeroBits())
        while (bit != 0) {
            result = result.multiply(result).round(working)
            if ((n and bit) != 0) result = result.multiply(this).round(working)
            bit = bit ushr 1
        }
        if (exponent < 0) result = ONE.divide(result, working)
        return result.round(precision)
    }
    enum class Rounding { UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UNNECESSARY }
    fun setScale(newScale: Int, rounding: Rounding = Rounding.HALF_EVEN): UnitDecimal {
        if (newScale == scale) return this
        if (sign == 0) return of(0, "0", newScale)
        if (newScale > scale) return of(sign, zeros(magnitude, newScale.toLong() - scale), newScale)
        val drop = scale.toLong() - newScale
        val retained = magnitude.length.toLong() - drop
        var integer = if (retained <= 0) "0" else magnitude.substring(0, retained.toInt())
        val discarded = if (retained <= 0) magnitude else magnitude.substring(retained.toInt())
        val nonzero = discarded.any { it != '0' }
        if (nonzero) {
            val halfway = if (drop > magnitude.length) -1 else when {
                discarded[0] < '5' -> -1
                discarded[0] > '5' -> 1
                discarded.drop(1).any { it != '0' } -> 1
                else -> 0
            }
            val up = when (rounding) {
                Rounding.UP -> true
                Rounding.DOWN -> false
                Rounding.CEILING -> sign > 0
                Rounding.FLOOR -> sign < 0
                Rounding.HALF_UP -> halfway >= 0
                Rounding.HALF_DOWN -> halfway > 0
                Rounding.HALF_EVEN -> halfway > 0 || halfway == 0 && (integer.last() - '0') % 2 != 0
                Rounding.UNNECESSARY -> throw ArithmeticException("Rounding necessary")
            }
            if (up) integer = addNatural(integer, "1")
        }
        return of(sign, integer, newScale)
    }
    fun divideToScale(other: UnitDecimal, newScale: Int, rounding: Rounding = Rounding.HALF_EVEN): UnitDecimal {
        if (other.sign == 0) throw ArithmeticException(if (sign == 0) "Division undefined" else "Division by zero")
        if (sign == 0) return of(0, "0", newScale)
        val shift = other.scale.toLong() + newScale - scale
        val numerator = if (shift >= 0) zeros(magnitude, shift) else magnitude
        val denominator = if (shift < 0) zeros(other.magnitude, -shift) else other.magnitude
        val (quotient, remainder) = divideNatural(numerator, denominator)
        var result = quotient
        if (remainder != "0") {
            val halfway = compareNatural(multiplyNatural(remainder, "2"), denominator)
            val resultSign = sign * other.sign
            val up = when (rounding) {
                Rounding.UP -> true
                Rounding.DOWN -> false
                Rounding.CEILING -> resultSign > 0
                Rounding.FLOOR -> resultSign < 0
                Rounding.HALF_UP -> halfway >= 0
                Rounding.HALF_DOWN -> halfway > 0
                Rounding.HALF_EVEN -> halfway > 0 || halfway == 0 && (quotient.last() - '0') % 2 != 0
                Rounding.UNNECESSARY -> throw ArithmeticException("Rounding necessary")
            }
            if (up) result = addNatural(result, "1")
        }
        return of(sign * other.sign, result, newScale)
    }
    fun stripTrailingZeros(): UnitDecimal {
        if (sign == 0) return ZERO
        val stripped = magnitude.trimEnd('0')
        return of(sign, stripped, checkedScale(scale.toLong() - (magnitude.length - stripped.length), false))
    }
    fun integerValue(): UnitDecimal = setScale(0, Rounding.DOWN)
    fun longValue(): Long {
        var result = 0L
        val end = if (scale >= 0) (magnitude.length.toLong() - scale).coerceAtLeast(0).toInt() else magnitude.length
        for (i in 0 until end) result = result * 10 + (magnitude[i] - '0')
        if (scale < 0) {
            var exponent = -scale.toLong(); var base = 10L; var power = 1L
            while (exponent != 0L) { if ((exponent and 1L) != 0L) power *= base; exponent = exponent ushr 1; if (exponent != 0L) base *= base }
            result *= power
        }
        return result * sign
    }
    /** Truncate toward zero and retain the low 32 bits, as required by BigDecimal.intValue(). */
    fun intValue(): Int {
        var result = 0
        val end = if (scale >= 0) (magnitude.length.toLong() - scale).coerceAtLeast(0).toInt() else magnitude.length
        for (i in 0 until end) result = result * 10 + (magnitude[i] - '0')
        if (scale < 0) {
            var exponent = -scale.toLong(); var base = 10; var power = 1
            while (exponent != 0L) { if ((exponent and 1L) != 0L) power *= base; exponent = exponent ushr 1; if (exponent != 0L) base *= base }
            result *= power
        }
        return result * sign
    }
    override fun compareTo(other: UnitDecimal): Int {
        if (sign != other.sign) return sign.compareTo(other.sign)
        if (sign == 0) return 0
        val adjusted = magnitude.length.toLong() - scale
        val otherAdjusted = other.magnitude.length.toLong() - other.scale
        if (adjusted != otherAdjusted) return adjusted.compareTo(otherAdjusted) * sign
        val width = maxOf(magnitude.length, other.magnitude.length)
        return magnitude.padEnd(width, '0').compareTo(other.magnitude.padEnd(width, '0')).coerceIn(-1, 1) * sign
    }
    override fun equals(other: Any?): Boolean = other is UnitDecimal && sign == other.sign && magnitude == other.magnitude && scale == other.scale
    override fun hashCode(): Int = 31 * (31 * sign + magnitude.hashCode()) + scale
    override fun toString(): String {
        val adjusted = magnitude.length.toLong() - 1 - scale
        val body = if (scale >= 0 && adjusted >= -6) {
            when {
                scale == 0 -> magnitude
                scale < magnitude.length -> magnitude.substring(0, magnitude.length - scale) + "." + magnitude.substring(magnitude.length - scale)
                else -> "0." + "0".repeat(scale - magnitude.length) + magnitude
            }
        } else {
            val coefficient = magnitude[0].toString() + if (magnitude.length > 1) "." + magnitude.substring(1) else ""
            coefficient + "E" + (if (adjusted >= 0) "+" else "") + adjusted
        }
        return (if (sign < 0) "-" else "") + body
    }
    companion object {
        val ZERO = UnitDecimal(0, "0", 0)
        val ONE = UnitDecimal(1, "1", 0)
        fun valueOf(value: Long): UnitDecimal = UnitDecimal(value.toString())
        private fun checkedScale(value: Long, zero: Boolean): Int {
            if (zero) return value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            if (value > Int.MAX_VALUE) throw ArithmeticException("Underflow")
            if (value < Int.MIN_VALUE) throw ArithmeticException("Overflow")
            return value.toInt()
        }
        private fun of(sign: Int, magnitude: String, scale: Int): UnitDecimal {
            val normalized = magnitude.trimStart('0').ifEmpty { "0" }
            return UnitDecimal(if (normalized == "0") 0 else sign, normalized, scale)
        }
        private fun parse(text: String): UnitDecimal {
            if (text.isEmpty()) throw NumberFormatException()
            var i = 0; var sign = 1
            if (text[i] == '-' || text[i] == '+') { if (text[i] == '-') sign = -1; i++ }
            val digits = StringBuilder(); var fraction = 0; var dot = false
            while (i < text.length && text[i] != 'e' && text[i] != 'E') {
                val c = text[i++]
                if (c == '.' && !dot) dot = true else {
                    if (c !in '0'..'9') throw NumberFormatException()
                    digits.append(c); if (dot) fraction++
                }
            }
            if (digits.isEmpty()) throw NumberFormatException()
            var exponent = 0L
            if (i < text.length) {
                i++; if (i == text.length) throw NumberFormatException()
                var exponentSign = 1
                if (text[i] == '+' || text[i] == '-') { if (text[i] == '-') exponentSign = -1; i++ }
                if (i == text.length) throw NumberFormatException()
                while (i < text.length) {
                    val c = text[i++]; if (c !in '0'..'9') throw NumberFormatException()
                    exponent = exponent * 10 + (c - '0')
                    if (exponent > Int.MAX_VALUE.toLong() * 2 + 1) throw NumberFormatException()
                }
                exponent *= exponentSign
            }
            val scale = fraction.toLong() - exponent
            if (scale !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) throw NumberFormatException()
            return of(sign, digits.toString(), scale.toInt())
        }
        private fun zeros(value: String, count: Long): String {
            if (value == "0") return value
            if (count < 0 || count > Int.MAX_VALUE - value.length) throw ArithmeticException("BigInteger would overflow supported range")
            return value + "0".repeat(count.toInt())
        }
        private fun compareNatural(a: String, b: String): Int = if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b).coerceIn(-1, 1)
        private fun addNatural(a: String, b: String): String {
            val out = StringBuilder(); var i = a.lastIndex; var j = b.lastIndex; var carry = 0
            while (i >= 0 || j >= 0 || carry != 0) {
                val sum = (if (i >= 0) a[i--] - '0' else 0) + (if (j >= 0) b[j--] - '0' else 0) + carry
                out.append(('0'.code + sum % 10).toChar()); carry = sum / 10
            }
            return out.reverse().toString()
        }
        private fun subtractNatural(a: String, b: String): String {
            val out = StringBuilder(); var j = b.lastIndex; var borrow = 0
            for (i in a.lastIndex downTo 0) {
                var digit = a[i] - '0' - borrow - (if (j >= 0) b[j--] - '0' else 0)
                borrow = if (digit < 0) 1 else 0; if (digit < 0) digit += 10
                out.append(('0'.code + digit).toChar())
            }
            return out.reverse().toString().trimStart('0').ifEmpty { "0" }
        }
        private fun multiplyNatural(a: String, b: String): String {
            if (a == "0" || b == "0") return "0"
            val out = IntArray(a.length + b.length)
            for (i in a.lastIndex downTo 0) for (j in b.lastIndex downTo 0) {
                val pos = i + j + 1; val product = out[pos] + (a[i] - '0') * (b[j] - '0')
                out[pos] = product % 10; out[pos - 1] += product / 10
            }
            return out.joinToString("").trimStart('0')
        }
        private fun divideNatural(a: String, b: String): Pair<String, String> {
            val out = StringBuilder(); var remainder = "0"
            for (digit in a) {
                remainder = (if (remainder == "0") digit.toString() else remainder + digit).trimStart('0').ifEmpty { "0" }
                var quotient = 0
                while (compareNatural(remainder, b) >= 0) { remainder = subtractNatural(remainder, b); quotient++ }
                out.append(('0'.code + quotient).toChar())
            }
            return out.toString().trimStart('0').ifEmpty { "0" } to remainder
        }
        private fun roundUp(quotient: String, remainder: String, halfway: String): Boolean {
            val comparison = compareNatural(remainder, halfway)
            return comparison > 0 || comparison == 0 && (quotient.last() - '0') % 2 != 0
        }
    }
}
