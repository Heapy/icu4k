package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.number.CommonDoubleToDecimal

internal actual fun measureNumberText(number: Number, kind: Int): String = when (number) {
    is Double -> CommonDoubleToDecimal.format(number)
    is Float -> CommonDoubleToDecimal.format(number)
    else -> number.toString()
}
internal actual fun measureNumbersEqual(first: Number, second: Number, firstKind: Int, secondKind: Int): Boolean = first == second || first.toDouble() == second.toDouble()

internal actual fun measureInputNumber(number: Number, kind: Int): Number = number
