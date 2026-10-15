package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.decimalValue

internal actual fun scaleHasUnitCoefficient(value: BigDecimal): Boolean = value.decimalValue().let { it.sign > 0 && it.magnitude == "1" }
