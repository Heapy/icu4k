package io.github.heapy.icu4k.impl.units

internal actual fun <T> unitArrayElement(values: Array<out T>, index: Int): T {
    if (index !in values.indices) throw IndexOutOfBoundsException("Index $index out of bounds for length ${values.size}")
    return values[index]
}
internal actual fun parseUnitConstant(value: String): Long {
    return parseUnitConstantPortable(value)
}

internal actual fun assertUnitIdentifierPower(power: Int) {}
