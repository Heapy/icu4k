// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Reader for ICU's compact byte trie. Serialized storage is shared and must remain immutable. */
class BytesTrie(private val bytes: ByteArray, private val root: Int) : Iterable<BytesTrie.Entry> {
    private var pos = root
    private var remaining = -1
    constructor(other: BytesTrie) : this(other.bytes, other.root) { pos = other.pos; remaining = other.remaining }
    fun clone(): BytesTrie = BytesTrie(this)
    fun reset(): BytesTrie { pos = root; remaining = -1; return this }
    fun getState64(): Long = (remaining.toLong() shl 32) or pos.toLong()
    fun resetToState64(state: Long): BytesTrie { remaining = (state shr 32).toInt(); pos = state.toInt(); return this }
    class State {
        internal var bytes: ByteArray? = null
        internal var root = 0
        internal var pos = 0
        internal var remaining = 0
    }
    fun saveState(state: State): BytesTrie {
        state.bytes = bytes; state.root = root; state.pos = pos; state.remaining = remaining
        return this
    }
    fun resetToState(state: State): BytesTrie {
        require(bytes === state.bytes && root == state.root) { "incompatible trie state" }
        pos = state.pos; remaining = state.remaining
        return this
    }
    enum class Result {
        NO_MATCH, NO_VALUE, FINAL_VALUE, INTERMEDIATE_VALUE;
        fun matches(): Boolean = this != NO_MATCH
        fun hasValue(): Boolean = ordinal >= 2
        fun hasNext(): Boolean = ordinal and 1 != 0
    }
    private fun byteAt(index: Int): Int = bytes[index].toInt() and 255
    private fun resultAt(index: Int): Result {
        val node = byteAt(index)
        return if (node >= MIN_VALUE) valueResult(node) else Result.NO_VALUE
    }
    fun current(): Result = if (pos < 0) Result.NO_MATCH else if (remaining < 0) resultAt(pos) else Result.NO_VALUE
    fun first(inByte: Int): Result { remaining = -1; return nextImpl(root, if (inByte < 0) inByte + 256 else inByte) }
    fun next(inByte: Int): Result {
        if (pos < 0) return Result.NO_MATCH
        val input = if (inByte < 0) inByte + 256 else inByte
        if (remaining >= 0) {
            if (input == byteAt(pos++)) {
                --remaining
                return if (remaining < 0) resultAt(pos) else Result.NO_VALUE
            }
            pos = -1
            return Result.NO_MATCH
        }
        return nextImpl(pos, input)
    }
    fun next(input: ByteArray, start: Int, limit: Int): Result {
        if (start >= limit) return current()
        var p = pos
        if (p < 0) return Result.NO_MATCH
        var length = remaining
        var index = start
        while (true) {
            var inByte: Byte
            while (true) {
                if (index == limit) {
                    remaining = length; pos = p
                    return if (length < 0) resultAt(p) else Result.NO_VALUE
                }
                inByte = input[index++]
                if (length < 0) { remaining = length; break }
                if (inByte != bytes[p]) { pos = -1; return Result.NO_MATCH }
                ++p; --length
            }
            while (true) {
                val node = byteAt(p++)
                if (node < MIN_LINEAR) {
                    val result = branchNext(p, node, inByte.toInt() and 255)
                    if (result == Result.NO_MATCH) return result
                    if (index == limit) return result
                    if (result == Result.FINAL_VALUE) { pos = -1; return Result.NO_MATCH }
                    inByte = input[index++]; p = pos
                } else if (node < MIN_VALUE) {
                    length = node - MIN_LINEAR
                    if (inByte != bytes[p]) { pos = -1; return Result.NO_MATCH }
                    ++p; --length
                    break
                } else if (node and 1 != 0) {
                    pos = -1; return Result.NO_MATCH
                } else p = skipValue(p, node)
            }
        }
    }
    fun getValue(): Int = readValue(bytes, pos + 1, byteAt(pos) shr 1)
    private fun nextImpl(start: Int, input: Int): Result {
        var p = start
        while (true) {
            val node = byteAt(p++)
            if (node < MIN_LINEAR) return branchNext(p, node, input)
            if (node < MIN_VALUE) {
                var length = node - MIN_LINEAR
                if (input == byteAt(p++)) {
                    remaining = --length; pos = p
                    return if (length < 0) resultAt(p) else Result.NO_VALUE
                }
                break
            }
            if (node and 1 != 0) break
            p = skipValue(p, node)
        }
        pos = -1
        return Result.NO_MATCH
    }
    private fun branchNext(start: Int, count: Int, input: Int): Result {
        var p = start
        var length = count
        if (length == 0) length = byteAt(p++)
        ++length
        while (length > 5) {
            if (input < byteAt(p++)) { length = length shr 1; p = jumpByDelta(bytes, p) }
            else { length -= length shr 1; p = skipDelta(bytes, p) }
        }
        do {
            if (input == byteAt(p++)) {
                val node = byteAt(p)
                if (node and 1 != 0) { pos = p; return Result.FINAL_VALUE }
                val delta = readValue(bytes, p + 1, node shr 1)
                p = skipValue(p + 1, node) + delta
                pos = p
                return resultAt(p)
            }
            --length
            p = skipValue(p + 1, byteAt(p))
        } while (length > 1)
        if (input == byteAt(p++)) { pos = p; return resultAt(p) }
        pos = -1
        return Result.NO_MATCH
    }
    fun getNextBytes(out: Appendable): Int {
        var p = pos
        if (p < 0) return 0
        if (remaining >= 0) { out.append(byteAt(p).toChar()); return 1 }
        var node = byteAt(p++)
        if (node >= MIN_VALUE) {
            if (node and 1 != 0) return 0
            p = skipValue(p, node)
            node = byteAt(p++)
        }
        if (node < MIN_LINEAR) {
            if (node == 0) node = byteAt(p++)
            getNextBranchBytes(bytes, p, ++node, out)
            return node
        }
        out.append(byteAt(p).toChar())
        return 1
    }
    fun getUniqueValue(): Long {
        if (pos < 0) return 0
        val value = findUniqueValue(bytes, pos + remaining + 1, 0)
        return (value shl 31) shr 31
    }
    override fun iterator(): Iterator = Iterator(bytes, pos, remaining, 0)
    fun iterator(maxStringLength: Int): Iterator = Iterator(bytes, pos, remaining, maxStringLength)

