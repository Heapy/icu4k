// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ResourceCacheLock
internal actual fun numberFormatDoubleValue(value: Number): Double = value.toDouble()
internal actual fun <T> numberFormatCurrencyLocked(owner: NumberFormat, portable: ResourceCacheLock?, block: () -> T): T = synchronized(owner, block)
internal actual fun numberFormatWriteFields(stream: PluralObjectOutputStream) = stream.defaultWriteObject()
internal actual fun numberFormatReadFields(stream: PluralObjectInputStream) = stream.defaultReadObject()
internal actual fun invalidNumberFormatStream(): Nothing = throw java.io.InvalidObjectException("Digit count range invalid")
