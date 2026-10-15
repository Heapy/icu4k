// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.BinaryBuffer

/** ICU's legacy Unicode trie, including alternate lead-code-unit values. */
abstract class Trie2 protected constructor() : Iterable<Trie2.Range> {
    internal class UTrie2Header {
        var signature = 0x54726932
        var options = 0
        var indexLength = 0
        var shiftedDataLength = 0
        var index2NullOffset = 0
        var dataNullOffset = 0
        var shiftedHighStart = 0
    }

    internal var header = UTrie2Header()
    internal lateinit var index: CharArray
    internal var data16 = 0
    internal var data32: IntArray? = null
    internal var indexLength = 0
    internal var dataLength = 0
    internal var index2NullOffset = 0
    internal var initialValue = 0
    internal var errorValue = 0
    internal var highStart = 0
    internal var highValueIndex = 0
    internal var dataNullOffset = 0
    internal var fHash = 0

    abstract operator fun get(codePoint: Int): Int
    abstract fun getFromU16SingleLead(c: Char): Int
    internal abstract fun dataValue(index: Int): Int
    abstract fun getSerializedLength(): Int

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Trie2) return false
        val theirs = other.iterator()
        for (range in this) {
            if (!theirs.hasNext() || range != theirs.next()) return false
        }
        return !theirs.hasNext() && errorValue == other.errorValue && initialValue == other.initialValue
    }

    override fun hashCode(): Int {
        if (fHash == 0) {
            var hash = initHash()
            for (range in this) hash = hashInt(hash, range.hashCode())
            fHash = if (hash == 0) 1 else hash
        }
        return fHash
    }

    /** Inclusive range result; one instance is reused by each iterator. */
    open class Range {
        var startCodePoint = 0
        var endCodePoint = 0
        var value = 0
        var leadSurrogate = false
        override fun equals(other: Any?): Boolean = other is Range && other::class == this::class &&
            startCodePoint == other.startCodePoint && endCodePoint == other.endCodePoint &&
            value == other.value && leadSurrogate == other.leadSurrogate
        override fun hashCode(): Int {
            var hash = hashUChar32(initHash(), startCodePoint)
            hash = hashUChar32(hash, endCodePoint)
            hash = hashInt(hash, value)
            return hashByte(hash, if (leadSurrogate) 1 else 0)
        }
    }

    fun interface ValueMapper { fun map(originalVal: Int): Int }
    override fun iterator(): Iterator<Range> = iterator(identityMapper)
    fun iterator(mapper: ValueMapper): Iterator<Range> = Trie2Iterator(mapper, 0, 0x110000, true)
    fun iteratorForLeadSurrogate(lead: Char, mapper: ValueMapper = identityMapper): Iterator<Range> {
        require(lead.code in 0xd800..0xdbff) { "Bad lead surrogate value" }
        val start = (lead.code - 0xd7c0) shl 10
        return Trie2Iterator(mapper, start, start + 0x400, false)
    }

    private inner class Trie2Iterator(
        private val mapper: ValueMapper,
        private var nextStart: Int,
        private val limitCP: Int,
        private val doLeadSurrogates: Boolean,
    ) : Iterator<Range> {
        private val result = Range()
        private var doingCodePoints = true
        override fun hasNext(): Boolean = (doingCodePoints && (doLeadSurrogates || nextStart < limitCP)) || nextStart < 0xdc00
        override fun next(): Range {
            if (!hasNext()) throw NoSuchElementException()
            if (nextStart >= limitCP) {
                doingCodePoints = false
                nextStart = 0xd800
            }
            var end: Int
            val mapped: Int
            if (doingCodePoints) {
                val value = get(nextStart)
                mapped = mapper.map(value)
                end = rangeEnd(nextStart, limitCP, value)
                while (end < limitCP - 1) {
                    val nextValue = get(end + 1)
                    if (mapper.map(nextValue) != mapped) break
                    end = rangeEnd(end + 1, limitCP, nextValue)
                }
            } else {
                mapped = mapper.map(getFromU16SingleLead(nextStart.toChar()))
                end = rangeEndLS(nextStart)
                while (end < 0xdbff) {
                    if (mapper.map(getFromU16SingleLead((end + 1).toChar())) != mapped) break
                    end = rangeEndLS(end + 1)
                }
            }
            result.startCodePoint = nextStart
            result.endCodePoint = end
            result.value = mapped
            result.leadSurrogate = !doingCodePoints
            nextStart = end + 1
            return result
        }
        private fun rangeEndLS(start: Int): Int {
            val value = getFromU16SingleLead(start.toChar())
            var c = start + 1
            while (c <= 0xdbff && getFromU16SingleLead(c.toChar()) == value) c++
            return c - 1
        }
    }

    /** Skips null index/data blocks instead of scanning the whole Unicode range. */
    internal open fun rangeEnd(start: Int, limit: Int, value: Int): Int {
        var c = start + 1
        val scanLimit = minOf(highStart, limit)
        while (c < scanLimit && get(c) == value) c++
        if (c >= highStart) c = limit
        return c - 1
    }

    internal fun immutableRangeEnd(start: Int, limit: Int, value: Int): Int {
        var cp = start
        outer@ while (cp < limit) {
            val index2Block: Int
            val block: Int
            if (cp < 0xd800 || cp in 0xdc00..0xffff) {
                index2Block = 0
                block = index[cp shr UTRIE2_SHIFT_2].code shl UTRIE2_INDEX_SHIFT
            } else if (cp < 0xffff) {
                index2Block = UTRIE2_LSCP_INDEX_2_OFFSET
                block = index[index2Block + ((cp - 0xd800) shr UTRIE2_SHIFT_2)].code shl UTRIE2_INDEX_SHIFT
            } else if (cp < highStart) {
                val ix = UTRIE2_INDEX_1_OFFSET - UTRIE2_OMITTED_BMP_INDEX_1_LENGTH + (cp shr UTRIE2_SHIFT_1)
                index2Block = index[ix].code
                block = index[index2Block + ((cp shr UTRIE2_SHIFT_2) and UTRIE2_INDEX_2_MASK)].code shl UTRIE2_INDEX_SHIFT
            } else {
                if (value == dataValue(highValueIndex)) cp = limit
                break
            }
            if (index2Block == index2NullOffset) {
                if (value != initialValue) break
                cp += UTRIE2_CP_PER_INDEX_1_ENTRY
            } else if (block == dataNullOffset) {
                if (value != initialValue) break
                cp += UTRIE2_DATA_BLOCK_LENGTH
            } else {
                val startIndex = block + (cp and UTRIE2_DATA_MASK)
                val limitIndex = block + UTRIE2_DATA_BLOCK_LENGTH
                for (ix in startIndex until limitIndex) {
                    if (dataValue(ix) != value) {
                        cp += ix - startIndex
                        break@outer
                    }
                }
                cp += limitIndex - startIndex
            }
        }
        return minOf(cp, limit) - 1
    }

    open class CharSequenceValues {
        var index = 0
        var codePoint = 0
        var value = 0
    }

    fun charSequenceIterator(text: CharSequence, index: Int): CharSequenceIterator = CharSequenceIterator(text, index)
    inner class CharSequenceIterator internal constructor(private val text: CharSequence, index: Int) : Iterator<CharSequenceValues> {
        private var cursor = 0
        private val result = CharSequenceValues()
        init { set(index) }
        fun set(i: Int) {
            if (i !in 0..text.length) throw IndexOutOfBoundsException("String index: $i")
            cursor = i
        }
        override fun hasNext(): Boolean = cursor < text.length
        fun hasPrevious(): Boolean = cursor > 0
        override fun next(): CharSequenceValues {
            // ICU throws IndexOutOfBoundsException here when exhausted.
            val start = cursor
            var cp = text[cursor].code
            if (UTF16.isLeadSurrogate(cp) && cursor + 1 < text.length && UTF16.isTrailSurrogate(text[cursor + 1].code)) {
                cp = ((cp - 0xd800) shl 10) + text[cursor + 1].code - 0xdc00 + 0x10000
            }
            val value = get(cp)
            cursor += UTF16.getCharCount(cp)
            result.index = start
            result.codePoint = cp
            result.value = value
            return result
        }
        fun previous(): CharSequenceValues {
            var cp = text[cursor - 1].code
            if (UTF16.isTrailSurrogate(cp) && cursor > 1 && UTF16.isLeadSurrogate(text[cursor - 2].code)) {
                cp = ((text[cursor - 2].code - 0xd800) shl 10) + cp - 0xdc00 + 0x10000
            }
            val value = get(cp)
            cursor -= UTF16.getCharCount(cp)
            result.index = cursor
            result.codePoint = cp
            result.value = value
            return result
        }
    }

    /** Portable serialization equivalent of ICU's OutputStream API. */
    fun serialize(order: BinaryBuffer.ByteOrder = BinaryBuffer.ByteOrder.BIG_ENDIAN): ByteArray {
        val bytes = BinaryBuffer(ByteArray(getSerializedLength()), order)
        bytes.writeInt(header.signature)
        bytes.writeShort(header.options)
        bytes.writeShort(header.indexLength)
        bytes.writeShort(header.shiftedDataLength)
        bytes.writeShort(header.index2NullOffset)
        bytes.writeShort(header.dataNullOffset)
        bytes.writeShort(header.shiftedHighStart)
        for (i in 0 until indexLength) bytes.writeShort(index[i].code)
        for (i in 0 until dataLength) {
            if (data32 == null) bytes.writeShort(index[data16 + i].code)
            else bytes.writeInt(data32!![i])
        }
        return bytes.bytes
    }

    companion object {
        private val identityMapper = ValueMapper { it }
        internal const val UTRIE2_OPTIONS_VALUE_BITS_MASK = 0x000f
        internal const val UTRIE2_SHIFT_1 = 11
        internal const val UTRIE2_SHIFT_2 = 5
        internal const val UTRIE2_SHIFT_1_2 = UTRIE2_SHIFT_1 - UTRIE2_SHIFT_2
        internal const val UTRIE2_OMITTED_BMP_INDEX_1_LENGTH = 0x10000 shr UTRIE2_SHIFT_1
        internal const val UTRIE2_CP_PER_INDEX_1_ENTRY = 1 shl UTRIE2_SHIFT_1
        internal const val UTRIE2_INDEX_2_BLOCK_LENGTH = 1 shl UTRIE2_SHIFT_1_2
        internal const val UTRIE2_INDEX_2_MASK = UTRIE2_INDEX_2_BLOCK_LENGTH - 1
        internal const val UTRIE2_DATA_BLOCK_LENGTH = 1 shl UTRIE2_SHIFT_2
        internal const val UTRIE2_DATA_MASK = UTRIE2_DATA_BLOCK_LENGTH - 1
        internal const val UTRIE2_INDEX_SHIFT = 2
        internal const val UTRIE2_DATA_GRANULARITY = 1 shl UTRIE2_INDEX_SHIFT
        internal const val UTRIE2_INDEX_2_OFFSET = 0
        internal const val UTRIE2_LSCP_INDEX_2_OFFSET = 0x10000 shr UTRIE2_SHIFT_2
        internal const val UTRIE2_LSCP_INDEX_2_LENGTH = 0x400 shr UTRIE2_SHIFT_2
        internal const val UTRIE2_INDEX_2_BMP_LENGTH = UTRIE2_LSCP_INDEX_2_OFFSET + UTRIE2_LSCP_INDEX_2_LENGTH
        internal const val UTRIE2_UTF8_2B_INDEX_2_OFFSET = UTRIE2_INDEX_2_BMP_LENGTH
        internal const val UTRIE2_UTF8_2B_INDEX_2_LENGTH = 0x800 shr 6
        internal const val UTRIE2_INDEX_1_OFFSET = UTRIE2_UTF8_2B_INDEX_2_OFFSET + UTRIE2_UTF8_2B_INDEX_2_LENGTH
        internal const val UTRIE2_MAX_INDEX_1_LENGTH = 0x100000 shr UTRIE2_SHIFT_1
        internal const val UTRIE2_BAD_UTF8_DATA_OFFSET = 0x80
        internal const val UTRIE2_DATA_START_OFFSET = 0xc0
        internal const val UNEWTRIE2_INDEX_GAP_OFFSET = UTRIE2_INDEX_2_BMP_LENGTH
        internal const val UNEWTRIE2_INDEX_GAP_LENGTH =
            (UTRIE2_UTF8_2B_INDEX_2_LENGTH + UTRIE2_MAX_INDEX_1_LENGTH + UTRIE2_INDEX_2_MASK) and UTRIE2_INDEX_2_MASK.inv()
        internal const val UNEWTRIE2_MAX_INDEX_2_LENGTH = (0x110000 shr UTRIE2_SHIFT_2) + UTRIE2_LSCP_INDEX_2_LENGTH + UNEWTRIE2_INDEX_GAP_LENGTH + UTRIE2_INDEX_2_BLOCK_LENGTH
        internal const val UNEWTRIE2_INDEX_1_LENGTH = 0x110000 shr UTRIE2_SHIFT_1
        internal const val UNEWTRIE2_MAX_DATA_LENGTH = 0x110000 + 0x40 + 0x40 + 0x400

        fun createFromSerialized(bytes: ByteArray): Trie2 = createFromSerialized(BinaryBuffer(bytes))
        fun createFromSerialized(bytes: BinaryBuffer): Trie2 {
            val originalOrder = bytes.order
            try {
                bytes.requireRemaining(16)
                val header = UTrie2Header()
                when (bytes.readInt()) {
                    0x54726932 -> Unit
                    0x32697254 -> bytes.order = if (originalOrder == BinaryBuffer.ByteOrder.BIG_ENDIAN) BinaryBuffer.ByteOrder.LITTLE_ENDIAN else BinaryBuffer.ByteOrder.BIG_ENDIAN
                    else -> throw IllegalArgumentException("Buffer does not contain a serialized UTrie2")
                }
                header.options = bytes.readUnsignedShort()
                header.indexLength = bytes.readUnsignedShort()
                header.shiftedDataLength = bytes.readUnsignedShort()
                header.index2NullOffset = bytes.readUnsignedShort()
                header.dataNullOffset = bytes.readUnsignedShort()
                header.shiftedHighStart = bytes.readUnsignedShort()
                val width = header.options and UTRIE2_OPTIONS_VALUE_BITS_MASK
                require(width <= 1) { "UTrie2 serialized format error" }
                val trie: Trie2 = if (width == 0) Trie2_16() else Trie2_32()
                trie.header = header
                trie.indexLength = header.indexLength
                trie.dataLength = header.shiftedDataLength shl UTRIE2_INDEX_SHIFT
                trie.index2NullOffset = header.index2NullOffset
                trie.dataNullOffset = header.dataNullOffset
                trie.highStart = header.shiftedHighStart shl UTRIE2_SHIFT_1
                trie.highValueIndex = trie.dataLength - UTRIE2_DATA_GRANULARITY
                if (width == 0) trie.highValueIndex += trie.indexLength
                val indexSize = trie.indexLength + if (width == 0) trie.dataLength else 0
                bytes.requireRemaining(indexSize * 2 + if (width == 1) trie.dataLength * 4 else 0)
                trie.index = bytes.readChars(indexSize)
                if (width == 0) {
                    trie.data16 = trie.indexLength
                    trie.initialValue = trie.index[trie.dataNullOffset].code
                    trie.errorValue = trie.index[trie.data16 + UTRIE2_BAD_UTF8_DATA_OFFSET].code
                } else {
                    trie.data32 = bytes.readInts(trie.dataLength)
                    trie.initialValue = trie.data32!![trie.dataNullOffset]
                    trie.errorValue = trie.data32!![UTRIE2_BAD_UTF8_DATA_OFFSET]
                }
                return trie
            } finally {
                bytes.order = originalOrder
            }
        }

        /** Checks a stream signature without advancing the common buffer cursor. */
        fun getVersion(bytes: BinaryBuffer, littleEndianOk: Boolean): Int {
            if (bytes.remaining < 4) return 0
            var signature = 0
            for (i in 0..3) signature = (signature shl 8) or (bytes.getByte(bytes.position + i).toInt() and 255)
            return when (signature) {
                0x54726965 -> 1
                0x54726932 -> 2
                0x65697254 -> if (littleEndianOk) 1 else 0
                0x32697254 -> if (littleEndianOk) 2 else 0
                else -> 0
            }
        }

        private fun initHash(): Int = 0x811c9dc5.toInt()
        private fun hashByte(hash: Int, byte: Int): Int = (hash * 16777619) xor byte
        private fun hashUChar32(hash: Int, cp: Int): Int = hashByte(hashByte(hashByte(hash, cp and 255), (cp shr 8) and 255), cp shr 16)
        private fun hashInt(hash: Int, value: Int): Int = hashByte(hashByte(hashByte(hashByte(hash, value and 255), (value shr 8) and 255), (value shr 16) and 255), (value shr 24) and 255)
    }
}
