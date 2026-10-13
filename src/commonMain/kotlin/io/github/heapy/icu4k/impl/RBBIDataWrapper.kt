// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.CodePointTrie
import io.github.heapy.icu4k.util.ICUUncheckedIOException

/** Compiled ICU break rules; offsets are relative to the rule header. */
internal class RBBIDataWrapper private constructor() {
    class RBBIDataHeader {
        var fMagic = 0
        var fFormatVersion = ByteArray(4)
        var fLength = 0
        var fCatCount = 0
        var fFTable = 0
        var fFTableLen = 0
        var fRTable = 0
        var fRTableLen = 0
        var fTrie = 0
        var fTrieLen = 0
        var fRuleSource = 0
        var fRuleSourceLen = 0
        var fStatusTable = 0
        var fStatusTableLen = 0
    }
    class RBBIStateTable {
        var fNumStates = 0
        var fRowLen = 0
        var fDictCategoriesStart = 0
        var fLookAheadResultsSize = 0
        var fFlags = 0
        var fTable = CharArray(0)

        /** Common counterpart of writing the table to a DataOutputStream. */
        fun toBinary(): ByteArray {
            val size = fHeaderSize + fRowLen * fNumStates
            val bytes = BinaryBuffer(ByteArray((size + 7) and -8))
            bytes.writeInt(fNumStates); bytes.writeInt(fRowLen)
            bytes.writeInt(fDictCategoriesStart); bytes.writeInt(fLookAheadResultsSize); bytes.writeInt(fFlags)
            if (fFlags and RBBI_8BITS_ROWS != 0) {
                repeat(fRowLen * fNumStates) { bytes.writeByte(fTable[it].code) }
            } else {
                repeat(fRowLen * fNumStates / 2) { bytes.writeShort(fTable[it].code) }
            }
            return bytes.bytes
        }
        override fun equals(other: Any?): Boolean = other is RBBIStateTable &&
            fNumStates == other.fNumStates && fRowLen == other.fRowLen &&
            fDictCategoriesStart == other.fDictCategoriesStart && fLookAheadResultsSize == other.fLookAheadResultsSize &&
            fFlags == other.fFlags && fTable.contentEquals(other.fTable)
        override fun hashCode(): Int {
            var fields = 1
            for (n in intArrayOf(fDictCategoriesStart, fFlags, fLookAheadResultsSize, fNumStates, fRowLen)) fields = 31 * fields + n
            return 31 * (31 + fTable.contentHashCode()) + fields
        }
        companion object {
            const val fHeaderSize = 20
            fun get(bytes: BinaryBuffer, length: Int): RBBIStateTable? {
                if (length == 0) return null
                if (length < fHeaderSize) throw ICUUncheckedIOException("Invalid RBBI state table length.")
                val table = RBBIStateTable()
                table.fNumStates = bytes.readInt(); table.fRowLen = bytes.readInt()
                table.fDictCategoriesStart = bytes.readInt(); table.fLookAheadResultsSize = bytes.readInt(); table.fFlags = bytes.readInt()
                val count = length - fHeaderSize
                if (table.fFlags and RBBI_8BITS_ROWS != 0) {
                    table.fTable = CharArray(count) { bytes.readUnsignedByte().toChar() }
                    ICUBinary.skipBytes(bytes, count and 1)
                } else table.fTable = ICUBinary.getChars(bytes, count / 2, count and 1)
                return table
            }
        }
    }
    lateinit var fHeader: RBBIDataHeader
    var fFTable: RBBIStateTable? = null
    var fRTable: RBBIStateTable? = null
    lateinit var fTrie: CodePointTrie
    lateinit var fRuleSource: String
    lateinit var fStatusTable: IntArray
    fun getRowIndex(state: Int): Int = state * (fHeader.fCatCount + NEXTSTATES)

