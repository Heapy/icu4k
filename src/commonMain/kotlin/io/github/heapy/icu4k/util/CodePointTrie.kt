// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.text.UTF16

/** Immutable compact Unicode trie using ICU's serialized Tri3 format. */
abstract class CodePointTrie internal constructor(
    private val index: CharArray,
    private val data: TrieData,
    protected val highStart: Int,
    private val index3NullOffset: Int,
    private val dataNullOffset: Int,
) : CodePointMap() {
    enum class Type { FAST, SMALL }
    enum class ValueWidth { BITS_16, BITS_32, BITS_8 }

    protected val dataLength: Int = data.size
    private val ascii = IntArray(ASCII_LIMIT) { data[it] }
    private val nullValue = data[if (dataNullOffset >= dataLength) dataLength - HIGH_VALUE_NEG_DATA_OFFSET else dataNullOffset]

    abstract fun getType(): Type
    fun getValueWidth(): ValueWidth = data.width
    override fun get(c: Int): Int = data[cpIndex(c)]
    fun asciiGet(c: Int): Int = ascii[c]
    protected fun dataGet(index: Int): Int = data[index]
    protected abstract fun cpIndex(c: Int): Int
    private fun indexAt(i: Int): Int = index[i].code
    protected fun fastIndex(c: Int): Int = indexAt(c shr FAST_SHIFT) + (c and FAST_DATA_MASK)
    protected fun smallIndex(type: Type, c: Int): Int {
        if (c >= highStart) return dataLength - HIGH_VALUE_NEG_DATA_OFFSET
        var i1 = c shr SHIFT_1
        i1 += if (type == Type.FAST) BMP_INDEX_LENGTH - OMITTED_BMP_INDEX_1_LENGTH else SMALL_INDEX_LENGTH
        var i3Block = indexAt(indexAt(i1) + ((c shr SHIFT_2) and INDEX_2_MASK))
        var i3 = (c shr SHIFT_3) and INDEX_3_MASK
        val dataBlock: Int
        if ((i3Block and 0x8000) == 0) {
            dataBlock = indexAt(i3Block + i3)
        } else {
            i3Block = (i3Block and 0x7fff) + (i3 and 7.inv()) + (i3 shr 3)
            i3 = i3 and 7
            dataBlock = ((indexAt(i3Block) shl (2 + 2 * i3)) and 0x30000) or indexAt(i3Block + 1 + i3)
        }
        return dataBlock + (c and SMALL_DATA_MASK)
    }

    private fun maybeFilterValue(value: Int, filteredNull: Int, filter: ValueFilter?): Int =
        if (value == nullValue) filteredNull else filter?.apply(value) ?: value

    final override fun getRange(start: Int, filter: ValueFilter?, range: Range): Boolean {
        if (start !in 0..MAX_UNICODE) return false
        if (start >= highStart) {
            val value = data[dataLength - HIGH_VALUE_NEG_DATA_OFFSET]
            range.set(start, MAX_UNICODE, filter?.apply(value) ?: value)
            return true
        }
        val filteredNull = filter?.apply(nullValue) ?: nullValue
        val type = getType()
        var previousIndex3Block = -1
        var previousBlock = -1
        var c = start
        var trieValue = 0
        var value = 0
        var haveValue = false
        do {
            val i3Block: Int
            var i3: Int
            val i3BlockLength: Int
            val dataBlockLength: Int
            if (c <= 0xffff && (type == Type.FAST || c <= SMALL_MAX)) {
                i3Block = 0
                i3 = c shr FAST_SHIFT
                i3BlockLength = if (type == Type.FAST) BMP_INDEX_LENGTH else SMALL_INDEX_LENGTH
                dataBlockLength = FAST_DATA_BLOCK_LENGTH
            } else {
                var i1 = c shr SHIFT_1
                i1 += if (type == Type.FAST) BMP_INDEX_LENGTH - OMITTED_BMP_INDEX_1_LENGTH else SMALL_INDEX_LENGTH
                i3Block = indexAt(indexAt(i1) + ((c shr SHIFT_2) and INDEX_2_MASK))
                if (i3Block == previousIndex3Block && c - start >= CP_PER_INDEX_2_ENTRY) {
                    c += CP_PER_INDEX_2_ENTRY
                    continue
                }
                previousIndex3Block = i3Block
                if (i3Block == index3NullOffset) {
                    if (haveValue) {
                        if (filteredNull != value) {
                            range.set(start, c - 1, value)
                            return true
                        }
                    } else {
                        trieValue = nullValue
                        value = filteredNull
                        haveValue = true
                    }
                    previousBlock = dataNullOffset
                    c = (c + CP_PER_INDEX_2_ENTRY) and (CP_PER_INDEX_2_ENTRY - 1).inv()
                    continue
                }
                i3 = (c shr SHIFT_3) and INDEX_3_MASK
                i3BlockLength = INDEX_3_BLOCK_LENGTH
                dataBlockLength = SMALL_DATA_BLOCK_LENGTH
            }
            do {
                val block = if ((i3Block and 0x8000) == 0) {
                    indexAt(i3Block + i3)
                } else {
                    val group = (i3Block and 0x7fff) + (i3 and 7.inv()) + (i3 shr 3)
                    val gi = i3 and 7
                    ((indexAt(group) shl (2 + 2 * gi)) and 0x30000) or indexAt(group + 1 + gi)
                }
                if (block == previousBlock && c - start >= dataBlockLength) {
                    c += dataBlockLength
                } else {
                    val dataMask = dataBlockLength - 1
                    previousBlock = block
                    if (block == dataNullOffset) {
                        if (haveValue) {
                            if (filteredNull != value) {
                                range.set(start, c - 1, value)
                                return true
                            }
                        } else {
                            trieValue = nullValue
                            value = filteredNull
                            haveValue = true
                        }
                        c = (c + dataBlockLength) and dataMask.inv()
                    } else {
                        var di = block + (c and dataMask)
                        var nextTrieValue = data[di]
                        if (haveValue) {
                            if (nextTrieValue != trieValue) {
                                if (filter == null || maybeFilterValue(nextTrieValue, filteredNull, filter) != value) {
                                    range.set(start, c - 1, value)
                                    return true
                                }
                                trieValue = nextTrieValue
                            }
                        } else {
                            trieValue = nextTrieValue
                            value = maybeFilterValue(nextTrieValue, filteredNull, filter)
                            haveValue = true
                        }
                        c++
                        while ((c and dataMask) != 0) {
                            nextTrieValue = data[++di]
                            if (nextTrieValue != trieValue) {
                                if (filter == null || maybeFilterValue(nextTrieValue, filteredNull, filter) != value) {
                                    range.set(start, c - 1, value)
                                    return true
                                }
                                trieValue = nextTrieValue
                            }
                            c++
                        }
                    }
                }
                i3++
            } while (i3 < i3BlockLength)
        } while (c < highStart)
        val highValue = data[dataLength - HIGH_VALUE_NEG_DATA_OFFSET]
        c = if (maybeFilterValue(highValue, filteredNull, filter) != value) c - 1 else MAX_UNICODE
        range.set(start, c, value)
        return true
    }

    /** Serializes to a newly allocated byte array (big endian, matching ICU). */
    fun toBinary(): ByteArray = toBinary(BinaryBuffer.ByteOrder.BIG_ENDIAN)

    fun toBinary(order: BinaryBuffer.ByteOrder): ByteArray {
        val result = BinaryBuffer(ByteArray(16 + index.size * 2 + dataLength * data.widthInBytes), order)
        result.writeInt(0x54726933)
        result.writeShort(((dataLength and 0xf0000) shr 4) or ((dataNullOffset and 0xf0000) shr 8) or
            (getType().ordinal shl 6) or getValueWidth().ordinal)
        result.writeShort(index.size)
        result.writeShort(dataLength)
        result.writeShort(index3NullOffset)
        result.writeShort(dataNullOffset)
        result.writeShort(highStart shr SHIFT_2)
        for (entry in index) result.writeShort(entry.code)
        for (i in 0 until dataLength) when (data.width) {
            ValueWidth.BITS_8 -> result.writeByte(data[i])
            ValueWidth.BITS_16 -> result.writeShort(data[i])
            ValueWidth.BITS_32 -> result.writeInt(data[i])
        }
        return result.bytes
    }

    // CodePointMap's forward/backward pairing semantics also apply to trie strings,
    // but unpaired surrogate values must come from the trie's error slot.
    final override fun stringIterator(s: CharSequence, sIndex: Int): StringIterator = object : StringIterator(s, sIndex) {
        override fun next(): Boolean {
            if (!super.next()) return false
            if (UTF16.isSurrogate(c)) mappedValue = data[dataLength - ERROR_VALUE_NEG_DATA_OFFSET]
            return true
        }
        override fun previous(): Boolean {
            if (!super.previous()) return false
            if (UTF16.isSurrogate(c)) mappedValue = data[dataLength - ERROR_VALUE_NEG_DATA_OFFSET]
            return true
        }
    }

    abstract class Fast internal constructor(index: CharArray, data: TrieData, highStart: Int, index3NullOffset: Int, dataNullOffset: Int) :
        CodePointTrie(index, data, highStart, index3NullOffset, dataNullOffset) {
        final override fun getType(): Type = Type.FAST
        fun bmpGet(c: Int): Int = dataGet(fastIndex(c))
        fun suppGet(c: Int): Int = dataGet(smallIndex(Type.FAST, c))
        final override fun cpIndex(c: Int): Int = when {
            c < 0 || c > MAX_UNICODE -> dataLength - ERROR_VALUE_NEG_DATA_OFFSET
            c <= 0xffff -> fastIndex(c)
            else -> smallIndex(Type.FAST, c)
        }
        companion object {
            fun fromBinary(valueWidth: ValueWidth?, bytes: BinaryBuffer): Fast = CodePointTrie.fromBinary(Type.FAST, valueWidth, bytes) as Fast
        }
    }

    abstract class Small internal constructor(index: CharArray, data: TrieData, highStart: Int, index3NullOffset: Int, dataNullOffset: Int) :
        CodePointTrie(index, data, highStart, index3NullOffset, dataNullOffset) {
        final override fun getType(): Type = Type.SMALL
        final override fun cpIndex(c: Int): Int = when {
            c < 0 || c > MAX_UNICODE -> dataLength - ERROR_VALUE_NEG_DATA_OFFSET
            c <= SMALL_MAX -> fastIndex(c)
            else -> smallIndex(Type.SMALL, c)
        }
        companion object {
            fun fromBinary(valueWidth: ValueWidth?, bytes: BinaryBuffer): Small = CodePointTrie.fromBinary(Type.SMALL, valueWidth, bytes) as Small
        }
    }

    class Fast16 internal constructor(index: CharArray, data: CharArray, highStart: Int, index3NullOffset: Int, dataNullOffset: Int) :
        Fast(index, TrieData.Bits16(data), highStart, index3NullOffset, dataNullOffset) {
        companion object {
            fun fromBinary(bytes: BinaryBuffer): Fast16 = CodePointTrie.fromBinary(Type.FAST, ValueWidth.BITS_16, bytes) as Fast16
        }
    }

    class Fast32 internal constructor(index: CharArray, data: IntArray, highStart: Int, index3NullOffset: Int, dataNullOffset: Int) :
        Fast(index, TrieData.Bits32(data), highStart, index3NullOffset, dataNullOffset) {
        companion object {
            fun fromBinary(bytes: BinaryBuffer): Fast32 = CodePointTrie.fromBinary(Type.FAST, ValueWidth.BITS_32, bytes) as Fast32
        }
    }

    class Fast8 internal constructor(index: CharArray, data: ByteArray, highStart: Int, index3NullOffset: Int, dataNullOffset: Int) :
        Fast(index, TrieData.Bits8(data), highStart, index3NullOffset, dataNullOffset) {
        companion object {
            fun fromBinary(bytes: BinaryBuffer): Fast8 = CodePointTrie.fromBinary(Type.FAST, ValueWidth.BITS_8, bytes) as Fast8
        }
    }

    class Small16 internal constructor(index: CharArray, data: CharArray, highStart: Int, index3NullOffset: Int, dataNullOffset: Int) :
        Small(index, TrieData.Bits16(data), highStart, index3NullOffset, dataNullOffset) {
        companion object {
            fun fromBinary(bytes: BinaryBuffer): Small16 = CodePointTrie.fromBinary(Type.SMALL, ValueWidth.BITS_16, bytes) as Small16
        }
    }

    class Small32 internal constructor(index: CharArray, data: IntArray, highStart: Int, index3NullOffset: Int, dataNullOffset: Int) :
        Small(index, TrieData.Bits32(data), highStart, index3NullOffset, dataNullOffset) {
        companion object {
            fun fromBinary(bytes: BinaryBuffer): Small32 = CodePointTrie.fromBinary(Type.SMALL, ValueWidth.BITS_32, bytes) as Small32
        }
    }

    class Small8 internal constructor(index: CharArray, data: ByteArray, highStart: Int, index3NullOffset: Int, dataNullOffset: Int) :
        Small(index, TrieData.Bits8(data), highStart, index3NullOffset, dataNullOffset) {
        companion object {
            fun fromBinary(bytes: BinaryBuffer): Small8 = CodePointTrie.fromBinary(Type.SMALL, ValueWidth.BITS_8, bytes) as Small8
        }
    }

    companion object {
        private const val MAX_UNICODE = 0x10ffff
        private const val ASCII_LIMIT = 0x80
        internal const val FAST_SHIFT = 6
        internal const val FAST_DATA_BLOCK_LENGTH = 1 shl FAST_SHIFT
        private const val FAST_DATA_MASK = FAST_DATA_BLOCK_LENGTH - 1
        private const val SMALL_MAX = 0xfff
        private const val ERROR_VALUE_NEG_DATA_OFFSET = 1
        private const val HIGH_VALUE_NEG_DATA_OFFSET = 2
        private const val BMP_INDEX_LENGTH = 0x10000 shr FAST_SHIFT
        internal const val SMALL_LIMIT = 0x1000
        private const val SMALL_INDEX_LENGTH = SMALL_LIMIT shr FAST_SHIFT
        internal const val SHIFT_3 = 4
        private const val SHIFT_2 = 5 + SHIFT_3
        private const val SHIFT_1 = 5 + SHIFT_2
        internal const val SHIFT_2_3 = SHIFT_2 - SHIFT_3
        internal const val SHIFT_1_2 = SHIFT_1 - SHIFT_2
        private const val OMITTED_BMP_INDEX_1_LENGTH = 0x10000 shr SHIFT_1
        internal const val INDEX_2_BLOCK_LENGTH = 1 shl SHIFT_1_2
        internal const val INDEX_2_MASK = INDEX_2_BLOCK_LENGTH - 1
        internal const val CP_PER_INDEX_2_ENTRY = 1 shl SHIFT_2
        internal const val INDEX_3_BLOCK_LENGTH = 1 shl SHIFT_2_3
        private const val INDEX_3_MASK = INDEX_3_BLOCK_LENGTH - 1
        internal const val SMALL_DATA_BLOCK_LENGTH = 1 shl SHIFT_3
        internal const val SMALL_DATA_MASK = SMALL_DATA_BLOCK_LENGTH - 1
        internal const val NO_INDEX3_NULL_OFFSET = 0x7fff
        internal const val NO_DATA_NULL_OFFSET = 0xfffff

        fun fromBinary(type: Type?, valueWidth: ValueWidth?, bytes: ByteArray): CodePointTrie = fromBinary(type, valueWidth, BinaryBuffer(bytes))

        fun fromBinary(type: Type?, valueWidth: ValueWidth?, bytes: BinaryBuffer): CodePointTrie {
            val originalOrder = bytes.order
            try {
                bytes.requireRemaining(16)
                when (bytes.readInt()) {
                    0x54726933 -> Unit
                    0x33697254 -> bytes.order = if (originalOrder == BinaryBuffer.ByteOrder.BIG_ENDIAN) BinaryBuffer.ByteOrder.LITTLE_ENDIAN else BinaryBuffer.ByteOrder.BIG_ENDIAN
                    else -> throw ICUUncheckedIOException("Buffer does not contain a serialized CodePointTrie")
                }
                val options = bytes.readUnsignedShort()
                val indexLength = bytes.readUnsignedShort()
                var dataLength = bytes.readUnsignedShort()
                val index3NullOffset = bytes.readUnsignedShort()
                var dataNullOffset = bytes.readUnsignedShort()
                val highStart = bytes.readUnsignedShort() shl SHIFT_2
                val actualType = when ((options shr 6) and 3) {
                    0 -> Type.FAST
                    1 -> Type.SMALL
                    else -> throw ICUUncheckedIOException("CodePointTrie has an unsupported type")
                }
                val actualWidth = when (options and 7) {
                    0 -> ValueWidth.BITS_16
                    1 -> ValueWidth.BITS_32
                    2 -> ValueWidth.BITS_8
                    else -> throw ICUUncheckedIOException("CodePointTrie has an unsupported value width")
                }
                if ((options and 0x38) != 0) throw ICUUncheckedIOException("CodePointTrie has unsupported options")
                if ((type != null && type != actualType) || (valueWidth != null && valueWidth != actualWidth)) {
                    throw ICUUncheckedIOException("CodePointTrie has a different type or value width than required")
                }
                dataLength = dataLength or ((options and 0xf000) shl 4)
                dataNullOffset = dataNullOffset or ((options and 0xf00) shl 8)
                val widthInBytes = when (actualWidth) { ValueWidth.BITS_8 -> 1; ValueWidth.BITS_16 -> 2; ValueWidth.BITS_32 -> 4 }
                bytes.requireRemaining(indexLength * 2 + dataLength * widthInBytes)
                val index = bytes.readChars(indexLength)
                return when (actualWidth) {
                    ValueWidth.BITS_8 -> {
                        val data = bytes.readBytes(dataLength)
                        if (actualType == Type.FAST) Fast8(index, data, highStart, index3NullOffset, dataNullOffset)
                        else Small8(index, data, highStart, index3NullOffset, dataNullOffset)
                    }
                    ValueWidth.BITS_16 -> {
                        val data = bytes.readChars(dataLength)
                        if (actualType == Type.FAST) Fast16(index, data, highStart, index3NullOffset, dataNullOffset)
                        else Small16(index, data, highStart, index3NullOffset, dataNullOffset)
                    }
                    ValueWidth.BITS_32 -> {
                        val data = bytes.readInts(dataLength)
                        if (actualType == Type.FAST) Fast32(index, data, highStart, index3NullOffset, dataNullOffset)
                        else Small32(index, data, highStart, index3NullOffset, dataNullOffset)
                    }
                }
            } finally {
                bytes.order = originalOrder
            }
        }
    }
}

internal sealed class TrieData(val width: CodePointTrie.ValueWidth, val size: Int, val widthInBytes: Int) {
    abstract operator fun get(index: Int): Int
    class Bits8(private val array: ByteArray) : TrieData(CodePointTrie.ValueWidth.BITS_8, array.size, 1) {
        override fun get(index: Int): Int = array[index].toInt() and 255
    }
    class Bits16(private val array: CharArray) : TrieData(CodePointTrie.ValueWidth.BITS_16, array.size, 2) {
        override fun get(index: Int): Int = array[index].code
    }
    class Bits32(private val array: IntArray) : TrieData(CodePointTrie.ValueWidth.BITS_32, array.size, 4) {
        override fun get(index: Int): Int = array[index]
    }
}
