// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.UCharacterIterator
import io.github.heapy.icu4k.util.BytesTrie

internal class BytesDictionaryMatcher(private val characters: ByteArray, private val transform: Int) : DictionaryMatcher() {
    init { check(transform and 0x7f000000 == 0x1000000) { "Unsupported dictionary transform" } }
    private fun transform(c: Int): Int {
        if (c == 0x200d) return 255
        if (c == 0x200c) return 254
        val delta = c - (transform and 0x1fffff)
        return if (delta in 0..253) delta else -1
    }
    override fun matches(text: CharacterIterator, maxLength: Int, lengths: IntArray, count: IntArray, limit: Int, values: IntArray?): Int {
        val iterator = UCharacterIterator.getInstance(text)
        val trie = BytesTrie(characters, 0)
        var c = iterator.nextCodePoint()
        if (c == UCharacterIterator.DONE) return 0
        var result = trie.first(transform(c))
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
            result = trie.next(transform(c))
        }
        count[0] = found
        return numChars
    }
    override fun getType(): Int = 0
}
