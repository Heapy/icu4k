// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("UNCHECKED_CAST")
package io.github.heapy.icu4k.text
import io.github.heapy.icu4k.util.*
internal actual interface MeasureFormatExternalizable : ICUSerializable {
    actual fun writeExternal(output: MeasureFormatObjectOutput)
    actual fun readExternal(input: MeasureFormatObjectInput)
}
internal actual interface MeasureFormatObjectOutput {
    actual fun writeByte(value: Int)
    actual fun writeUTF(value: String)
    actual fun writeObject(value: Any?)
}
internal actual interface MeasureFormatObjectInput {
    actual fun readByte(): Byte
    actual fun readUTF(): String
    actual fun readObject(): Any?
}
internal actual fun invalidMeasureFormatStream(message: String): Nothing = throw IllegalArgumentException(message)
internal actual fun measureFormatOptionalValues(value: Any?): MutableMap<Any?,Any?>? = value as HashMap<Any?,Any?>?
internal actual fun measureFormatIsMeasureArray(value: Array<*>): Boolean = value.all { it==null || it is Measure }
