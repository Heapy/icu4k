// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
internal actual fun bidiByteAt(data: ByteArray?, index: Int): Byte {
    val bytes = data!!
    if (index < 0 || index >= bytes.size) throw IndexOutOfBoundsException("Index $index out of bounds for length ${bytes.size}")
    return bytes[index]
}
internal actual fun bidiCharAt(data: CharArray?, index: Int): Char {
    val chars = data!!
    if (index < 0 || index >= chars.size) throw IndexOutOfBoundsException("Index $index out of bounds for length ${chars.size}")
    return chars[index]
}
internal actual fun bidiStringCharAt(text: String, index: Int): Char {
    if (index < 0 || index >= text.length) throw IndexOutOfBoundsException("String index out of range: $index")
    return text[index]
}
