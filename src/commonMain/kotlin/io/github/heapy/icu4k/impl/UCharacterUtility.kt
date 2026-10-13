// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Primitive helpers for ICU character-name data. Byte characters are unsigned Latin-1. */
internal object UCharacterUtility {
    fun isNonCharacter(ch: Int): Boolean = ch and 0xfffe == 0xfffe || ch in 0xfdd0..0xfdef
    fun toInt(msc: Char, lsc: Char): Int = (msc.code shl 16) or lsc.code
    private fun byteAt(array: ByteArray, index: Int): Byte {
        if (index !in array.indices) throw IndexOutOfBoundsException("Byte index: $index")
        return array[index]
    }
    fun getNullTermByteSubString(str: StringBuilder, array: ByteArray, initialIndex: Int): Int {
        var index = initialIndex
        while (true) {
            val b = byteAt(array, index++)
            if (b == 0.toByte()) return index
            str.append((b.toInt() and 255).toChar())
        }
    }
    fun compareNullTermByteSubString(str: String, array: ByteArray, initialStringIndex: Int, initialArrayIndex: Int): Int {
        var si = initialStringIndex
        var ai = initialArrayIndex
        while (true) {
            val b = byteAt(array, ai++)
            if (b == 0.toByte()) return si
            if (si == str.length) return -1
            if (si !in str.indices) throw IndexOutOfBoundsException("String index: $si")
            if (str[si] != (b.toInt() and 255).toChar()) return -1
            ++si
        }
    }
    fun skipNullTermByteSubString(array: ByteArray, initialIndex: Int, skipcount: Int): Int {
        var index = initialIndex
        for (i in 0 until skipcount) while (byteAt(array, index++) != 0.toByte()) { }
        return index
    }
    fun skipByteSubString(array: ByteArray, index: Int, length: Int, skipend: Byte): Int {
        var result = 0
        while (result < length) {
            if (byteAt(array, index + result) == skipend) return result + 1
            ++result
        }
        return result
    }
}
