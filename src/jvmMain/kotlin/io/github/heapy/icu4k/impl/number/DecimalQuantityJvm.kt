// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.UnitDecimal
import java.math.BigInteger

internal actual fun quantityFromNumber(number: Number?): DecimalQuantityCore = when (number) {
    is Long -> DecimalQuantityCore(number)
    is Int -> DecimalQuantityCore(number.toLong())
    is Float -> DecimalQuantityCore(number.toDouble())
    is Double -> DecimalQuantityCore(number)
    is BigInteger -> DecimalQuantityCore(UnitDecimal(number.toString()))
    is java.math.BigDecimal -> DecimalQuantityCore().also { it.setToBigDecimal(number) }
    is io.github.heapy.icu4k.math.BigDecimal -> DecimalQuantityCore().also { it.setToBigDecimal(number.toBigDecimal()) }
    else -> throw IllegalArgumentException("Number is of an unsupported type: ${number!!.javaClass.name}")
}

fun DecimalQuantity_DualStorageBCD(input: BigInteger?): DecimalQuantity_DualStorageBCD = DecimalQuantity_DualStorageBCD(input as Number?)
fun DecimalQuantity_AbstractBCD.setToBigInteger(input: BigInteger?) { setToBigDecimal(java.math.BigDecimal(input)) }
