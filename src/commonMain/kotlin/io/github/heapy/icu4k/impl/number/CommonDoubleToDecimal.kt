// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.UnitDecimal

/** Java's shortest-decimal selection contract, evaluated with exact binary rounding intervals. */
internal object CommonDoubleToDecimal {
    private val fives by lazy { powers(5, 1076) }
    private val twos by lazy { powers(2, 1024) }
    private fun powers(base: Long, maximum: Int): List<UnitDecimal> = buildList {
        add(UnitDecimal.ONE)
        val factor = UnitDecimal.valueOf(base)
        repeat(maximum) { add(last().multiply(factor)) }
    }
    private fun binary(mantissa: Long, exponent: Int): UnitDecimal {
        val value = UnitDecimal.valueOf(mantissa)
        if (exponent >= 0) return value.multiply(twos[exponent])
        val product = value.multiply(fives[-exponent])
        return UnitDecimal(product.magnitude + "E" + exponent)
    }
    fun exact(value: Double): UnitDecimal {
        val raw = value.toRawBits()
        val bits = raw and Long.MAX_VALUE
        if (bits >= 0x7ff0000000000000L) throw NumberFormatException("Infinite or NaN")
        if (bits == 0L) return UnitDecimal.ZERO
        val biased = (bits ushr 52).toInt()
        val fraction = bits and 0x000fffffffffffffL
        val mantissa = if (biased == 0) fraction else fraction or 0x0010000000000000L
        val exponent = if (biased == 0) -1074 else biased - 1075
        var result = binary(mantissa, exponent).stripTrailingZeros()
        if (result.scale < 0) result = result.setScale(0, UnitDecimal.Rounding.UNNECESSARY)
        return if (raw < 0) result.negate() else result
    }
    fun format(value: Double): String {
        val raw = value.toRawBits()
        val bits = raw and Long.MAX_VALUE
        if (bits > 0x7ff0000000000000L) return "NaN"
        val sign = if (raw < 0) "-" else ""
        if (bits == 0x7ff0000000000000L) return sign + "Infinity"
        if (bits == 0L) return sign + "0.0"
        val biased = (bits ushr 52).toInt()
        val fraction = bits and 0x000fffffffffffffL
        val mantissa = if (biased == 0) fraction else fraction or 0x0010000000000000L
        val exponent = if (biased == 0) -1074 else biased - 1075
        return formatBinary(mantissa, exponent, fraction == 0L && biased > 1, sign, 17)
    }
    fun format(value: Float): String {
        val raw = value.toRawBits()
        val bits = raw and Int.MAX_VALUE
        if (bits > 0x7f800000) return "NaN"
        val sign = if (raw < 0) "-" else ""
        if (bits == 0x7f800000) return sign + "Infinity"
        if (bits == 0) return sign + "0.0"
        val biased = bits ushr 23
        val fraction = bits and 0x007fffff
        val mantissa = (if (biased == 0) fraction else fraction or 0x00800000).toLong()
        val exponent = if (biased == 0) -149 else biased - 150
        return formatBinary(mantissa, exponent, fraction == 0 && biased > 1, sign, 9)
    }
    private fun formatBinary(mantissa: Long, exponent: Int, asymmetricLower: Boolean, sign: String, maxDigits: Int): String {
        val exact = binary(mantissa, exponent)
        val lower = if (asymmetricLower) binary(4 * mantissa - 1, exponent - 2) else binary(2 * mantissa - 1, exponent - 1)
        val upper = binary(2 * mantissa + 1, exponent - 1)
        val closed = (mantissa and 1L) == 0L
        fun inInterval(candidate: UnitDecimal): Boolean {
            val lo = candidate.compareTo(lower); val hi = candidate.compareTo(upper)
            return (lo > 0 || closed && lo == 0) && (hi < 0 || closed && hi == 0)
        }
        val adjusted = exact.precision - exact.scale - 1
        fun choose(precision: Int): UnitDecimal? {
            val scale = precision - 1 - adjusted
            val down = exact.setScale(scale, UnitDecimal.Rounding.DOWN)
            val up = exact.setScale(scale, UnitDecimal.Rounding.UP)
            val downFits = inInterval(down); val upFits = inInterval(up)
            if (!downFits) return if (upFits) up else null
            if (!upFits) return down
            val comparison = exact.subtract(down).compareTo(up.subtract(exact))
            return if (comparison < 0 || comparison == 0 && (down.magnitude.last() - '0') % 2 == 0) down else up
        }
        for (precision in 1..maxDigits) {
            val candidate = choose(precision) ?: continue
            val selected = (if (precision == 1) choose(2) ?: candidate else candidate).stripTrailingZeros()
            val digits = selected.magnitude
            val point = digits.length - selected.scale
            val body = when {
                point in 1..7 -> if (point >= digits.length) digits + "0".repeat(point - digits.length) + ".0" else digits.substring(0, point) + "." + digits.substring(point)
                point in -2..0 -> "0." + "0".repeat(-point) + digits
                else -> digits[0] + "." + (digits.substring(1).ifEmpty { "0" }) + "E" + (point - 1)
            }
            return sign + body
        }
        error("No decimal in binary rounding interval")
    }
}
