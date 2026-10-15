// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Java numeric boundaries used by the rule-based formatter. */
internal object RbnfMath {
    /** Java Math.round: nearest integer, ties toward positive infinity, saturating to Long. */
    fun roundToLong(value: Double): Long {
        val bits = value.toRawBits()
        val biasedExponent = ((bits ushr 52) and 0x7ffL).toInt()
        val shift = 1074 - biasedExponent
        if (shift in 0..63) {
            var significand = (bits and 0x000fffffffffffffL) or 0x0010000000000000L
            if (bits < 0L) significand = -significand
            return ((significand shr shift) + 1L) shr 1
        }
        return when {
            value.isNaN() -> 0L
            value >= 9223372036854775808.0 -> Long.MAX_VALUE
            value <= -9223372036854775808.0 -> Long.MIN_VALUE
            else -> value.toLong()
        }
    }
    fun divide(value: Long, divisor: Long): Long {
        if (divisor == 0L) throw ArithmeticException("/ by zero")
        if (value == Long.MIN_VALUE && divisor == -1L) return Long.MIN_VALUE
        return value / divisor
    }
    fun remainder(value: Long, divisor: Long): Long {
        if (divisor == 0L) throw ArithmeticException("/ by zero")
        if (divisor == -1L) return 0L
        return value % divisor
    }
}
