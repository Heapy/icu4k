// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import io.github.heapy.icu4k.impl.units.UnitDecimal

object BigDecimals {
    @JvmField val ZERO = decimalValueOf(0, 0)
    @JvmField val ONE = decimalValueOf(1, 0)
    @JvmField val TEN = decimalValueOf(10, 0)
    @JvmStatic fun valueOf(value: Long): BigDecimal = decimalValueOf(value, 0)
    @JvmStatic fun valueOf(value: Long, scale: Int): BigDecimal = decimalValueOf(value, scale)
    @JvmStatic fun valueOf(value: Double): BigDecimal = decimalValueOfDouble(value)
}
internal expect fun decimalValueOf(value: Long, scale: Int): BigDecimal
internal expect fun decimalValueOfDouble(value: Double): BigDecimal
internal expect fun BigDecimal.decimalValue(): UnitDecimal
internal expect fun decimalFromValue(value: UnitDecimal): BigDecimal
