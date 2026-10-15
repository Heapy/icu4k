package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.decimalValue

internal actual fun precisionIncrementKind(reduced: BigDecimal): Int {
    val value = reduced.decimalValue()
    var digit = 0
    for (c in value.magnitude) digit = digit * 10 + (c - '0')
    digit *= value.sign
    return if (digit == 1) 1 else if (digit == 5) 5 else 0
}
internal actual fun assertPrecision(condition: () -> Boolean) = Unit