    /** The iterator reuses this entry and its storage. Copy bytes before advancing. */
    class Entry internal constructor(capacity: Int) {
        private var bytes = ByteArray(capacity)
        internal var length = 0
        var value = 0
        fun bytesLength(): Int = length
        fun byteAt(index: Int): Byte = bytes[index]
        fun copyBytesTo(dest: ByteArray, offset: Int) { bytes.copyInto(dest, offset, 0, length) }
        /** Portable snapshot replacing the Java read-only ByteBuffer view. */
        fun toByteArray(): ByteArray = bytes.copyOf(length)
        private fun ensureCapacity(size: Int) {
            if (bytes.size < size) bytes = bytes.copyOf(minOf(2 * bytes.size, 2 * size))
        }
        internal fun append(byte: Byte) { ensureCapacity(length + 1); bytes[length++] = byte }
        internal fun append(source: ByteArray, offset: Int, count: Int) {
            ensureCapacity(length + count)
            source.copyInto(bytes, length, offset, offset + count)
            length += count
        }
    }
    class Iterator internal constructor(
        private val bytes: ByteArray,
        private val initialPos: Int,
        private val initialRemaining: Int,
        private val maxLength: Int,
    ) : MutableIterator<Entry> {
        private var pos = initialPos
        private var remaining = initialRemaining
        private val entry = Entry(if (maxLength != 0) maxLength else 32)
        private val stack = ArrayList<Long>()
        init {
            if (remaining >= 0) {
                var count = remaining + 1
                if (maxLength > 0 && count > maxLength) count = maxLength
                entry.append(bytes, pos, count)
                pos += count
                remaining -= count
            }
        }
        fun reset(): Iterator {
            pos = initialPos
            remaining = initialRemaining
            var count = remaining + 1
            if (maxLength > 0 && count > maxLength) count = maxLength
            entry.length = count
            pos += count
            remaining -= count
            stack.clear()
            return this
        }
        override fun hasNext(): Boolean = pos >= 0 || stack.isNotEmpty()
        override fun next(): Entry {
            var p = pos
            if (p < 0) {
                if (stack.isEmpty()) throw NoSuchElementException()
                val top = stack.removeAt(stack.lastIndex)
                var length = top.toInt()
                p = (top shr 32).toInt()
                entry.length = length and 0xffff
                length = length ushr 16
                if (length > 1) {
                    p = branchNext(p, length)
                    if (p < 0) return entry
                } else entry.append(bytes[p++])
            }
            if (remaining >= 0) return truncateAndStop()
            while (true) {
                var node = u(bytes, p++)
                if (node >= MIN_VALUE) {
                    entry.value = readValue(bytes, p, node shr 1)
                    pos = if (node and 1 != 0 || maxLength > 0 && entry.length == maxLength) -1 else skipValue(p, node)
                    return entry
                }
                if (maxLength > 0 && entry.length == maxLength) return truncateAndStop()
                if (node < MIN_LINEAR) {
                    if (node == 0) node = u(bytes, p++)
                    p = branchNext(p, node + 1)
                    if (p < 0) return entry
                } else {
                    val length = node - MIN_LINEAR + 1
                    if (maxLength > 0 && entry.length + length > maxLength) {
                        entry.append(bytes, p, maxLength - entry.length)
                        return truncateAndStop()
                    }
                    entry.append(bytes, p, length)
                    p += length
                }
            }
        }
        override fun remove(): Nothing = throw UnsupportedOperationException()
        private fun truncateAndStop(): Entry { pos = -1; entry.value = -1; return entry }
        private fun branchNext(start: Int, count: Int): Int {
            var p = start
            var length = count
            while (length > 5) {
                ++p
                stack.add((skipDelta(bytes, p).toLong() shl 32) or ((length - (length shr 1)).toLong() shl 16) or entry.length.toLong())
                length = length shr 1
                p = jumpByDelta(bytes, p)
            }
            val trieByte = bytes[p++]
            val node = u(bytes, p++)
            val value = readValue(bytes, p, node shr 1)
            p = skipValue(p, node)
            stack.add((p.toLong() shl 32) or ((length - 1).toLong() shl 16) or entry.length.toLong())
            entry.append(trieByte)
            if (node and 1 != 0) {
                pos = -1
                entry.value = value
                return -1
            }
            return p + value
        }
    }
    companion object {
        fun iterator(bytes: ByteArray, offset: Int, maxStringLength: Int): Iterator = Iterator(bytes, offset, -1, maxStringLength)
        private const val MIN_LINEAR = 0x10
        private const val MIN_VALUE = 0x20
        private const val MIN_TWO = 0x51
        private const val MIN_THREE = 0x6c
        private fun valueResult(node: Int): Result = if (node and 1 != 0) Result.FINAL_VALUE else Result.INTERMEDIATE_VALUE
        private fun u(bytes: ByteArray, p: Int): Int = bytes[p].toInt() and 255
        private fun readValue(bytes: ByteArray, p: Int, lead: Int): Int = when {
            lead < MIN_TWO -> lead - 0x10
            lead < MIN_THREE -> ((lead - MIN_TWO) shl 8) or u(bytes, p)
            lead < 0x7e -> ((lead - MIN_THREE) shl 16) or (u(bytes, p) shl 8) or u(bytes, p + 1)
            lead == 0x7e -> (u(bytes, p) shl 16) or (u(bytes, p + 1) shl 8) or u(bytes, p + 2)
            else -> (bytes[p].toInt() shl 24) or (u(bytes, p + 1) shl 16) or (u(bytes, p + 2) shl 8) or u(bytes, p + 3)
        }
        private fun skipValue(p: Int, lead: Int): Int = p + when {
            lead < MIN_TWO * 2 -> 0
            lead < MIN_THREE * 2 -> 1
            lead < 0x7e * 2 -> 2
            else -> 3 + ((lead shr 1) and 1)
        }
        fun jumpByDelta(bytes: ByteArray, start: Int): Int {
            var p = start
            var delta = u(bytes, p++)
            when {
                delta < 0xc0 -> Unit
                delta < 0xf0 -> { delta = ((delta - 0xc0) shl 8) or u(bytes, p++) }
                delta < 0xfe -> { delta = ((delta - 0xf0) shl 16) or (u(bytes, p) shl 8) or u(bytes, p + 1); p += 2 }
                delta == 0xfe -> { delta = (u(bytes, p) shl 16) or (u(bytes, p + 1) shl 8) or u(bytes, p + 2); p += 3 }
                else -> { delta = (bytes[p].toInt() shl 24) or (u(bytes, p + 1) shl 16) or (u(bytes, p + 2) shl 8) or u(bytes, p + 3); p += 4 }
            }
            return p + delta
        }
        private fun skipDelta(bytes: ByteArray, start: Int): Int {
            val delta = u(bytes, start)
            return start + 1 + when {
                delta < 0xc0 -> 0
                delta < 0xf0 -> 1
                delta < 0xfe -> 2
                else -> 3 + (delta and 1)
            }
        }
        private fun getNextBranchBytes(bytes: ByteArray, start: Int, count: Int, out: Appendable) {
            var p = start
            var length = count
            while (length > 5) {
                ++p
                getNextBranchBytes(bytes, jumpByDelta(bytes, p), length shr 1, out)
                length -= length shr 1
                p = skipDelta(bytes, p)
            }
            do {
                out.append(u(bytes, p++).toChar())
                p = skipValue(p + 1, u(bytes, p))
            } while (--length > 1)
            out.append(u(bytes, p).toChar())
        }
        private fun findUniqueValueFromBranch(bytes: ByteArray, start: Int, count: Int, initial: Long): Long {
            var p = start
            var length = count
            var unique = initial
            while (length > 5) {
                ++p
                unique = findUniqueValueFromBranch(bytes, jumpByDelta(bytes, p), length shr 1, unique)
                if (unique == 0L) return 0
                length -= length shr 1
                p = skipDelta(bytes, p)
            }
            do {
                ++p
                val node = u(bytes, p++)
                val value = readValue(bytes, p, node shr 1)
                p = skipValue(p, node)
                if (node and 1 != 0) {
                    if (unique != 0L) { if (value != (unique shr 1).toInt()) return 0 }
                    else unique = (value.toLong() shl 1) or 1
                } else {
                    unique = findUniqueValue(bytes, p + value, unique)
                    if (unique == 0L) return 0
                }
            } while (--length > 1)
            return ((p + 1).toLong() shl 33) or (unique and 0x1ffffffffL)
        }
        private fun findUniqueValue(bytes: ByteArray, start: Int, initial: Long): Long {
            var p = start
            var unique = initial
            while (true) {
                var node = u(bytes, p++)
                if (node < MIN_LINEAR) {
                    if (node == 0) node = u(bytes, p++)
                    unique = findUniqueValueFromBranch(bytes, p, node + 1, unique)
                    if (unique == 0L) return 0
                    p = (unique ushr 33).toInt()
                } else if (node < MIN_VALUE) p += node - MIN_LINEAR + 1
                else {
                    val value = readValue(bytes, p, node shr 1)
                    if (unique != 0L) { if (value != (unique shr 1).toInt()) return 0 }
                    else unique = (value.toLong() shl 1) or 1
                    if (node and 1 != 0) return unique
                    p = skipValue(p, node)
                }
            }
        }
    }
}
