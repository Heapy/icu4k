// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

// Java Number supplies concrete byteValue/shortValue; Kotlin actual matching sees abstract built-ins.
@Suppress("EXPECT_ACTUAL_INCOMPATIBLE_CLASS_SCOPE")
actual typealias BigDecimal = java.math.BigDecimal
internal actual fun decimalValueOf(value: Long, scale: Int): BigDecimal = BigDecimal.valueOf(value, scale)
internal actual fun decimalValueOfDouble(value: Double): BigDecimal = BigDecimal.valueOf(value)
internal actual fun BigDecimal.decimalValue(): io.github.heapy.icu4k.impl.units.UnitDecimal =
    io.github.heapy.icu4k.impl.units.UnitDecimal("${unscaledValue()}E${-scale().toLong()}")
internal actual fun decimalFromValue(value: io.github.heapy.icu4k.impl.units.UnitDecimal): BigDecimal =
    BigDecimal(java.math.BigInteger((if (value.sign < 0) "-" else "") + value.magnitude), value.scale)
