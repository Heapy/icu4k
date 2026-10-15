package io.github.heapy.icu4k.impl.number

internal actual fun fixedDecimalPlainFormat(value: Double, fraction: Int): String = String.format(java.util.Locale.ROOT, "%.${fraction}f", value)
internal actual fun fixedDecimalScientificFormat(value: Double): String = String.format(java.util.Locale.ENGLISH, "%1.15e", value)

internal actual fun fixedDecimalSerializationFailure(): Nothing = throw java.io.NotSerializableException()
