// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2002-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Reader for ICU serialized UnicodeSet code-point ranges. */
class USerializedSet {
    private var array: CharArray? = CharArray(8)
    private var arrayOffset = 0
    private var bmpLength = 0
    private var length = 0
    fun getSet(source: CharArray, start: Int): Boolean {
        array = null; arrayOffset = 0; bmpLength = 0; length = 0
        var index = start
        length = source[index++].code
        if (length and 0x8000 != 0) {
            length = length and 0x7fff
            if (source.size < index + 1 + length) { length = 0; throw IndexOutOfBoundsException() }
            bmpLength = source[index++].code
        } else {
            if (source.size < index + length) { length = 0; throw IndexOutOfBoundsException() }
            bmpLength = length
        }
        array = source.copyOfRange(index, index + length)
        return true
    }
    fun setToOne(codePoint: Int) {
        var c = codePoint
        if (c > 0x10ffff) return
        val data = array!!
        when {
            c < 0xffff -> {
                bmpLength = 2; length = 2
                data[0] = c.toChar(); data[1] = (c + 1).toChar()
            }
            c == 0xffff -> {
                bmpLength = 1; length = 3
                data[0] = 0xffff.toChar(); data[1] = 1.toChar(); data[2] = 0.toChar()
            }
            c < 0x10ffff -> {
                bmpLength = 0; length = 4
                data[0] = (c shr 16).toChar(); data[1] = c.toChar(); ++c
                data[2] = (c shr 16).toChar(); data[3] = c.toChar()
            }
            else -> {
                bmpLength = 0; length = 2
                data[0] = 0x10.toChar(); data[1] = 0xffff.toChar()
            }
        }
    }
    fun getRange(rangeIndex: Int, range: IntArray?): Boolean {
        if (rangeIndex < 0) return false
        if (array == null) array = CharArray(8)
        require(range != null && range.size >= 2)
        val data = array!!
        var index = rangeIndex * 2
        if (index < bmpLength) {
            range[0] = data[index++].code
            range[1] = when {
                index < bmpLength -> data[index].code - 1
                index < length -> ((data[index].code shl 16) or data[index + 1].code) - 1
                else -> 0x10ffff
            }
            return true
        }
        index = (index - bmpLength) * 2
        val supplementaryLength = length - bmpLength
        if (index >= supplementaryLength) return false
        val offset = arrayOffset + bmpLength
        range[0] = (data[offset + index].code shl 16) or data[offset + index + 1].code
        index += 2
        range[1] = if (index < supplementaryLength) ((data[offset + index].code shl 16) or data[offset + index + 1].code) - 1 else 0x10ffff
        return true
    }
    fun contains(c: Int): Boolean {
        if (c > 0x10ffff) return false
        if (c <= 0xffff) {
            var i = 0
            while (i < bmpLength && c.toChar() >= array!![i]) ++i
            return i and 1 != 0
        }
        val high = (c shr 16).toChar(); val low = c.toChar()
        var i = bmpLength
        while (i < length && (high > array!![i] || high == array!![i] && low >= array!![i + 1])) i += 2
        return (i + bmpLength) and 2 != 0
    }
    fun countRanges(): Int = (bmpLength + (length - bmpLength) / 2 + 1) / 2
}
