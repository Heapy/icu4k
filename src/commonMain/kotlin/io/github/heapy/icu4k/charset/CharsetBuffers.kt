// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

/** Cursor views used by the common codecs instead of java.nio character/int buffers. */
internal open class CharsetBufferCursor(val capacity: Int) {
    var limit = capacity
        set(value) {
            require(value in 0..capacity)
            field = value
            if (position > value) position = value
        }
    var position = 0
        set(value) { require(value in 0..limit); field = value }
    val remaining: Int get() = limit - position
    fun hasRemaining(): Boolean = remaining > 0
}
internal class CharsetBufferOverflowException : RuntimeException()
internal class CharsetBufferUnderflowException : RuntimeException()
internal class CharsetReadOnlyBufferException : RuntimeException()

internal class CharsetCharBuffer(
    val array: CharArray,
    val arrayOffset: Int = 0,
    capacity: Int = array.size - arrayOffset,
    val isReadOnly: Boolean = false,
) : CharsetBufferCursor(capacity) {
    init { require(arrayOffset >= 0 && capacity >= 0 && arrayOffset <= array.size - capacity) }
    fun get(index: Int): Char {
        if (index !in 0..<limit) throw IndexOutOfBoundsException()
        return array[arrayOffset + index]
    }
    fun get(): Char {
        if (!hasRemaining()) throw CharsetBufferUnderflowException()
        return get(position++)
    }
    fun put(index: Int, value: Char) {
        if (isReadOnly) throw CharsetReadOnlyBufferException()
        if (index !in 0..<limit) throw IndexOutOfBoundsException()
        array[arrayOffset + index] = value
    }
    fun put(value: Char) {
        if (isReadOnly) throw CharsetReadOnlyBufferException()
        if (!hasRemaining()) throw CharsetBufferOverflowException()
        put(position, value); position++
    }
    fun slice(): CharsetCharBuffer = CharsetCharBuffer(array, arrayOffset + position, remaining, isReadOnly)
}
internal class CharsetIntBuffer(val array: IntArray) : CharsetBufferCursor(array.size) {
    fun put(value: Int) {
        if (!hasRemaining()) throw CharsetBufferOverflowException()
        array[position++] = value
    }
}

internal enum class CharsetCodingErrorAction { REPORT, IGNORE, REPLACE }
internal class CharsetCoderResult private constructor(private val kind: Int, private val count: Int = 0) {
    fun isUnderflow(): Boolean = kind == 0
    fun isOverflow(): Boolean = kind == 1
    fun isMalformed(): Boolean = kind == 2
    fun isUnmappable(): Boolean = kind == 3
    fun isError(): Boolean = kind >= 2
    fun length(): Int {
        if (!isError()) throw UnsupportedOperationException()
        return count
    }
    override fun toString(): String = when (kind) {
        0 -> "UNDERFLOW"
        1 -> "OVERFLOW"
        2 -> "MALFORMED[$count]"
        else -> "UNMAPPABLE[$count]"
    }
    companion object {
        val UNDERFLOW = CharsetCoderResult(0)
        val OVERFLOW = CharsetCoderResult(1)
        fun malformedForLength(length: Int): CharsetCoderResult { require(length > 0); return CharsetCoderResult(2, length) }
        fun unmappableForLength(length: Int): CharsetCoderResult { require(length > 0); return CharsetCoderResult(3, length) }
    }
}
