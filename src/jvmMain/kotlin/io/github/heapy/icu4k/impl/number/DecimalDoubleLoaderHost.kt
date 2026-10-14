package io.github.heapy.icu4k.impl.number

internal actual fun javaCompatibleDoubleString(value: Double): String = java.lang.Double.toString(value)
internal actual fun roundPositiveDoubleToLong(value: Double): Long = java.lang.Math.round(value)
