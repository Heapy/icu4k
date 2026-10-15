// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.util.BigDecimal

internal actual fun quantityFromNumber(number: Number?): DecimalQuantityCore = when (number) {
    is Long -> DecimalQuantityCore(number)
    is Int -> DecimalQuantityCore(number.toLong())
    is Float -> DecimalQuantityCore(number.toDouble())
    is Double -> DecimalQuantityCore(number.toDouble())
    is BigDecimal -> DecimalQuantityCore().also { it.setToBigDecimal(number) }
    else -> {
        val name = when (number) { is Byte -> "java.lang.Byte"; is Short -> "java.lang.Short"; else -> number!!::class.simpleName }
        throw IllegalArgumentException("Number is of an unsupported type: $name")
    }
}
