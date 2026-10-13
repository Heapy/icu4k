// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Mutable 32-bit Unicode map. Immutable compaction is being ported separately. */
class MutableCodePointTrie(initialValue: Int, private var errorValue: Int) : CodePointMap() {
    private var index = IntArray(BMP_I_LIMIT)
    private var index3NullOffset = -1
    private var data = IntArray(INITIAL_DATA_LENGTH)
    private var dataLength = 0
    private var dataNullOffset = -1
    private var origInitialValue = initialValue
    private var initialValue = initialValue
    private var highStart = 0
    private var highValue = initialValue
    private var index16: CharArray? = null
    private var flags = ByteArray(I_LIMIT)

    fun clone(): MutableCodePointTrie {
        val builder = MutableCodePointTrie(origInitialValue, errorValue)
        builder.index = IntArray(if (highStart <= BMP_LIMIT) BMP_I_LIMIT else I_LIMIT)
        val limit = highStart shr CodePointTrie.SHIFT_3
        index.copyInto(builder.index, 0, 0, limit)
        flags.copyInto(builder.flags, 0, 0, limit)
        builder.index3NullOffset = index3NullOffset
        builder.data = data.copyOf()
        builder.dataLength = dataLength
        builder.dataNullOffset = dataNullOffset
        builder.initialValue = initialValue
        builder.highStart = highStart
        builder.highValue = highValue
        return builder
    }
    private fun clear() {
        index3NullOffset = -1
        dataNullOffset = -1
        dataLength = 0
        initialValue = origInitialValue
        highValue = initialValue
        highStart = 0
        index16 = null
    }
    override fun get(c: Int): Int {
        if (c !in 0..MAX_UNICODE) return errorValue
        if (c >= highStart) return highValue
        val i = c shr CodePointTrie.SHIFT_3
        return if (flags[i] == ALL_SAME) index[i] else data[index[i] + (c and CodePointTrie.SMALL_DATA_MASK)]
    }
    override fun getRange(start: Int, filter: ValueFilter?, range: Range): Boolean {
        if (start !in 0..MAX_UNICODE) return false
        if (start >= highStart) {
            range.set(start, MAX_UNICODE, filter?.apply(highValue) ?: highValue)
            return true
        }
        val nullValue = filter?.apply(initialValue) ?: initialValue
        var c = start
        var trieValue = 0
        var value = 0
        var haveValue = false
        var i = c shr CodePointTrie.SHIFT_3
        do {
            if (flags[i] == ALL_SAME) {
                val next = index[i]
                if (haveValue) {
                    if (next != trieValue) {
                        if (filter == null || maybeFilterValue(next, initialValue, nullValue, filter) != value) {
                            range.set(start, c - 1, value)
                            return true
                        }
                        trieValue = next
                    }
                } else {
                    trieValue = next
                    value = maybeFilterValue(next, initialValue, nullValue, filter)
                    haveValue = true
                }
                c = (c + CodePointTrie.SMALL_DATA_BLOCK_LENGTH) and CodePointTrie.SMALL_DATA_MASK.inv()
            } else {
                var di = index[i] + (c and CodePointTrie.SMALL_DATA_MASK)
                var next = data[di]
                if (haveValue) {
                    if (next != trieValue) {
                        if (filter == null || maybeFilterValue(next, initialValue, nullValue, filter) != value) {
                            range.set(start, c - 1, value)
                            return true
                        }
                        trieValue = next
                    }
                } else {
                    trieValue = next
                    value = maybeFilterValue(next, initialValue, nullValue, filter)
                    haveValue = true
                }
                while (++c and CodePointTrie.SMALL_DATA_MASK != 0) {
                    next = data[++di]
                    if (next != trieValue) {
                        if (filter == null || maybeFilterValue(next, initialValue, nullValue, filter) != value) {
                            range.set(start, c - 1, value)
                            return true
                        }
                        trieValue = next
                    }
                }
            }
            ++i
        } while (c < highStart)
        range.set(start, if (maybeFilterValue(highValue, initialValue, nullValue, filter) != value) c - 1 else MAX_UNICODE, value)
        return true
    }
    fun set(c: Int, value: Int) {
        require(c in 0..MAX_UNICODE) { "invalid code point" }
        ensureHighStart(c)
        val block = getDataBlock(c shr CodePointTrie.SHIFT_3)
        data[block + (c and CodePointTrie.SMALL_DATA_MASK)] = value
    }
    fun setRange(initialStart: Int, end: Int, value: Int) {
        require(initialStart in 0..MAX_UNICODE && end in initialStart..MAX_UNICODE) { "invalid code point range" }
        ensureHighStart(end)
        var start = initialStart
        var limit = end + 1
        if (start and CodePointTrie.SMALL_DATA_MASK != 0) {
            val block = getDataBlock(start shr CodePointTrie.SHIFT_3)
            val nextStart = (start + CodePointTrie.SMALL_DATA_MASK) and CodePointTrie.SMALL_DATA_MASK.inv()
            if (nextStart <= limit) {
                fillBlock(block, start and CodePointTrie.SMALL_DATA_MASK, CodePointTrie.SMALL_DATA_BLOCK_LENGTH, value)
                start = nextStart
            } else {
                fillBlock(block, start and CodePointTrie.SMALL_DATA_MASK, limit and CodePointTrie.SMALL_DATA_MASK, value)
                return
            }
        }
        val rest = limit and CodePointTrie.SMALL_DATA_MASK
        limit = limit and CodePointTrie.SMALL_DATA_MASK.inv()
        while (start < limit) {
            val i = start shr CodePointTrie.SHIFT_3
            if (flags[i] == ALL_SAME) index[i] = value
            else fillBlock(index[i], 0, CodePointTrie.SMALL_DATA_BLOCK_LENGTH, value)
            start += CodePointTrie.SMALL_DATA_BLOCK_LENGTH
        }
        if (rest > 0) fillBlock(getDataBlock(start shr CodePointTrie.SHIFT_3), 0, rest, value)
    }
    private fun writeBlock(block: Int, value: Int) { data.fill(value, block, block + CodePointTrie.SMALL_DATA_BLOCK_LENGTH) }
    private fun fillBlock(block: Int, start: Int, limit: Int, value: Int) { data.fill(value, block + start, block + limit) }
    private fun ensureHighStart(initialCodePoint: Int) {
        if (initialCodePoint >= highStart) {
            val c = (initialCodePoint + CodePointTrie.CP_PER_INDEX_2_ENTRY) and (CodePointTrie.CP_PER_INDEX_2_ENTRY - 1).inv()
            var i = highStart shr CodePointTrie.SHIFT_3
            val limit = c shr CodePointTrie.SHIFT_3
            if (limit > index.size) index = index.copyOf(I_LIMIT)
            do {
                flags[i] = ALL_SAME
                index[i] = initialValue
            } while (++i < limit)
            highStart = c
        }
    }
    private fun allocDataBlock(length: Int): Int {
        val block = dataLength
        val top = block + length
        if (top > data.size) {
            val capacity = when {
                data.size < MEDIUM_DATA_LENGTH -> MEDIUM_DATA_LENGTH
                data.size < MAX_DATA_LENGTH -> MAX_DATA_LENGTH
                else -> throw AssertionError()
            }
            data = data.copyOf(capacity)
        }
        dataLength = top
        return block
    }
    private fun getDataBlock(i: Int): Int {
        if (flags[i] == MIXED) return index[i]
        if (i < BMP_I_LIMIT) {
            var newBlock = allocDataBlock(CodePointTrie.FAST_DATA_BLOCK_LENGTH)
            var start = i and (SMALL_DATA_BLOCKS_PER_BMP_BLOCK - 1).inv()
            val limit = start + SMALL_DATA_BLOCKS_PER_BMP_BLOCK
            do {
                writeBlock(newBlock, index[start])
                flags[start] = MIXED
                index[start++] = newBlock
                newBlock += CodePointTrie.SMALL_DATA_BLOCK_LENGTH
            } while (start < limit)
            return index[i]
        }
        val newBlock = allocDataBlock(CodePointTrie.SMALL_DATA_BLOCK_LENGTH)
        writeBlock(newBlock, index[i])
        flags[i] = MIXED
        index[i] = newBlock
        return newBlock
    }
    companion object {
        private const val MAX_UNICODE = 0x10ffff
        private const val UNICODE_LIMIT = 0x110000
        private const val BMP_LIMIT = 0x10000
        private const val I_LIMIT = UNICODE_LIMIT shr CodePointTrie.SHIFT_3
        private const val BMP_I_LIMIT = BMP_LIMIT shr CodePointTrie.SHIFT_3
        private const val SMALL_DATA_BLOCKS_PER_BMP_BLOCK = 1 shl (CodePointTrie.FAST_SHIFT - CodePointTrie.SHIFT_3)
        private const val ALL_SAME: Byte = 0
        private const val MIXED: Byte = 1
        private const val INITIAL_DATA_LENGTH = 1 shl 14
        private const val MEDIUM_DATA_LENGTH = 1 shl 17
        private const val MAX_DATA_LENGTH = UNICODE_LIMIT
        private fun maybeFilterValue(value: Int, initialValue: Int, nullValue: Int, filter: ValueFilter?): Int =
            if (value == initialValue) nullValue else filter?.apply(value) ?: value
        fun fromCodePointMap(map: CodePointMap): MutableCodePointTrie {
            val initialValue = map[MAX_UNICODE]
            val trie = MutableCodePointTrie(initialValue, map[-1])
            val range = Range()
            var start = 0
            while (map.getRange(start, null, range)) {
                val end = range.getEnd()
                val value = range.getValue()
                if (value != initialValue) {
                    if (start == end) trie.set(start, value) else trie.setRange(start, end, value)
                }
                start = end + 1
            }
            return trie
        }
    }
}
