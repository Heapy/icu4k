// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.UCharacterIterator
import io.github.heapy.icu4k.util.BytesTrie
import io.github.heapy.icu4k.util.CharsTrie

internal class CharsDictionaryMatcher(private val characters: CharSequence) : DictionaryMatcher() {
    override fun matches(text: CharacterIterator, maxLength: Int, lengths: IntArray, count: IntArray, limit: Int, values: IntArray?): Int {
        val iterator = UCharacterIterator.getInstance(text)
        val trie = CharsTrie(characters, 0)
        var c = iterator.nextCodePoint()
        if (c == UCharacterIterator.DONE) return 0
        var result = trie.firstForCodePoint(c)
        // Preserve ICU's code-point count here, even though the contract mentions code units.
        var numChars = 1
        var found = 0
        while (true) {
            if (result.hasValue()) {
                if (found < limit) {
                    if (values != null) values[found] = trie.getValue()
                    lengths[found++] = numChars
                }
                if (result == BytesTrie.Result.FINAL_VALUE) break
            } else if (result == BytesTrie.Result.NO_MATCH) break
            if (numChars >= maxLength) break
            c = iterator.nextCodePoint()
            if (c == UCharacterIterator.DONE) break
            ++numChars
            result = trie.nextForCodePoint(c)
        }
        count[0] = found
        return numChars
    }
    override fun getType(): Int = DictionaryData.TRIE_TYPE_UCHARS
}
