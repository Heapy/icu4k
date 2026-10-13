// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

/** Immutable 32-bit legacy Unicode trie. */
class Trie2_32 internal constructor() : Trie2() {
    override fun get(codePoint: Int): Int {
        if (codePoint < 0 || codePoint > 0x10ffff) return errorValue
        val ix = when {
            codePoint < 0xd800 || codePoint in 0xdc00..0xffff -> index[codePoint shr UTRIE2_SHIFT_2].code
            codePoint <= 0xffff -> index[UTRIE2_LSCP_INDEX_2_OFFSET + ((codePoint - 0xd800) shr UTRIE2_SHIFT_2)].code
            codePoint < highStart -> {
                val i1 = UTRIE2_INDEX_1_OFFSET - UTRIE2_OMITTED_BMP_INDEX_1_LENGTH + (codePoint shr UTRIE2_SHIFT_1)
                index[index[i1].code + ((codePoint shr UTRIE2_SHIFT_2) and UTRIE2_INDEX_2_MASK)].code
            }
            else -> return dataValue(highValueIndex)
        }
        return dataValue((ix shl UTRIE2_INDEX_SHIFT) + (codePoint and UTRIE2_DATA_MASK))
    }
    override fun getFromU16SingleLead(c: Char): Int =
        dataValue((index[c.code shr UTRIE2_SHIFT_2].code shl UTRIE2_INDEX_SHIFT) + (c.code and UTRIE2_DATA_MASK))
    internal override fun dataValue(index: Int): Int = data32!![index]
    internal override fun rangeEnd(start: Int, limit: Int, value: Int): Int = immutableRangeEnd(start, limit, value)
    override fun getSerializedLength(): Int = 16 + indexLength * 2 + dataLength * 4
    companion object {
        fun createFromSerialized(bytes: BinaryBuffer): Trie2_32 = Trie2.createFromSerialized(bytes) as Trie2_32
        fun createFromSerialized(bytes: ByteArray): Trie2_32 = createFromSerialized(BinaryBuffer(bytes))
    }
}
