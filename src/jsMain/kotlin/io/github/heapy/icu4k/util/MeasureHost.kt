package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.number.CommonDoubleToDecimal

internal actual fun measureNumberText(number: Number, kind: Int): String = when (kind) {
    1 -> CommonDoubleToDecimal.format(number.toDouble())
    2 -> CommonDoubleToDecimal.format(number.toFloat())
    else -> when (number) {
        is Int -> number.toString()
        is Double -> CommonDoubleToDecimal.format(number)
        else -> number.toString()
    }
}
internal actual fun measureNumbersEqual(first: Number, second: Number, firstKind: Int, secondKind: Int): Boolean {
    // Java's Float/Double boxed NaNs compare equal within each type, but not across types.
    if (firstKind != 0 && secondKind != 0 && first is Double && second is Double && first.isNaN() && second.isNaN()) return firstKind == secondKind
    return first == second || first.toDouble() == second.toDouble()
}

// Kotlin/JS Float uses a JavaScript number; force the binary32 input before hashing/comparison.
internal actual fun measureInputNumber(number: Number, kind: Int): Number =
    if (kind == 2) Float.fromBits(number.toFloat().toRawBits()) else number
