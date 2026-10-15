// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.UnitDecimal

internal object FixedDecimalFormatting {
    fun plain(value: Double, fraction: Int): String {
        if (fraction < 0) throw IllegalArgumentException("Invalid fraction precision")
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value < 0) "-Infinity" else "Infinity"
        val rounded = UnitDecimal(CommonDoubleToDecimal.format(value)).setScale(fraction, UnitDecimal.Rounding.HALF_UP)
        val digits = rounded.magnitude
        val body = when {
            fraction == 0 -> digits
            fraction < digits.length -> digits.substring(0, digits.length - fraction) + "." + digits.substring(digits.length - fraction)
            else -> "0." + "0".repeat(fraction - digits.length) + digits
        }
        return (if (value.toRawBits() < 0) "-" else "") + body
    }
    fun scientific(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value < 0) "-Infinity" else "Infinity"
        val sign = if (value.toRawBits() < 0) "-" else ""
        if (value == 0.0) return sign + "0.000000000000000e+00"
        val rounded = UnitDecimal(CommonDoubleToDecimal.format(value)).round(16, UnitDecimal.Rounding.HALF_UP)
        val exponent = rounded.precision - rounded.scale - 1
        val digits = rounded.magnitude.padEnd(16, '0')
        return sign + digits[0] + "." + digits.substring(1) + "e" + (if (exponent < 0) "-" else "+") + kotlin.math.abs(exponent).toString().padStart(2, '0')
    }
}
