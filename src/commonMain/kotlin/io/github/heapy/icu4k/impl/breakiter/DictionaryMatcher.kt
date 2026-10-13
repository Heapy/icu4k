// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2012, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.CharacterIterator

/** Matcher leaves text after its longest prefix, which may exceed its longest word. */
internal abstract class DictionaryMatcher {
    abstract fun matches(text: CharacterIterator, maxLength: Int, lengths: IntArray, count: IntArray, limit: Int, values: IntArray?): Int
    fun matches(text: CharacterIterator, maxLength: Int, lengths: IntArray, count: IntArray, limit: Int): Int =
        matches(text, maxLength, lengths, count, limit, null)
    abstract fun getType(): Int
}
