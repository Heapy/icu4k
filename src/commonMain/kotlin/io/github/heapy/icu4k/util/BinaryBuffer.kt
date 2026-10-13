package io.github.heapy.icu4k.util

/**
 * Common byte-array cursor for ICU binary formats, replacing java.nio.ByteBuffer.
 * The underlying bytes are shared. Typed reads advance [position] only after
 * checking capacity. Trie decoders copy the data they retain.
 */
class BinaryBuffer(
    val bytes: ByteArray,
    var order: ByteOrder = ByteOrder.BIG_ENDIAN,
    val arrayOffset: Int = 0,
    val capacity: Int = bytes.size - arrayOffset,
) {
    init {
        require(arrayOffset >= 0 && capacity >= 0 && arrayOffset <= bytes.size - capacity) { "Invalid byte-array slice" }
    }
    enum class ByteOrder { BIG_ENDIAN, LITTLE_ENDIAN }
    var position: Int = 0
        set(value) {
            if (value !in 0..limit) throw IndexOutOfBoundsException("Invalid buffer position: $value")
            field = value
        }
    var limit: Int = capacity
        set(value) {
            if (value !in 0..capacity) throw IndexOutOfBoundsException("Invalid buffer limit: $value")
            field = value
            if (position > value) position = value
        }
    val remaining: Int get() = limit - position

    fun getByte(index: Int): Byte {
        if (index !in 0 until limit) throw IndexOutOfBoundsException("Invalid byte index: $index")
        return bytes[arrayOffset + index]
    }
    /** Shares the remaining bytes, with an independent cursor and byte order. */
    fun slice(): BinaryBuffer = BinaryBuffer(bytes, order, arrayOffset + position, remaining)

    fun requireRemaining(count: Int) {
        if (count < 0 || count > remaining) throw ICUUncheckedIOException("Buffer too short: need $count bytes, have $remaining")
    }
    fun writeByte(value: Int) {
        requireRemaining(1)
        bytes[arrayOffset + position++] = value.toByte()
    }
    fun writeShort(value: Int) {
        requireRemaining(2)
        if (order == ByteOrder.BIG_ENDIAN) {
            writeByte(value ushr 8); writeByte(value)
        } else {
            writeByte(value); writeByte(value ushr 8)
        }
    }
    fun writeInt(value: Int) {
        requireRemaining(4)
        if (order == ByteOrder.BIG_ENDIAN) {
            writeShort(value ushr 16); writeShort(value)
        } else {
            writeShort(value); writeShort(value ushr 16)
        }
    }
    fun readByte(): Byte {
        requireRemaining(1)
        return bytes[arrayOffset + position++]
    }
    fun readUnsignedByte(): Int = readByte().toInt() and 255
    fun readUnsignedShort(): Int {
        requireRemaining(2)
        val first = readUnsignedByte()
        val second = readUnsignedByte()
        return if (order == ByteOrder.BIG_ENDIAN) (first shl 8) or second else (second shl 8) or first
    }
    fun readInt(): Int {
        requireRemaining(4)
        val first = readUnsignedShort()
        val second = readUnsignedShort()
        return if (order == ByteOrder.BIG_ENDIAN) (first shl 16) or second else (second shl 16) or first
    }
    fun readLong(): Long {
        requireRemaining(8)
        val first = readInt().toLong() and 0xffffffffL
        val second = readInt().toLong() and 0xffffffffL
        return if (order == ByteOrder.BIG_ENDIAN) (first shl 32) or second else (second shl 32) or first
    }
    fun readBytes(count: Int): ByteArray {
        requireRemaining(count)
        val result = bytes.copyOfRange(arrayOffset + position, arrayOffset + position + count)
        position += count
        return result
    }
    fun readChars(count: Int): CharArray {
        if (count < 0 || count > remaining / 2) throw ICUUncheckedIOException("Buffer too short for $count UTF-16 units")
        return CharArray(count) { readUnsignedShort().toChar() }
    }
    fun readInts(count: Int): IntArray {
        if (count < 0 || count > remaining / 4) throw ICUUncheckedIOException("Buffer too short for $count integers")
        return IntArray(count) { readInt() }
    }
    fun skip(count: Int) {
        requireRemaining(count)
        position += count
    }
}
