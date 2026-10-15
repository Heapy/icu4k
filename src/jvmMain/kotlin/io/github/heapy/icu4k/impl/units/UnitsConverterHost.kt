package io.github.heapy.icu4k.impl.units
import io.github.heapy.icu4k.util.BigDecimal
internal actual fun assertUnitsConverter(condition: () -> Boolean) {
    if (UnitsConverter::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
internal actual fun unitFactorPower(value: BigDecimal, power: Int, rounded: Boolean): BigDecimal =
    if (rounded) value.pow(power, java.math.MathContext.DECIMAL128) else value.pow(power)

internal actual fun unitConversionMathContext(): io.github.heapy.icu4k.util.MathContext = java.math.MathContext.DECIMAL128
