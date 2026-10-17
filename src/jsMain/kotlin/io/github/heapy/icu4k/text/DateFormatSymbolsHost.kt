// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
import kotlin.reflect.KClass
internal actual fun dateSymbolsClassName(type: KClass<*>): String = type.simpleName ?: type.toString()

internal actual fun dateSymbolsSerializationFields(): Array<RbnfObjectStreamField> = emptyArray()
internal actual fun dateSymbolsWrite(output: PluralObjectOutputStream,owner: DateFormatSymbols): Unit = throw UnsupportedOperationException("Java serialization is available on JVM")
internal actual fun dateSymbolsRead(input: PluralObjectInputStream,owner: DateFormatSymbols): Unit = throw UnsupportedOperationException("Java serialization is available on JVM")
