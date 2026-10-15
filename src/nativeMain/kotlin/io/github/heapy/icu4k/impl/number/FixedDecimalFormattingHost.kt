package io.github.heapy.icu4k.impl.number

internal actual fun fixedDecimalPlainFormat(value: Double, fraction: Int): String = FixedDecimalFormatting.plain(value, fraction)
internal actual fun fixedDecimalScientificFormat(value: Double): String = FixedDecimalFormatting.scientific(value)

internal actual fun fixedDecimalSerializationFailure(): Nothing = throw UnsupportedOperationException("FixedDecimal is not serializable")
