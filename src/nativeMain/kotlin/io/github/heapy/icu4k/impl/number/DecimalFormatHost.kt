// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

internal actual fun assertDecimalFormatCore(condition: () -> Boolean) = Unit
internal actual fun formatNumberInput(value: Any?): Number {
    if (value !is Number) throw IllegalArgumentException()
    return value
}
