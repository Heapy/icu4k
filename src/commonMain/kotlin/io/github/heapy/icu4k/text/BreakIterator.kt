// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.BreakIteratorFactoryCache
import io.github.heapy.icu4k.impl.BreakIteratorService
import io.github.heapy.icu4k.impl.CSCharacterIterator
import io.github.heapy.icu4k.util.ULocale

/** Boundary iterator contract, resource factories and locale service registration. */
abstract class BreakIterator protected constructor() {
    private var validLocale: ULocale? = null
    private var actualLocale: ULocale? = null
    fun getLocale(type: ULocale.Type?): ULocale? = if (type === ULocale.ACTUAL_LOCALE) actualLocale else validLocale
    internal fun setLocale(valid: ULocale?, actual: ULocale?) {
        if ((valid == null) != (actual == null)) throw IllegalArgumentException()
        validLocale = valid; actualLocale = actual
    }
    protected fun copyLocaleMetadataTo(target: BreakIterator) { target.validLocale = validLocale; target.actualLocale = actualLocale }
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
    open fun setText(newText: String?) { setText(StringCharacterIterator(newText!!)) }
    open fun setText(newText: CharSequence?) { setText(CSCharacterIterator(newText!!)) }
    abstract fun setText(newText: CharacterIterator)
    companion object {
        private val iteratorCache = BreakIteratorFactoryCache { locale, kind -> BreakIteratorService.create(locale, kind) }
        fun getWordInstance(): BreakIterator = getWordInstance(ULocale.getDefault())
        fun getWordInstance(where: ULocale?): BreakIterator = getBreakInstance(where, KIND_WORD)
        fun getLineInstance(): BreakIterator = getLineInstance(ULocale.getDefault())
        fun getLineInstance(where: ULocale?): BreakIterator = getBreakInstance(where, KIND_LINE)
        fun getCharacterInstance(): BreakIterator = getCharacterInstance(ULocale.getDefault())
        fun getCharacterInstance(where: ULocale?): BreakIterator = getBreakInstance(where, KIND_CHARACTER)
        fun getSentenceInstance(): BreakIterator = getSentenceInstance(ULocale.getDefault())
        fun getSentenceInstance(where: ULocale?): BreakIterator = getBreakInstance(where, KIND_SENTENCE)
        @Deprecated("Use word iteration for titlecasing") fun getTitleInstance(): BreakIterator = getBreakInstance(ULocale.getDefault(), KIND_TITLE)
        @Deprecated("Use word iteration for titlecasing") fun getTitleInstance(where: ULocale?): BreakIterator = getBreakInstance(where, KIND_TITLE)
        @Deprecated("This API is ICU internal only.") fun getBreakInstance(where: ULocale?, kind: Int): BreakIterator = iteratorCache.get(where, kind)
        fun registerInstance(iter: BreakIterator?, locale: ULocale?, kind: Int): Any {
            iteratorCache.clearMatching(locale, kind)
            return BreakIteratorService.registerInstance(iter, locale, kind)
        }
        fun unregister(key: Any?): Boolean {
            if (key == null) throw IllegalArgumentException("registry key must not be null")
            if (!BreakIteratorService.isInitialized()) return false
            iteratorCache.clear()
            return BreakIteratorService.unregister(key)
        }
        fun getAvailableULocales(): Array<ULocale> = BreakIteratorService.get().getAvailableULocales()
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
