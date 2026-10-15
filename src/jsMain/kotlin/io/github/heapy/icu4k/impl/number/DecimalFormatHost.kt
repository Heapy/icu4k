// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.util.BigDecimal
import kotlin.js.unsafeCast

internal actual fun assertDecimalFormatCore(condition: () -> Boolean) = Unit
internal actual fun formatNumberInput(value: Any?): Number {
    // Kotlin/JS's Number check/cast only recognizes primitives and Long. The portable decimal
    // directly extends Kotlin Number, so its superclass also recognizes arbitrary Number subclasses.
    val decimalClass = BigDecimal::class.js
    val numberClass = js("Object.getPrototypeOf(decimalClass.prototype).constructor")
    if (value !is Number && !(js("value instanceof numberClass") as Boolean)) throw IllegalArgumentException()
    return value.unsafeCast<Number>()
}
