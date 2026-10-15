// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal actual fun decimalFormatClassName(value: DecimalFormat): String = value::class.qualifiedName ?: value::class.simpleName!!
internal actual fun decimalFormatWrite(stream: PluralObjectOutputStream, owner: DecimalFormat): Unit = throw UnsupportedOperationException("Java serialization is available on JVM")
internal actual fun decimalFormatRead(stream: PluralObjectInputStream, owner: DecimalFormat): Unit = throw UnsupportedOperationException("Java serialization is available on JVM")
