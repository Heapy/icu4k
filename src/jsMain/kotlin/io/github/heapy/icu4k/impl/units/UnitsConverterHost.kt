package io.github.heapy.icu4k.impl.units
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.decimalFromValue
import io.github.heapy.icu4k.util.decimalValue
internal actual fun assertUnitsConverter(condition: () -> Boolean) = Unit
internal actual fun unitFactorPower(value: BigDecimal, power: Int, rounded: Boolean): BigDecimal =
    decimalFromValue(if (rounded) value.decimalValue().powRounded(power) else value.decimalValue().pow(power))

internal actual fun unitConversionMathContext(): io.github.heapy.icu4k.util.MathContext =
    io.github.heapy.icu4k.util.MathContext(34, io.github.heapy.icu4k.util.RoundingMode.HALF_EVEN)
