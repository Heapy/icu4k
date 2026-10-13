// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2004-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet

import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException

/** Unicode bidi/shaping property tables. The bidi reordering algorithm is separate. */
class UBiDiProps internal constructor(bytes: BinaryBuffer = ICUBinary.getRequiredData("ubidi.icu")) {
    private val indexes: IntArray
    private val trie: Trie2_16
    private val mirrors: IntArray
    private val joiningGroups: ByteArray
    private val joiningGroups2: ByteArray

    init {
        ICUBinary.readHeader(bytes, 0x42694469, ICUBinary.Authenticate { it[0] == 2.toByte() })
        val count = bytes.readInt()
        if (count < 16 || count - 1 > bytes.remaining / 4) throw ICUUncheckedIOException("indexes[0] too small in ubidi.icu")
        indexes = IntArray(count)
        indexes[0] = count
        for (i in 1 until count) indexes[i] = bytes.readInt()
        trie = Trie2_16.createFromSerialized(bytes)
        val trieLength = trie.getSerializedLength()
        if (trieLength > indexes[2]) throw ICUUncheckedIOException("ubidi.icu: not enough bytes for the trie")
        bytes.skip(indexes[2] - trieLength)
        mirrors = bytes.readInts(indexes[3])
        joiningGroups = bytes.readBytes(indexes[5] - indexes[4])
        joiningGroups2 = bytes.readBytes(indexes[7] - indexes[6])
    }

    fun getMaxValue(which: Int): Int = when (which) {
        UProperty.BIDI_CLASS -> indexes[15] and 31
        UProperty.JOINING_GROUP -> (indexes[15] and 0xff0000) shr 16
        UProperty.JOINING_TYPE -> (indexes[15] and 0xe0) shr 5
        UProperty.BIDI_PAIRED_BRACKET_TYPE -> (indexes[15] and 0x300) shr 8
        else -> -1
    }
    fun getClass(c: Int): Int = trie[c] and 31
    fun isMirrored(c: Int): Boolean = (trie[c] and (1 shl 12)) != 0
    fun isBidiControl(c: Int): Boolean = (trie[c] and (1 shl 11)) != 0
    fun isJoinControl(c: Int): Boolean = (trie[c] and (1 shl 10)) != 0
    fun getJoiningType(c: Int): Int = (trie[c] and 0xe0) shr 5
    fun getJoiningGroup(c: Int): Int = when (c) {
        in indexes[4] until indexes[5] -> joiningGroups[c - indexes[4]].toInt() and 255
        in indexes[6] until indexes[7] -> joiningGroups2[c - indexes[6]].toInt() and 255
        else -> 0
    }
    fun getPairedBracketType(c: Int): Int = (trie[c] and 0x300) shr 8
    fun getPairedBracket(c: Int): Int {
        val props = trie[c]
        return if ((props and 0x300) == 0) c else getMirror(c, props)
    }
    fun getMirror(c: Int): Int = getMirror(c, trie[c])
    private fun getMirror(c: Int, props: Int): Int {
        val delta = props.toShort().toInt() shr 13
        if (delta != -4) return c + delta
        for (entry in mirrors) {
            val cp = entry and 0x1fffff
            if (c == cp) return mirrors[entry ushr 21] and 0x1fffff
            if (c < cp) break
        }
        return c
    }

    fun addPropertyStarts(set: UnicodeSet) { forEachPropertyStart { set.add(it) } }

    /** Callback form for property-boundary enumeration. */
    internal fun forEachPropertyStart(add: (Int) -> Unit) {
        for (range in trie) {
            if (range.leadSurrogate) break
            add(range.startCodePoint)
        }
        for (entry in mirrors) {
            val c = entry and 0x1fffff
            add(c)
            add(c + 1)
        }
        fun addJoiningGroupStarts(start: Int, values: ByteArray) {
            var previous = 0.toByte()
            for (i in values.indices) {
                val value = values[i]
                if (value != previous) { add(start + i); previous = value }
            }
            if (previous != 0.toByte()) add(start + values.size)
        }
        addJoiningGroupStarts(indexes[4], joiningGroups)
        addJoiningGroupStarts(indexes[6], joiningGroups2)
    }

    companion object {
        val INSTANCE: UBiDiProps by lazy { UBiDiProps() }
    }
}
