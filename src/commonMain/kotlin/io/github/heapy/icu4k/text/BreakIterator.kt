// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CSCharacterIterator

/** Boundary iterator contract. Locale factories and concrete iterators are being ported. */
abstract class BreakIterator protected constructor() {
    /** Concrete iterators supply their copy operation in Common Kotlin. */
    abstract fun clone(): BreakIterator
    abstract fun first(): Int
    abstract fun last(): Int
    abstract fun next(n: Int): Int
    abstract fun next(): Int
    abstract fun previous(): Int
    abstract fun following(offset: Int): Int
    open fun preceding(offset: Int): Int {
        if (offset < 0) return DONE
        var pos = following(offset)
        while (pos >= offset && pos != DONE) pos = previous()
        return pos
    }
    open fun isBoundary(offset: Int): Boolean = offset == 0 || following(offset - 1) == offset
    abstract fun current(): Int
    open fun getRuleStatus(): Int = 0
    open fun getRuleStatusVec(fillInArray: IntArray?): Int {
        if (fillInArray != null && fillInArray.isNotEmpty()) fillInArray[0] = 0
        return 1
    }
    abstract fun getText(): CharacterIterator
    open fun setText(newText: String) { setText(StringCharacterIterator(newText)) }
    open fun setText(newText: CharSequence) { setText(CSCharacterIterator(newText)) }
    abstract fun setText(newText: CharacterIterator)
    companion object {
        const val DONE = -1
        const val WORD_NONE = 0
        const val WORD_NONE_LIMIT = 100
        const val WORD_NUMBER = 100
        const val WORD_NUMBER_LIMIT = 200
        const val WORD_LETTER = 200
        const val WORD_LETTER_LIMIT = 300
        const val WORD_KANA = 300
        const val WORD_KANA_LIMIT = 400
        const val WORD_IDEO = 400
        const val WORD_IDEO_LIMIT = 500
        const val KIND_CHARACTER = 0
        const val KIND_WORD = 1
        const val KIND_LINE = 2
        const val KIND_SENTENCE = 3
        @Deprecated("Use word iteration for titlecasing") const val KIND_TITLE = 4
    }
}
