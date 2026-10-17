// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
internal actual fun bidiByteAt(data: ByteArray?, index: Int): Byte = data!![index]
internal actual fun bidiCharAt(data: CharArray?, index: Int): Char = data!![index]
internal actual fun bidiStringCharAt(text: String, index: Int): Char = text[index]
internal actual fun bidiShortAt(data: ShortArray?, index: Int): Short = data!![index]
internal actual fun <T> bidiArrayAt(data: Array<T>?, index: Int): T = data!![index]
internal actual fun bidiIntAt(data: IntArray?, index: Int): Int = data!![index]
