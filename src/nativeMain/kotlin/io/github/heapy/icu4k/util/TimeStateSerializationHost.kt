// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util
import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream
import io.github.heapy.icu4k.text.RbnfObjectStreamField
internal actual fun timeStateSerialFields(kind: String): Array<RbnfObjectStreamField> = emptyArray()
internal actual fun timeStateWriteFields(output: PluralObjectOutputStream, kind: String, values: Map<String, Any?>): Unit = throw UnsupportedOperationException("Java serialization is only available on JVM")
internal actual fun timeStateReadFields(input: PluralObjectInputStream, kind: String): Map<String, Any?> = throw UnsupportedOperationException("Java serialization is only available on JVM")
