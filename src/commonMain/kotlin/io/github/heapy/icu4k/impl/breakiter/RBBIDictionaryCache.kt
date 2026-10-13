// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.CharacterIteration
import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.text.CharacterIterator

/** Original RuleBasedBreakIterator dictionary cache, with explicit owner dependencies. */
internal class RBBIDictionaryCache(private var text: CharacterIterator, private val data: RBBIDataWrapper,
                                   private val languageEngine: (Int) -> LanguageBreakEngine?,
                                   private val phraseBreaking: Boolean) {
    var breaks = DictionaryBreakEngine.DequeI()
        private set
    var positionInCache = -1
        private set
    var start = 0
        private set
    var limit = 0
        private set
    private var firstRuleStatusIndex = 0
    private var otherRuleStatusIndex = 0
    var boundary = 0
        private set
    var statusIndex = 0
        private set
    fun copyFor(text: CharacterIterator, languageEngine: (Int) -> LanguageBreakEngine?): RBBIDictionaryCache =
        RBBIDictionaryCache(text, data, languageEngine, phraseBreaking).also {
            it.breaks = breaks.clone()
            it.positionInCache = positionInCache
            it.start = start; it.limit = limit
            it.firstRuleStatusIndex = firstRuleStatusIndex; it.otherRuleStatusIndex = otherRuleStatusIndex
            it.boundary = boundary; it.statusIndex = statusIndex
        }
    fun setText(text: CharacterIterator) { this.text = text }
    fun reset() {
        positionInCache = -1; start = 0; limit = 0
        firstRuleStatusIndex = 0; otherRuleStatusIndex = 0
        breaks.removeAllElements()
    }
    fun following(fromPos: Int): Boolean {
        if (fromPos >= limit || fromPos < start) { positionInCache = -1; return false }
        if (positionInCache >= 0 && positionInCache < breaks.size() && breaks.elementAt(positionInCache) == fromPos) {
            positionInCache++
            if (positionInCache >= breaks.size()) { positionInCache = -1; return false }
            boundary = breaks.elementAt(positionInCache)
            statusIndex = otherRuleStatusIndex
            return true
        }
        positionInCache = 0
        while (positionInCache < breaks.size()) {
            val r = breaks.elementAt(positionInCache)
            if (r > fromPos) { boundary = r; statusIndex = otherRuleStatusIndex; return true }
            positionInCache++
        }
        positionInCache = -1
        return false
    }
    fun preceding(fromPos: Int): Boolean {
        if (fromPos <= start || fromPos > limit) { positionInCache = -1; return false }
        if (fromPos == limit) positionInCache = breaks.size() - 1
        if (positionInCache > 0 && positionInCache < breaks.size() && breaks.elementAt(positionInCache) == fromPos) {
            positionInCache--
            boundary = breaks.elementAt(positionInCache)
            statusIndex = if (boundary == start) firstRuleStatusIndex else otherRuleStatusIndex
            return true
        }
        if (positionInCache == 0) { positionInCache = -1; return false }
        positionInCache = breaks.size() - 1
        while (positionInCache >= 0) {
            val r = breaks.elementAt(positionInCache)
            if (r < fromPos) {
                boundary = r
                statusIndex = if (r == start) firstRuleStatusIndex else otherRuleStatusIndex
                return true
            }
            positionInCache--
        }
        positionInCache = -1
        return false
    }
    fun populateDictionary(startPos: Int, endPos: Int, firstRuleStatus: Int, otherRuleStatus: Int) {
        if (endPos - startPos <= 1) return
        reset()
        firstRuleStatusIndex = firstRuleStatus; otherRuleStatusIndex = otherRuleStatus
        text.setIndex(startPos)
        var c = CharacterIteration.current32(text)
        var category = data.fTrie[c].toShort().toInt()
        val dictStart = data.fFTable!!.fDictCategoriesStart
        var foundBreakCount = 0
        while (true) {
            while (text.getIndex() < endPos && category < dictStart) {
                c = CharacterIteration.next32(text)
                category = data.fTrie[c].toShort().toInt()
            }
            if (text.getIndex() >= endPos) break
            val engine = languageEngine(c)
            if (engine != null) foundBreakCount += engine.findBreaks(text, startPos, endPos, breaks, phraseBreaking)
            c = CharacterIteration.current32(text)
            category = data.fTrie[c].toShort().toInt()
        }
        if (foundBreakCount > 0) {
            if (startPos < breaks.elementAt(0)) breaks.offer(startPos)
            if (endPos > breaks.peek()) breaks.push(endPos)
            positionInCache = 0
            start = breaks.elementAt(0)
            limit = breaks.peek()
        }
    }
}
