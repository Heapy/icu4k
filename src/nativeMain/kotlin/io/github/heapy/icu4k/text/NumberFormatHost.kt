// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ResourceCacheLock
internal actual fun numberFormatDoubleValue(value: Number): Double = value.toDouble()
internal actual fun <T> numberFormatCurrencyLocked(owner: NumberFormat, portable: ResourceCacheLock?, block: () -> T): T = portable!!.withLock(block)
internal actual fun numberFormatWriteFields(stream: PluralObjectOutputStream): Unit = throw UnsupportedOperationException("Java serialization is available on JVM")
internal actual fun numberFormatReadFields(stream: PluralObjectInputStream): Unit = throw UnsupportedOperationException("Java serialization is available on JVM")
internal actual fun invalidNumberFormatStream(): Nothing = throw IllegalArgumentException("Digit count range invalid")
