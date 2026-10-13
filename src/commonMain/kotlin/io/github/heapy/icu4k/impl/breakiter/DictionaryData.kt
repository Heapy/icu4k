// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2012-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.BinaryBuffer

/** Dictionary decoding and script-to-file resolution from the pinned root break bundle. */
internal object DictionaryData {
    const val TRIE_TYPE_BYTES = 0
    const val TRIE_TYPE_UCHARS = 1
    const val TRIE_TYPE_MASK = 7
    const val TRIE_HAS_VALUES = 8
    const val TRANSFORM_NONE = 0
    const val TRANSFORM_TYPE_OFFSET = 0x1000000
    const val TRANSFORM_TYPE_MASK = 0x7f000000
    const val TRANSFORM_OFFSET_MASK = 0x1fffff
    const val IX_STRING_TRIE_OFFSET = 0
    const val IX_RESERVED1_OFFSET = 1
    const val IX_RESERVED2_OFFSET = 2
    const val IX_TOTAL_SIZE = 3
    const val IX_TRIE_TYPE = 4
    const val IX_TRANSFORM = 5
    const val IX_RESERVED6 = 6
    const val IX_RESERVED7 = 7
    const val IX_COUNT = 8
    private const val DATA_FORMAT_ID = 0x44696374

    private val rootBundle by lazy { ICUResourceBundleReader(ICUBinary.getRequiredData("brkitr/root.res")) }
    private val dictionaries by lazy {
        rootBundle.getTable(rootBundle.getTable(rootBundle.rootResource)!!.getResource("dictionaries"))!!
    }
    /** The dictionaries table is defined in root; locale bundle fallback is ported separately. */
    fun loadDictionaryFor(dictType: String): DictionaryMatcher? {
        val filename = rootBundle.getString(dictionaries.getResource(dictType))
            ?: throw MissingResourceException("Can't find resource for dictionaries/$dictType", ICUData.ICU_BRKITR_BASE_NAME,
                "dictionaries/$dictType")
        return when (val data = read(ICUBinary.getRequiredData("${ICUData.ICU_BRKITR_NAME}/$filename"))) {
            is TrieData.Bytes -> data.matcher()
            is TrieData.Chars -> data.matcher()
            null -> null
        }
    }

    sealed class TrieData(val indexes: IntArray) {
        class Bytes(indexes: IntArray, val data: ByteArray) : TrieData(indexes) {
            fun matcher(): DictionaryMatcher = BytesDictionaryMatcher(data, indexes[IX_TRANSFORM])
        }
        class Chars(indexes: IntArray, val data: String) : TrieData(indexes) {
            fun matcher(): DictionaryMatcher = CharsDictionaryMatcher(data)
        }
    }
    fun read(bytes: BinaryBuffer): TrieData? {
        ICUBinary.readHeader(bytes, DATA_FORMAT_ID, null)
        val indexes = bytes.readInts(IX_COUNT)
        val offset = indexes[IX_STRING_TRIE_OFFSET]
        check(offset >= 4 * IX_COUNT)
        if (offset > 4 * IX_COUNT) ICUBinary.skipBytes(bytes, offset - 4 * IX_COUNT)
        val size = indexes[IX_TOTAL_SIZE] - offset
        return when (indexes[IX_TRIE_TYPE] and TRIE_TYPE_MASK) {
            TRIE_TYPE_BYTES -> TrieData.Bytes(indexes, bytes.readBytes(size))
            TRIE_TYPE_UCHARS -> {
                check(size % 2 == 0)
                TrieData.Chars(indexes, ICUBinary.getString(bytes, size / 2, size and 1))
            }
            else -> null
        }
    }
}
