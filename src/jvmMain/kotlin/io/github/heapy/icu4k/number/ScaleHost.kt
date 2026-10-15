package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.util.BigDecimal

internal actual fun scaleHasUnitCoefficient(value: BigDecimal): Boolean = value.unscaledValue().equals(java.math.BigInteger.ONE)
