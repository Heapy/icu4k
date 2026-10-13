// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.CodePointTrie
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import io.github.heapy.icu4k.util.VersionInfo

/** Unicode layout property tables, ported from UCharacterProperty.LayoutProps. */
internal class LayoutProps(bytes: BinaryBuffer = ICUBinary.getRequiredData("ulayout.icu")) {
    val unicodeVersion: VersionInfo
    val inpcTrie: CodePointTrie?
    val inscTrie: CodePointTrie?
    val voTrie: CodePointTrie?
    val maxInpcValue: Int
    val maxInscValue: Int
    val maxVoValue: Int

    init {
        unicodeVersion = ICUBinary.readHeaderAndDataVersion(bytes, 0x4c61796f, ICUBinary.Authenticate { it[0] == 1.toByte() })
        val startPosition = bytes.position
        val indexesLength = bytes.readInt()
        if (indexesLength < 12 || indexesLength - 1 > bytes.remaining / 4) {
            throw ICUUncheckedIOException("Text layout properties data: not enough indexes")
        }
        val indexes = IntArray(indexesLength)
        indexes[0] = indexesLength
        for (i in 1 until indexesLength) indexes[i] = bytes.readInt()
        var offset = indexesLength * 4
        fun readTrie(top: Int): CodePointTrie? {
            if (top < offset || top > bytes.limit - startPosition) throw ICUUncheckedIOException("Invalid layout trie offset")
            val trie = if (top - offset >= 16) CodePointTrie.fromBinary(null, null, bytes) else null
            if (bytes.position - startPosition > top) throw ICUUncheckedIOException("Layout trie exceeds its data section")
            bytes.position = startPosition + top
            offset = top
            return trie
        }
        inpcTrie = readTrie(indexes[1])
        inscTrie = readTrie(indexes[2])
        voTrie = readTrie(indexes[3])
        val maxValues = indexes[9]
        maxInpcValue = maxValues ushr 24
        maxInscValue = (maxValues shr 16) and 255
        maxVoValue = (maxValues shr 8) and 255
    }

    companion object {
        val INSTANCE: LayoutProps by lazy { LayoutProps() }
    }
}
