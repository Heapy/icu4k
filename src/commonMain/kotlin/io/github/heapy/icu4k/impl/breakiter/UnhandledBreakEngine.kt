// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.CharacterIteration
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.concurrent.atomics.AtomicReference

/** Shared fallback for scripts without a dictionary engine. Published sets are never mutated. */
internal class UnhandledBreakEngine : LanguageBreakEngine {
    private val handled = AtomicReference(UnicodeSet())
    override fun handles(c: Int): Boolean = handled.load().contains(c)
    override fun findBreaks(text: CharacterIterator, startPos: Int, endPos: Int,
                            foundBreaks: DictionaryBreakEngine.DequeI, isPhraseBreaking: Boolean): Int {
        val set = handled.load()
        var c = CharacterIteration.current32(text)
        while (text.getIndex() < endPos && set.contains(c)) {
            CharacterIteration.next32(text)
            c = CharacterIteration.current32(text)
        }
        return 0
    }
    /** May overlap readers, but callers must serialize calls to this method, as in ICU4J. */
    fun handleChar(c: Int) {
        val original = handled.load()
        if (!original.contains(c)) {
            val script = UCharacter.getIntPropertyValue(c, UProperty.SCRIPT)
            val updated = UnicodeSet().applyIntPropertyValue(UProperty.SCRIPT, script)
            updated.addAll(original)
            handled.store(updated)
        }
    }
}
