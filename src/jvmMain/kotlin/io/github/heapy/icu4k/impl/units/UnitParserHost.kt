package io.github.heapy.icu4k.impl.units

internal actual fun <T> unitArrayElement(values: Array<out T>, index: Int): T {
    return values[index]
}
internal actual fun parseUnitConstant(value: String): Long {
    val number = java.math.BigDecimal(value)
    if (number.scale() <= 0 && number >= java.math.BigDecimal.ZERO && number <= java.math.BigDecimal.valueOf(Long.MAX_VALUE)) return number.longValueExact()
    throw IllegalArgumentException("The unit constant value is not a valid non-negative long integer.")
}

internal actual fun assertUnitIdentifierPower(power: Int) {
    if (SingleUnitImpl::class.java.desiredAssertionStatus() && power <= 0) throw AssertionError("this function does not support the dimensionless single units")
}
