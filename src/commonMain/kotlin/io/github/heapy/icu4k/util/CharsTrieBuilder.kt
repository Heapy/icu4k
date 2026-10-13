// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Builds ICU compact character tries with original FAST/SMALL node sharing. */
class CharsTrieBuilder : StringTrieBuilder() {
    private var chars: CharArray? = null
    private var length = 0
    private val intUnits = CharArray(3)
    fun add(s: CharSequence, value: Int): CharsTrieBuilder { addImpl(s, value); return this }
    fun build(option: Option): CharsTrie = CharsTrie(buildCharSequence(option), 0)
    fun buildCharSequence(option: Option): CharSequence {
        if (chars == null) chars = CharArray(1024)
        buildImpl(option)
        return CharArrayView(chars!!, chars!!.size - length, length)
    }
    fun clear(): CharsTrieBuilder { clearImpl(); chars = null; length = 0; return this }
    override fun matchNodesCanHaveValues(): Boolean = true
    override fun getMaxBranchLinearSubNodeLength(): Int = 5
    override fun getMinLinearMatch(): Int = 0x30
    override fun getMaxLinearMatchLength(): Int = 16
    private fun ensureCapacity(size: Int) {
        val old = chars!!
        if (size <= old.size) return
        var capacity = old.size
        do { capacity *= 2 } while (capacity <= size)
        chars = CharArray(capacity).also { old.copyInto(it, capacity - length, old.size - length, old.size) }
    }
    override fun write(unit: Int): Int {
        ensureCapacity(length + 1); ++length
        chars!![chars!!.size - length] = unit.toChar()
        return length
    }
    override fun write(offset: Int, length: Int): Int {
        ensureCapacity(this.length + length); this.length += length
        val start = chars!!.size - this.length
        for (i in 0 until length) chars!![start + i] = strings[offset + i]
        return this.length
    }
    private fun write(units: CharArray, count: Int): Int {
        ensureCapacity(length + count); length += count
        units.copyInto(chars!!, chars!!.size - length, 0, count)
        return length
    }
    override fun writeValueAndFinal(value: Int, isFinal: Boolean): Int {
        val final = if (isFinal) 0x8000 else 0
        if (value in 0..0x3fff) return write(value or final)
        val count: Int
        if (value < 0 || value > 0x3ffeffff) {
            intUnits[0] = 0x7fff.toChar(); intUnits[1] = (value shr 16).toChar(); intUnits[2] = value.toChar(); count = 3
        } else {
            intUnits[0] = (0x4000 + (value shr 16)).toChar(); intUnits[1] = value.toChar(); count = 2
        }
        intUnits[0] = (intUnits[0].code or final).toChar()
        return write(intUnits, count)
    }
    override fun writeValueAndType(hasValue: Boolean, value: Int, node: Int): Int {
        if (!hasValue) return write(node)
        val count: Int
        if (value < 0 || value > 0xfdffff) {
            intUnits[0] = 0x7fc0.toChar(); intUnits[1] = (value shr 16).toChar(); intUnits[2] = value.toChar(); count = 3
        } else if (value <= 255) {
            intUnits[0] = ((value + 1) shl 6).toChar(); count = 1
        } else {
            intUnits[0] = (0x4040 + ((value shr 10) and 0x7fc0)).toChar(); intUnits[1] = value.toChar(); count = 2
        }
        intUnits[0] = (intUnits[0].code or node).toChar()
        return write(intUnits, count)
    }
    override fun writeDeltaTo(jumpTarget: Int): Int {
        val delta = length - jumpTarget
        if (delta <= 0xfbff) return write(delta)
        var count: Int
        if (delta <= 0x3feffff) { intUnits[0] = (0xfc00 + (delta shr 16)).toChar(); count = 1 }
        else { intUnits[0] = 0xffff.toChar(); intUnits[1] = (delta shr 16).toChar(); count = 2 }
        intUnits[count++] = delta.toChar()
        return write(intUnits, count)
    }
    private class CharArrayView(val data: CharArray, val offset: Int, override val length: Int) : CharSequence {
        override fun get(index: Int): Char {
            if (index !in 0 until length) throw IndexOutOfBoundsException()
            return data[offset + index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            if (startIndex < 0 || endIndex < startIndex || endIndex > length) throw IndexOutOfBoundsException()
            return CharArrayView(data, offset + startIndex, endIndex - startIndex)
        }
        override fun toString(): String = data.concatToString(offset, offset + length)
    }
}
