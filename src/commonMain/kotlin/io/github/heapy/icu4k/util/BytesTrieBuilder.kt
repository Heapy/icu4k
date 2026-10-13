// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Builds ICU compact byte tries using the original backwards serialization. */
class BytesTrieBuilder : StringTrieBuilder() {
    private var bytes: ByteArray? = null
    private var length = 0
    private val intBytes = ByteArray(5)
    private class BytesAsCharSequence(val data: ByteArray, override val length: Int) : CharSequence {
        override fun get(index: Int): Char = (data[index].toInt() and 255).toChar()
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = buildString {
            for (i in startIndex until endIndex) append(this@BytesAsCharSequence[i])
        }
        override fun toString(): String = subSequence(0, length).toString()
    }
    fun add(sequence: ByteArray, length: Int, value: Int): BytesTrieBuilder {
        addImpl(BytesAsCharSequence(sequence, length), value)
        return this
    }
    private fun buildBytes(option: Option) {
        if (bytes == null) bytes = ByteArray(1024)
        buildImpl(option)
    }
    fun build(option: Option): BytesTrie {
        buildBytes(option)
        return BytesTrie(bytes!!, bytes!!.size - length)
    }
    /** Shared portable cursor replacing the returned java.nio.ByteBuffer. */
    fun buildByteBuffer(option: Option): BinaryBuffer {
        buildBytes(option)
        return BinaryBuffer(bytes!!).also { it.position = bytes!!.size - length }
    }
    fun buildByteArray(option: Option): ByteArray {
        val buffer = buildByteBuffer(option)
        return buffer.readBytes(buffer.remaining)
    }
    fun clear(): BytesTrieBuilder { clearImpl(); bytes = null; length = 0; return this }
    override fun matchNodesCanHaveValues(): Boolean = false
    override fun getMaxBranchLinearSubNodeLength(): Int = 5
    override fun getMinLinearMatch(): Int = 0x10
    override fun getMaxLinearMatchLength(): Int = 16
    private fun ensureCapacity(size: Int) {
        val old = bytes!!
        if (size <= old.size) return
        var capacity = old.size
        do { capacity *= 2 } while (capacity <= size)
        bytes = ByteArray(capacity).also { old.copyInto(it, capacity - length, old.size - length, old.size) }
    }
    override fun write(unit: Int): Int {
        ensureCapacity(length + 1); ++length
        bytes!![bytes!!.size - length] = unit.toByte()
        return length
    }
    override fun write(offset: Int, length: Int): Int {
        ensureCapacity(this.length + length); this.length += length
        val start = bytes!!.size - this.length
        for (i in 0 until length) bytes!![start + i] = strings[offset + i].code.toByte()
        return this.length
    }
    private fun write(units: ByteArray, count: Int): Int {
        ensureCapacity(length + count); length += count
        units.copyInto(bytes!!, bytes!!.size - length, 0, count)
        return length
    }
    override fun writeValueAndFinal(value: Int, isFinal: Boolean): Int {
        val final = if (isFinal) 1 else 0
        if (value in 0..64) return write(((0x10 + value) shl 1) or final)
        var count = 1
        if (value < 0 || value > 0xffffff) {
            intBytes[0] = 0x7f
            intBytes[1] = (value shr 24).toByte(); intBytes[2] = (value shr 16).toByte()
            intBytes[3] = (value shr 8).toByte(); intBytes[4] = value.toByte(); count = 5
        } else {
            if (value <= 0x1aff) intBytes[0] = (0x51 + (value shr 8)).toByte()
            else {
                if (value <= 0x11ffff) intBytes[0] = (0x6c + (value shr 16)).toByte()
                else { intBytes[0] = 0x7e; intBytes[1] = (value shr 16).toByte(); count = 2 }
                intBytes[count++] = (value shr 8).toByte()
            }
            intBytes[count++] = value.toByte()
        }
        intBytes[0] = ((intBytes[0].toInt() shl 1) or final).toByte()
        return write(intBytes, count)
    }
    override fun writeValueAndType(hasValue: Boolean, value: Int, node: Int): Int {
        val offset = write(node)
        return if (hasValue) writeValueAndFinal(value, false) else offset
    }
    override fun writeDeltaTo(jumpTarget: Int): Int {
        val delta = length - jumpTarget
        return if (delta <= 0xbf) write(delta) else write(intBytes, internalEncodeDelta(delta, intBytes))
    }
    companion object {
        fun internalEncodeDelta(delta: Int, output: ByteArray): Int {
            if (delta <= 0xbf) { output[0] = delta.toByte(); return 1 }
            var count = 1
            if (delta <= 0x2fff) output[0] = (0xc0 + (delta shr 8)).toByte()
            else {
                if (delta <= 0xdffff) output[0] = (0xf0 + (delta shr 16)).toByte()
                else {
                    if (delta <= 0xffffff) output[0] = 0xfe.toByte()
                    else { output[0] = 0xff.toByte(); output[1] = (delta shr 24).toByte(); count = 2 }
                    output[count++] = (delta shr 16).toByte()
                }
                output[count++] = (delta shr 8).toByte()
            }
            output[count++] = delta.toByte()
            return count
        }
    }
}
