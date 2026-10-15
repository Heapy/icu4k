// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
import io.github.heapy.icu4k.util.BigDecimal
import kotlin.js.unsafeCast
internal actual fun assertRbnfSubstitutionHash() {}
internal actual fun rbnfIntValue(value: Number): Int = when (value) {
    is Double -> value.toInt()
    is Long -> value.toInt()
    else -> value.unsafeCast<BigDecimal>().toInt()
}
internal actual fun rbnfLongValue(value: Number): Long = when (value) {
    is Double -> value.toLong()
    is Long -> value
    else -> value.unsafeCast<BigDecimal>().toLong()
}
