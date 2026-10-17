// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream
import io.github.heapy.icu4k.text.RbnfObjectStreamField

internal expect fun timeStateSerialFields(kind: String): Array<RbnfObjectStreamField>
internal expect fun timeStateWriteFields(output: PluralObjectOutputStream, kind: String, values: Map<String, Any?>)
internal expect fun timeStateReadFields(input: PluralObjectInputStream, kind: String): Map<String, Any?>
