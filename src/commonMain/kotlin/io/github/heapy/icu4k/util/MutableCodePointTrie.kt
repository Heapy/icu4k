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
    private fun maskValues(mask: Int) {
        initialValue = initialValue and mask
        errorValue = errorValue and mask
        highValue = highValue and mask
        for (i in 0 until (highStart shr CodePointTrie.SHIFT_3)) {
            if (flags[i] == ALL_SAME) index[i] = index[i] and mask
        }
        for (i in 0 until dataLength) data[i] = data[i] and mask
    }
    private fun findHighStart(): Int {
        var i = highStart shr CodePointTrie.SHIFT_3
        while (i > 0) {
            --i
            val match = if (flags[i] == ALL_SAME) index[i] == highValue
                else allValuesSameAs(data, index[i], CodePointTrie.SMALL_DATA_BLOCK_LENGTH, highValue)
            if (!match) return (i + 1) shl CodePointTrie.SHIFT_3
        }
        return 0
    }
    internal class AllSameBlocks {
        private var length = 0
        private var mostRecent = -1
        private val indexes = IntArray(32)
        private val values = IntArray(32)
        private val refCounts = IntArray(32)
        fun findOrAdd(index: Int, count: Int, value: Int): Int {
            if (mostRecent >= 0 && values[mostRecent] == value) {
                refCounts[mostRecent] += count
                return indexes[mostRecent]
            }
            for (i in 0 until length) if (values[i] == value) {
                mostRecent = i
                refCounts[i] += count
                return indexes[i]
            }
            if (length == 32) return OVERFLOW
            mostRecent = length
            indexes[length] = index
            values[length] = value
            refCounts[length++] = count
            return NEW_UNIQUE
        }
        fun add(index: Int, count: Int, value: Int) {
            var least = -1
            var leastCount = I_LIMIT
            for (i in 0 until length) if (refCounts[i] < leastCount) {
                least = i
                leastCount = refCounts[i]
            }
            mostRecent = least
            indexes[least] = index
            values[least] = value
            refCounts[least] = count
        }
        fun findMostUsed(): Int {
            if (length == 0) return -1
            var max = -1
            var maxCount = 0
            for (i in 0 until length) if (refCounts[i] > maxCount) {
                max = i
                maxCount = refCounts[i]
            }
            return indexes[max]
        }
        companion object {
            const val NEW_UNIQUE = -1
            const val OVERFLOW = -2
        }
    }
    internal class MixedBlocks {
        private var table = IntArray(0)
        private var length = 0
        private var shift = 0
        private var mask = 0
        private var blockLength = 0
        fun init(maxLength: Int, newBlockLength: Int) {
            val maxDataIndex = maxLength - newBlockLength + 1
            val newLength: Int
            when {
                maxDataIndex <= 0xfff -> { newLength = 6007; shift = 12; mask = 0xfff }
                maxDataIndex <= 0x7fff -> { newLength = 50021; shift = 15; mask = 0x7fff }
                maxDataIndex <= 0x1ffff -> { newLength = 200003; shift = 17; mask = 0x1ffff }
                else -> { newLength = 1500007; shift = 21; mask = 0x1fffff }
            }
            if (newLength > table.size) table = IntArray(newLength) else table.fill(0, 0, newLength)
            length = newLength
            blockLength = newBlockLength
        }
        fun extend(data: IntArray, minStart: Int, prevDataLength: Int, newDataLength: Int) {
            var start = prevDataLength - blockLength
            start = if (start >= minStart) start + 1 else minStart
            while (start <= newDataLength - blockLength) {
                addEntry(data, null, start, makeHashCode(data, start), start)
                ++start
            }
        }
        fun extend(data: CharArray, minStart: Int, prevDataLength: Int, newDataLength: Int) {
            var start = prevDataLength - blockLength
            start = if (start >= minStart) start + 1 else minStart
            while (start <= newDataLength - blockLength) {
                addEntry(null, data, start, makeHashCode(data, start), start)
                ++start
            }
        }
        fun findBlock(data: IntArray, blockData: IntArray, blockStart: Int): Int {
            val entry = findEntry(data, null, blockData, null, blockStart, makeHashCode(blockData, blockStart))
            return if (entry >= 0) (table[entry] and mask) - 1 else -1
        }
        fun findBlock(data: CharArray, blockData: IntArray, blockStart: Int): Int {
            val entry = findEntry(null, data, blockData, null, blockStart, makeHashCode(blockData, blockStart))
            return if (entry >= 0) (table[entry] and mask) - 1 else -1
        }
        fun findBlock(data: CharArray, blockData: CharArray, blockStart: Int): Int {
            val entry = findEntry(null, data, null, blockData, blockStart, makeHashCode(blockData, blockStart))
            return if (entry >= 0) (table[entry] and mask) - 1 else -1
        }
        fun findAllSameBlock(data: IntArray, blockValue: Int): Int {
            val entry = findEntry(data, blockValue, makeHashCode(blockValue))
            return if (entry >= 0) (table[entry] and mask) - 1 else -1
        }
        private fun makeHashCode(data: IntArray, start: Int): Int {
            var hash = data[start]
            var i = start + 1
            do { hash = 37 * hash + data[i++] } while (i < start + blockLength)
            return hash
        }
        private fun makeHashCode(data: CharArray, start: Int): Int {
            var hash = data[start].code
            var i = start + 1
            do { hash = 37 * hash + data[i++].code } while (i < start + blockLength)
            return hash
        }
        private fun makeHashCode(value: Int): Int {
            var hash = value
            for (i in 1 until blockLength) hash = 37 * hash + value
            return hash
        }
        private fun addEntry(data32: IntArray?, data16: CharArray?, blockStart: Int, hash: Int, dataIndex: Int) {
            val entry = findEntry(data32, data16, data32, data16, blockStart, hash)
            if (entry < 0) table[entry.inv()] = (hash shl shift) or (dataIndex + 1)
        }
        private fun findEntry(data32: IntArray?, data16: CharArray?, block32: IntArray?, block16: CharArray?, blockStart: Int, hash: Int): Int {
            val shiftedHash = hash shl shift
            val initial = modulo(hash, length - 1) + 1
            var i = initial
            while (true) {
                val entry = table[i]
                if (entry == 0) return i.inv()
                if (entry and mask.inv() == shiftedHash) {
                    val di = (entry and mask) - 1
                    val equal = if (data32 != null) equalBlocks(data32, di, block32!!, blockStart, blockLength)
                        else if (block32 != null) equalBlocks(data16!!, di, block32, blockStart, blockLength)
                        else equalBlocks(data16!!, di, block16!!, blockStart, blockLength)
                    if (equal) return i
                }
                i = (i + initial) % length
            }
        }
        private fun findEntry(data: IntArray, blockValue: Int, hash: Int): Int {
            val shiftedHash = hash shl shift
            val initial = modulo(hash, length - 1) + 1
            var i = initial
            while (true) {
                val entry = table[i]
                if (entry == 0) return i.inv()
                if (entry and mask.inv() == shiftedHash && allValuesSameAs(data, (entry and mask) - 1, blockLength, blockValue)) return i
                i = (i + initial) % length
            }
        }
        private fun modulo(n: Int, m: Int): Int = (n % m).let { if (it < 0) it + m else it }
    }
    companion object {
        private fun equalBlocks(s: IntArray, si: Int, t: IntArray, ti: Int, length: Int): Boolean {
            var i = 0
            while (i < length && s[si + i] == t[ti + i]) ++i
            return i == length
        }
        private fun equalBlocks(s: CharArray, si: Int, t: IntArray, ti: Int, length: Int): Boolean {
            var i = 0
            while (i < length && s[si + i].code == t[ti + i]) ++i
            return i == length
        }
        private fun equalBlocks(s: CharArray, si: Int, t: CharArray, ti: Int, length: Int): Boolean {
            var i = 0
            while (i < length && s[si + i] == t[ti + i]) ++i
            return i == length
        }
        private fun allValuesSameAs(p: IntArray, start: Int, length: Int, value: Int): Boolean {
            var i = start
            while (i < start + length && p[i] == value) ++i
            return i == start + length
        }
        private fun findSameBlock(p: CharArray, initialStart: Int, length: Int, q: CharArray, qStart: Int, blockLength: Int): Int {
            var start = initialStart
            while (start <= length - blockLength) {
                if (equalBlocks(p, start, q, qStart, blockLength)) return start
                ++start
            }
            return -1
        }
        private fun findAllSameBlock(p: IntArray, start: Int, limit: Int, value: Int, blockLength: Int): Int {
            var block = start
            while (block <= limit - blockLength) {
                if (p[block] == value) {
                    var i = 1
                    while (true) {
                        if (i == blockLength) return block
                        if (p[block + i] != value) { block += i; break }
                        ++i
                    }
                }
                ++block
            }
            return -1
        }
        private fun getOverlap(p: IntArray, length: Int, q: IntArray, qStart: Int, blockLength: Int): Int {
            var overlap = blockLength - 1
            while (overlap > 0 && !equalBlocks(p, length - overlap, q, qStart, overlap)) --overlap
            return overlap
        }
        private fun getOverlap(p: CharArray, length: Int, q: IntArray, qStart: Int, blockLength: Int): Int {
            var overlap = blockLength - 1
            while (overlap > 0 && !equalBlocks(p, length - overlap, q, qStart, overlap)) --overlap
            return overlap
        }
        private fun getOverlap(p: CharArray, length: Int, q: CharArray, qStart: Int, blockLength: Int): Int {
            var overlap = blockLength - 1
            while (overlap > 0 && !equalBlocks(p, length - overlap, q, qStart, overlap)) --overlap
            return overlap
        }
        private fun getAllSameOverlap(p: IntArray, length: Int, value: Int, blockLength: Int): Int {
            val min = length - (blockLength - 1)
            var i = length
            while (min < i && p[i - 1] == value) --i
            return length - i
        }
        private fun isStartOfSomeFastBlock(dataOffset: Int, index: IntArray, fastILimit: Int): Boolean {
            var i = 0
            while (i < fastILimit) {
                if (index[i] == dataOffset) return true
                i += SMALL_DATA_BLOCKS_PER_BMP_BLOCK
            }
            return false
        }
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
