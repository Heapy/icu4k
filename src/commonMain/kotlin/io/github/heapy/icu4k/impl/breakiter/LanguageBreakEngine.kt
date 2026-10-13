// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.CharacterIterator

internal interface LanguageBreakEngine {
    fun handles(c: Int): Boolean
    fun findBreaks(text: CharacterIterator, startPos: Int, endPos: Int,
                   foundBreaks: DictionaryBreakEngine.DequeI, isPhraseBreaking: Boolean): Int
}