    companion object {
        const val DATA_FORMAT = 0x42726b20
        const val FORMAT_VERSION = 0x06000000
        const val DH_SIZE = 20
        const val DH_MAGIC = 0
        const val DH_FORMATVERSION = 1
        const val DH_LENGTH = 2
        const val DH_CATCOUNT = 3
        const val DH_FTABLE = 4
        const val DH_FTABLELEN = 5
        const val DH_RTABLE = 6
        const val DH_RTABLELEN = 7
        const val DH_TRIE = 8
        const val DH_TRIELEN = 9
        const val DH_RULESOURCE = 10
        const val DH_RULESOURCELEN = 11
        const val DH_STATUSTABLE = 12
        const val DH_STATUSTABLELEN = 13
        const val ACCEPTING = 0
        const val LOOKAHEAD = 1
        const val TAGSIDX = 2
        const val NEXTSTATES = 3
        const val ACCEPTING_UNCONDITIONAL = 1
        const val RBBI_LOOKAHEAD_HARD_BREAK = 1
        const val RBBI_BOF_REQUIRED = 2
        const val RBBI_8BITS_ROWS = 4
        private fun acceptable(version: ByteArray): Boolean = version.contentEquals(byteArrayOf(6, 0, 0, 0))
        fun get(bytes: BinaryBuffer): RBBIDataWrapper {
            val result = RBBIDataWrapper()
            ICUBinary.readHeader(bytes, DATA_FORMAT, object : ICUBinary.Authenticate {
                override fun isDataVersionAcceptable(version: ByteArray): Boolean = acceptable(version)
            })
            val h = RBBIDataHeader()
            result.fHeader = h
            h.fMagic = bytes.readInt(); h.fFormatVersion = bytes.readBytes(4)
            h.fLength = bytes.readInt(); h.fCatCount = bytes.readInt()
            h.fFTable = bytes.readInt(); h.fFTableLen = bytes.readInt()
            h.fRTable = bytes.readInt(); h.fRTableLen = bytes.readInt()
            h.fTrie = bytes.readInt(); h.fTrieLen = bytes.readInt()
            h.fRuleSource = bytes.readInt(); h.fRuleSourceLen = bytes.readInt()
            h.fStatusTable = bytes.readInt(); h.fStatusTableLen = bytes.readInt()
            ICUBinary.skipBytes(bytes, 24)
            if (h.fMagic != 0xb1a0 || !acceptable(h.fFormatVersion)) throw ICUUncheckedIOException("Break Iterator Rule Data Magic Number Incorrect, or unsupported data version.")
            var pos = DH_SIZE * 4
            if (h.fFTable < pos || h.fFTable > h.fLength) throw ICUUncheckedIOException("Break iterator Rule data corrupt")
            ICUBinary.skipBytes(bytes, h.fFTable - pos)
            result.fFTable = RBBIStateTable.get(bytes, h.fFTableLen)
            pos = h.fFTable + h.fFTableLen
            ICUBinary.skipBytes(bytes, h.fRTable - pos)
            result.fRTable = RBBIStateTable.get(bytes, h.fRTableLen)
            pos = h.fRTable + h.fRTableLen
            ICUBinary.skipBytes(bytes, h.fTrie - pos)
            pos = h.fTrie
            val triePosition = bytes.position
            result.fTrie = CodePointTrie.fromBinary(CodePointTrie.Type.FAST, null, bytes)
            bytes.position = triePosition
            if (pos > h.fStatusTable) throw ICUUncheckedIOException("Break iterator Rule data corrupt")
            ICUBinary.skipBytes(bytes, h.fStatusTable - pos)
            result.fStatusTable = ICUBinary.getInts(bytes, h.fStatusTableLen / 4, h.fStatusTableLen and 3)
            pos = h.fStatusTable + h.fStatusTableLen
            if (pos > h.fRuleSource) throw ICUUncheckedIOException("Break iterator Rule data corrupt")
            ICUBinary.skipBytes(bytes, h.fRuleSource - pos)
            result.fRuleSource = ICUBinary.getBytes(bytes, h.fRuleSourceLen, 0).decodeToString()
            return result
        }
    }
}
