package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.util.BigDecimal

internal actual fun precisionIncrementKind(reduced: BigDecimal): Int {
    val digit = reduced.unscaledValue()
    return if (digit.toInt() == 1) 1 else if (digit.toInt() == 5) 5 else 0
}
internal actual fun assertPrecision(condition: () -> Boolean) {
    if (Precision::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
