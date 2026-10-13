// © 2021 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.CharsTrie
import io.github.heapy.icu4k.util.CodePointMap
import io.github.heapy.icu4k.util.CodePointTrie
import io.github.heapy.icu4k.util.ICUUncheckedIOException

/** Code-point flags and string tries from the original uemoji.icu format. */
internal class EmojiProps private constructor() {
    private val cpTrie: CodePointTrie
    private val stringTries = arrayOfNulls<String>(6)
    init {
        val bytes = ICUBinary.getRequiredData("uemoji.icu")
        ICUBinary.readHeaderAndDataVersion(bytes, 0x456d6f6a, ICUBinary.Authenticate { it[0].toInt() == 1 })
        val start = bytes.position
        val cpTrieOffset = bytes.readInt()
        val count = cpTrieOffset / 4
        if (count <= 9) throw ICUUncheckedIOException("Emoji properties data: not enough indexes")
        val indexes = IntArray(count)
        indexes[0] = cpTrieOffset
        for (i in 1 until count) indexes[i] = bytes.readInt()
        cpTrie = CodePointTrie.fromBinary(CodePointTrie.Type.FAST, CodePointTrie.ValueWidth.BITS_8, bytes)
        ICUBinary.skipBytes(bytes, indexes[1] - (bytes.position - start))
        ICUBinary.skipBytes(bytes, indexes[4] - indexes[1])
        for (i in 4..9) if (indexes[i + 1] > indexes[i]) stringTries[i - 4] = ICUBinary.getString(bytes, (indexes[i + 1] - indexes[i]) / 2, 0)
    }
    fun addPropertyStarts(set: UnicodeSet): UnicodeSet {
        val range = CodePointMap.Range()
        var start = 0
        while (cpTrie.getRange(start, null, range)) { set.add(start); start = range.getEnd() + 1 }
        return set
    }
    fun hasBinaryProperty(c: Int, which: Int): Boolean {
        if (which < UProperty.EMOJI || which > UProperty.RGI_EMOJI) return false
        val bit = bitFlags[which - UProperty.EMOJI]
        return bit >= 0 && (cpTrie[c] shr bit) and 1 != 0
    }
    fun hasBinaryProperty(s: CharSequence, which: Int): Boolean {
        if (s.isEmpty() || which < UProperty.BASIC_EMOJI || which > UProperty.RGI_EMOJI) return false
        val first = if (which == UProperty.RGI_EMOJI) UProperty.BASIC_EMOJI else which
        val last = if (which == UProperty.RGI_EMOJI) UProperty.RGI_EMOJI_ZWJ_SEQUENCE else which
        for (prop in first..last) {
            val data = stringTries[prop - UProperty.BASIC_EMOJI] ?: continue
            if (CharsTrie(data, 0).next(s, 0, s.length).hasValue()) return true
        }
        return false
    }
    fun addStrings(which: Int, set: UnicodeSet) {
        if (which < UProperty.BASIC_EMOJI || which > UProperty.RGI_EMOJI) return
        val first = if (which == UProperty.RGI_EMOJI) UProperty.BASIC_EMOJI else which
        val last = if (which == UProperty.RGI_EMOJI) UProperty.RGI_EMOJI_ZWJ_SEQUENCE else which
        for (prop in first..last) {
            val data = stringTries[prop - UProperty.BASIC_EMOJI] ?: continue
            for (entry in CharsTrie(data, 0)) set.add(entry.chars)
        }
    }
    companion object {
        val INSTANCE: EmojiProps by lazy { EmojiProps() }
        private val bitFlags = intArrayOf(0, 1, 2, 3, 4, -1, -1, 5, 6, -1, -1, -1, -1, -1, 6)
    }
}
