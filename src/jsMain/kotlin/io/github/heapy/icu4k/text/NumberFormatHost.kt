// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ResourceCacheLock
import io.github.heapy.icu4k.util.BigDecimal
import kotlin.js.unsafeCast

internal actual fun numberFormatDoubleValue(value: Number): Double =
    if (value is Double || value is Long) value.toDouble()
    // A concrete override uses virtual dispatch instead of Kotlin/JS's generic Number coercion.
    else value.unsafeCast<BigDecimal>().toDouble()
internal actual fun <T> numberFormatCurrencyLocked(owner: NumberFormat, portable: ResourceCacheLock?, block: () -> T): T = block()
internal actual fun numberFormatWriteFields(stream: PluralObjectOutputStream): Unit = throw UnsupportedOperationException("Java serialization is available on JVM")
internal actual fun numberFormatReadFields(stream: PluralObjectInputStream): Unit = throw UnsupportedOperationException("Java serialization is available on JVM")
internal actual fun invalidNumberFormatStream(): Nothing = throw IllegalArgumentException("Digit count range invalid")
