package io.github.heapy.icu4k.impl.number

internal actual fun javaCompatibleDoubleString(value: Double): String = CommonDoubleToDecimal.format(value)
internal actual fun roundPositiveDoubleToLong(value: Double): Long {
    val integer = value.toLong()
    return if (integer != Long.MAX_VALUE && value - integer.toDouble() >= 0.5) integer + 1 else integer
}
