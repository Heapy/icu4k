package io.github.heapy.icu4k.util

internal interface BinaryBufferStorage {
    val size: Int
    operator fun get(index: Int): Byte
    operator fun set(index: Int, value: Byte)
    fun array(): ByteArray = copyOfRange(0, size)
    fun copyOfRange(start: Int, end: Int): ByteArray = ByteArray(end - start) { get(start + it) }
}
private class ArrayBufferStorage(private val data: ByteArray) : BinaryBufferStorage {
    override val size: Int get() = data.size
    override fun get(index: Int): Byte = data[index]
    override fun set(index: Int, value: Byte) { data[index] = value }
    override fun array(): ByteArray = data
    override fun copyOfRange(start: Int, end: Int): ByteArray = data.copyOfRange(start, end)
}

/** Shared byte cursor for ICU binary formats, over array or host-backed storage. */
class BinaryBuffer private constructor(
    private val storage: BinaryBufferStorage,
    var order: ByteOrder,
    val arrayOffset: Int,
    val capacity: Int,
    val isReadOnly: Boolean,
    private val arrayAccessible: Boolean = true,
) {
    constructor(bytes: ByteArray, order: ByteOrder = ByteOrder.BIG_ENDIAN, arrayOffset: Int = 0,
                capacity: Int = bytes.size - arrayOffset, isReadOnly: Boolean = false) :
        this(ArrayBufferStorage(bytes), order, arrayOffset, capacity, isReadOnly)
    internal constructor(storage: BinaryBufferStorage) : this(storage, ByteOrder.BIG_ENDIAN, 0, storage.size, true)
    /** Mutable array buffers expose their storage; read-only buffers expose a defensive snapshot. */
    val bytes: ByteArray get() = if (isReadOnly) storage.copyOfRange(0, storage.size) else storage.array()
    init {
        require(arrayOffset >= 0 && capacity >= 0 && arrayOffset <= storage.size - capacity) { "Invalid byte-array slice" }
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
    /** Codec fast paths may only access an exposed, writable array. */
    internal val hasArray: Boolean get() = !isReadOnly && arrayAccessible && storage is ArrayBufferStorage

    fun getByte(index: Int): Byte {
        if (index !in 0 until limit) throw IndexOutOfBoundsException("Invalid byte index: $index")
        return storage[arrayOffset + index]
    }
    /** Shares the remaining bytes, with an independent cursor and byte order. */
    fun slice(): BinaryBuffer = BinaryBuffer(storage, order, arrayOffset + position, remaining, isReadOnly, arrayAccessible)

    /** Shares an absolute range within [limit], without changing this buffer's cursor. */
    fun slice(index: Int, length: Int = limit - index): BinaryBuffer {
        if (index < 0 || index > limit || length < 0 || length > limit - index) throw IndexOutOfBoundsException("Invalid buffer slice")
        return BinaryBuffer(storage, order, arrayOffset + index, length, isReadOnly, arrayAccessible)
    }

    /** Shares storage/cursor bounds; like ByteBuffer.duplicate(), starts in big-endian order. */
    fun duplicate(): BinaryBuffer = BinaryBuffer(storage, ByteOrder.BIG_ENDIAN, arrayOffset, capacity, isReadOnly, arrayAccessible).also {
        it.limit = limit
        it.position = position
    }

    /** Shares storage and preserves cursor state without permitting writes through this view. */
    fun asReadOnlyBuffer(): BinaryBuffer = BinaryBuffer(storage, order, arrayOffset, capacity, true, arrayAccessible).also {
        it.limit = limit
        it.position = position
    }
    /** Writable codec view that selects generic buffer loops without exposing an array fast path. */
    internal fun asOpaqueBuffer(): BinaryBuffer = BinaryBuffer(storage, order, arrayOffset, capacity, isReadOnly, false).also {
        it.limit = limit
        it.position = position
    }
    private fun requireWritable() {
        if (isReadOnly) throw UnsupportedOperationException("Read-only buffer")
    }

    fun requireRemaining(count: Int) {
        if (count < 0 || count > remaining) throw ICUUncheckedIOException("Buffer too short: need $count bytes, have $remaining")
    }
    fun writeByte(value: Int) {
        requireWritable()
        requireRemaining(1)
        storage[arrayOffset + position++] = value.toByte()
    }
    fun writeShort(value: Int) {
        requireWritable()
        requireRemaining(2)
        if (order == ByteOrder.BIG_ENDIAN) {
            writeByte(value ushr 8); writeByte(value)
        } else {
            writeByte(value); writeByte(value ushr 8)
        }
    }
    fun writeInt(value: Int) {
        requireWritable()
        requireRemaining(4)
        if (order == ByteOrder.BIG_ENDIAN) {
            writeShort(value ushr 16); writeShort(value)
        } else {
            writeShort(value); writeShort(value ushr 16)
        }
    }
    fun readByte(): Byte {
        requireRemaining(1)
        return storage[arrayOffset + position++]
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
        val result = storage.copyOfRange(arrayOffset + position, arrayOffset + position + count)
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
