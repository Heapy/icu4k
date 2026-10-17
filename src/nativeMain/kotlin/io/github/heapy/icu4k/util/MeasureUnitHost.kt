// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util
internal actual interface MeasureUnitExternalizable : ICUSerializable {
    actual fun writeExternal(out: MeasureUnitObjectOutput)
    actual fun readExternal(input: MeasureUnitObjectInput)
}
internal actual interface MeasureUnitObjectOutput {
    actual fun writeByte(value: Int)
    actual fun writeUTF(value: String?)
    actual fun writeShort(value: Int)
}
internal actual interface MeasureUnitObjectInput {
    actual fun readByte(): Byte
    actual fun readUTF(): String
    actual fun readShort(): Short
    actual fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

internal actual fun measureUnitSerialProxy(type: String?, subType: String?): Any = throw UnsupportedOperationException("Java object serialization is unavailable")

internal actual fun invalidTimeUnitIndex(index: Int): Nothing = throw IllegalArgumentException("Bad index: $index")
