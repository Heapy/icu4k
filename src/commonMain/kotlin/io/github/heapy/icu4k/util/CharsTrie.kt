// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.util.BytesTrie.Result

/** ICU compact UTF-16 trie reader. Serialized storage is shared and must remain immutable. */
class CharsTrie(private val chars: CharSequence, private val root: Int) : Iterable<CharsTrie.Entry> {
    private var pos = root
    private var remaining = -1
    constructor(other: CharsTrie) : this(other.chars, other.root) { pos = other.pos; remaining = other.remaining }
    fun clone(): CharsTrie = CharsTrie(this)
    fun reset(): CharsTrie { pos = root; remaining = -1; return this }
    fun getState64(): Long = (remaining.toLong() shl 32) or pos.toLong()
    fun resetToState64(state: Long): CharsTrie { remaining = (state shr 32).toInt(); pos = state.toInt(); return this }
    class State {
        internal var chars: CharSequence? = null
        internal var root = 0
        internal var pos = 0
        internal var remaining = 0
    }
    fun saveState(state: State): CharsTrie {
        state.chars = chars; state.root = root; state.pos = pos; state.remaining = remaining
        return this
    }
    fun resetToState(state: State): CharsTrie {
        require(chars === state.chars && root == state.root) { "incompatible trie state" }
        pos = state.pos; remaining = state.remaining
        return this
    }
    private fun resultAt(index: Int): Result = if (chars[index].code >= 0x40) valueResult(chars[index].code) else Result.NO_VALUE
    fun current(): Result = if (pos < 0) Result.NO_MATCH else if (remaining < 0) resultAt(pos) else Result.NO_VALUE
    fun first(unit: Int): Result { remaining = -1; return nextImpl(root, unit) }
    fun firstForCodePoint(cp: Int): Result = if (cp <= 0xffff) first(cp)
        else if (first((0xd7c0 + (cp shr 10)).toChar().code).hasNext()) next(0xdc00 + (cp and 0x3ff)) else Result.NO_MATCH
    fun nextForCodePoint(cp: Int): Result = if (cp <= 0xffff) next(cp)
        else if (next((0xd7c0 + (cp shr 10)).toChar().code).hasNext()) next(0xdc00 + (cp and 0x3ff)) else Result.NO_MATCH
    fun next(unit: Int): Result {
        if (pos < 0) return Result.NO_MATCH
        if (remaining >= 0) {
            if (unit == chars[pos++].code) {
                --remaining
                return if (remaining < 0) resultAt(pos) else Result.NO_VALUE
            }
            pos = -1
            return Result.NO_MATCH
        }
        return nextImpl(pos, unit)
    }
    fun next(input: CharSequence, start: Int, limit: Int): Result {
        if (start >= limit) return current()
        var p = pos
        if (p < 0) return Result.NO_MATCH
        var length = remaining
        var index = start
        while (true) {
            var unit: Char
            while (true) {
                if (index == limit) {
                    remaining = length; pos = p
                    return if (length < 0) resultAt(p) else Result.NO_VALUE
                }
                unit = input[index++]
                if (length < 0) { remaining = length; break }
                if (unit != chars[p]) { pos = -1; return Result.NO_MATCH }
                ++p; --length
            }
            var node = chars[p++].code
            while (true) {
                if (node < 0x30) {
                    val result = branchNext(p, node, unit.code)
                    if (result == Result.NO_MATCH) return result
                    if (index == limit) return result
                    if (result == Result.FINAL_VALUE) { pos = -1; return Result.NO_MATCH }
                    unit = input[index++]; p = pos; node = chars[p++].code
                } else if (node < 0x40) {
                    length = node - 0x30
                    if (unit != chars[p]) { pos = -1; return Result.NO_MATCH }
                    ++p; --length
                    break
                } else if (node and 0x8000 != 0) {
                    pos = -1; return Result.NO_MATCH
                } else { p = skipNodeValue(p, node); node = node and 0x3f }
            }
        }
    }
    fun getValue(): Int {
        val lead = chars[pos].code
        return if (lead and 0x8000 != 0) readValue(chars, pos + 1, lead and 0x7fff) else readNodeValue(chars, pos + 1, lead)
    }
    private fun nextImpl(start: Int, unit: Int): Result {
        var p = start
        var node = chars[p++].code
        while (true) {
            if (node < 0x30) return branchNext(p, node, unit)
            if (node < 0x40) {
                var length = node - 0x30
                if (unit == chars[p++].code) {
                    remaining = --length; pos = p
                    return if (length < 0) resultAt(p) else Result.NO_VALUE
                }
                break
            }
            if (node and 0x8000 != 0) break
            p = skipNodeValue(p, node)
            node = node and 0x3f
        }
        pos = -1
        return Result.NO_MATCH
    }
    private fun branchNext(start: Int, count: Int, unit: Int): Result {
        var p = start
        var length = count
        if (length == 0) length = chars[p++].code
        ++length
        while (length > 5) {
            if (unit < chars[p++].code) { length = length shr 1; p = jumpByDelta(chars, p) }
            else { length -= length shr 1; p = skipDelta(chars, p) }
        }
        do {
            if (unit == chars[p++].code) {
                val node = chars[p].code
                if (node and 0x8000 != 0) { pos = p; return Result.FINAL_VALUE }
                val delta = readValue(chars, p + 1, node)
                p = skipValue(p + 1, node) + delta
                pos = p
                return resultAt(p)
            }
            --length
            p = skipValue(p + 1, chars[p].code and 0x7fff)
        } while (length > 1)
        if (unit == chars[p++].code) { pos = p; return resultAt(p) }
        pos = -1
        return Result.NO_MATCH
    }
    fun getNextChars(out: Appendable): Int {
        var p = pos
        if (p < 0) return 0
        if (remaining >= 0) { out.append(chars[p]); return 1 }
        var node = chars[p++].code
        if (node >= 0x40) {
            if (node and 0x8000 != 0) return 0
            p = skipNodeValue(p, node); node = node and 0x3f
        }
        if (node < 0x30) {
            if (node == 0) node = chars[p++].code
            getNextBranchChars(chars, p, ++node, out)
            return node
        }
        out.append(chars[p])
        return 1
    }
    fun getUniqueValue(): Long {
        if (pos < 0) return 0
        val value = findUniqueValue(chars, pos + remaining + 1, 0)
        return (value shl 31) shr 31
    }
    override fun iterator(): Iterator = Iterator(chars, pos, remaining, 0)
    fun iterator(maxStringLength: Int): Iterator = Iterator(chars, pos, remaining, maxStringLength)
    /** Reused by the iterator; [chars] points to its mutable key buffer. */
    class Entry internal constructor() {
        var chars: CharSequence = ""
        var value = 0
    }
    class Iterator internal constructor(
        private val chars: CharSequence,
        private val initialPos: Int,
        private val initialRemaining: Int,
        private val maxLength: Int,
    ) : MutableIterator<Entry> {
        private var pos = initialPos
        private var remaining = initialRemaining
        private var skipValue = false
        private val str = StringBuilder()
        private val entry = Entry()
        private val stack = ArrayList<Long>()
        init {
            if (remaining >= 0) {
                var count = remaining + 1
                if (maxLength > 0 && count > maxLength) count = maxLength
                str.append(chars, pos, pos + count)
                pos += count; remaining -= count
            }
        }
        fun reset(): Iterator {
            pos = initialPos; remaining = initialRemaining; skipValue = false
            var count = remaining + 1
            if (maxLength > 0 && count > maxLength) count = maxLength
            str.setLength(count)
            pos += count; remaining -= count
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
                str.setLength(length and 0xffff)
                length = length ushr 16
                if (length > 1) {
                    p = branchNext(p, length)
                    if (p < 0) return entry
                } else str.append(chars[p++])
            }
            if (remaining >= 0) return truncateAndStop()
            while (true) {
                var node = chars[p++].code
                if (node >= 0x40) {
                    if (skipValue) {
                        p = skipNodeValue(p, node); node = node and 0x3f; skipValue = false
                    } else {
                        val final = node and 0x8000 != 0
                        entry.value = if (final) readValue(chars, p, node and 0x7fff) else readNodeValue(chars, p, node)
                        if (final || maxLength > 0 && str.length == maxLength) pos = -1
                        else { pos = p - 1; skipValue = true }
                        entry.chars = str
                        return entry
                    }
                }
                if (maxLength > 0 && str.length == maxLength) return truncateAndStop()
                if (node < 0x30) {
                    if (node == 0) node = chars[p++].code
                    p = branchNext(p, node + 1)
                    if (p < 0) return entry
                } else {
                    val length = node - 0x30 + 1
                    if (maxLength > 0 && str.length + length > maxLength) {
                        str.append(chars, p, p + maxLength - str.length)
                        return truncateAndStop()
                    }
                    str.append(chars, p, p + length)
                    p += length
                }
            }
        }
        override fun remove(): Nothing = throw UnsupportedOperationException()
        private fun truncateAndStop(): Entry { pos = -1; entry.chars = str; entry.value = -1; return entry }
        private fun branchNext(start: Int, count: Int): Int {
            var p = start
            var length = count
            while (length > 5) {
                ++p
                stack.add((skipDelta(chars, p).toLong() shl 32) or ((length - (length shr 1)).toLong() shl 16) or str.length.toLong())
                length = length shr 1
                p = jumpByDelta(chars, p)
            }
            val unit = chars[p++]
            val node = chars[p++].code
            val value = readValue(chars, p, node and 0x7fff)
            p = skipValue(p, node and 0x7fff)
            stack.add((p.toLong() shl 32) or ((length - 1).toLong() shl 16) or str.length.toLong())
            str.append(unit)
            if (node and 0x8000 != 0) {
                pos = -1; entry.chars = str; entry.value = value
                return -1
            }
            return p + value
        }
    }
    companion object {
        fun iterator(chars: CharSequence, offset: Int, maxStringLength: Int): Iterator = Iterator(chars, offset, -1, maxStringLength)
        private fun valueResult(node: Int): Result = if (node and 0x8000 != 0) Result.FINAL_VALUE else Result.INTERMEDIATE_VALUE
        private fun readValue(chars: CharSequence, p: Int, lead: Int): Int = when {
            lead < 0x4000 -> lead
            lead < 0x7fff -> ((lead - 0x4000) shl 16) or chars[p].code
            else -> (chars[p].code shl 16) or chars[p + 1].code
        }
        private fun skipValue(p: Int, lead: Int): Int = p + if (lead < 0x4000) 0 else if (lead < 0x7fff) 1 else 2
        private fun readNodeValue(chars: CharSequence, p: Int, lead: Int): Int = when {
            lead < 0x4040 -> (lead shr 6) - 1
            lead < 0x7fc0 -> (((lead and 0x7fc0) - 0x4040) shl 10) or chars[p].code
            else -> (chars[p].code shl 16) or chars[p + 1].code
        }
        private fun skipNodeValue(p: Int, lead: Int): Int = p + if (lead < 0x4040) 0 else if (lead < 0x7fc0) 1 else 2
        private fun jumpByDelta(chars: CharSequence, start: Int): Int {
            var p = start
            var delta = chars[p++].code
            if (delta >= 0xfc00) {
                if (delta == 0xffff) { delta = (chars[p].code shl 16) or chars[p + 1].code; p += 2 }
                else delta = ((delta - 0xfc00) shl 16) or chars[p++].code
            }
            return p + delta
        }
        private fun skipDelta(chars: CharSequence, p: Int): Int = p + 1 + if (chars[p].code < 0xfc00) 0 else if (chars[p].code == 0xffff) 2 else 1
        private fun getNextBranchChars(chars: CharSequence, start: Int, count: Int, out: Appendable) {
            var p = start
            var length = count
            while (length > 5) {
                ++p
                getNextBranchChars(chars, jumpByDelta(chars, p), length shr 1, out)
                length -= length shr 1; p = skipDelta(chars, p)
            }
            do {
                out.append(chars[p++]); p = skipValue(p + 1, chars[p].code and 0x7fff)
            } while (--length > 1)
            out.append(chars[p])
        }
        private fun findUniqueValueFromBranch(chars: CharSequence, start: Int, count: Int, initial: Long): Long {
            var p = start
            var length = count
            var unique = initial
            while (length > 5) {
                ++p
                unique = findUniqueValueFromBranch(chars, jumpByDelta(chars, p), length shr 1, unique)
                if (unique == 0L) return 0
                length -= length shr 1; p = skipDelta(chars, p)
            }
            do {
                ++p
                val node = chars[p++].code
                val value = readValue(chars, p, node and 0x7fff)
                p = skipValue(p, node and 0x7fff)
                if (node and 0x8000 != 0) {
                    if (unique != 0L) { if (value != (unique shr 1).toInt()) return 0 }
                    else unique = (value.toLong() shl 1) or 1
                } else {
                    unique = findUniqueValue(chars, p + value, unique)
                    if (unique == 0L) return 0
                }
            } while (--length > 1)
            return ((p + 1).toLong() shl 33) or (unique and 0x1ffffffffL)
        }
        private fun findUniqueValue(chars: CharSequence, start: Int, initial: Long): Long {
            var p = start
            var unique = initial
            var node = chars[p++].code
            while (true) {
                if (node < 0x30) {
                    if (node == 0) node = chars[p++].code
                    unique = findUniqueValueFromBranch(chars, p, node + 1, unique)
                    if (unique == 0L) return 0
                    p = (unique ushr 33).toInt(); node = chars[p++].code
                } else if (node < 0x40) {
                    p += node - 0x30 + 1; node = chars[p++].code
                } else {
                    val value = if (node and 0x8000 != 0) readValue(chars, p, node and 0x7fff) else readNodeValue(chars, p, node)
                    if (unique != 0L) { if (value != (unique shr 1).toInt()) return 0 }
                    else unique = (value.toLong() shl 1) or 1
                    if (node and 0x8000 != 0) return unique
                    p = skipNodeValue(p, node); node = node and 0x3f
                }
            }
        }
    }
}
