// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CharacterIterator

/** Rule state machines from RuleBasedBreakIterator; caches/dictionaries are separate layers. */
internal class RBBIRuleEngine(val data: RBBIDataWrapper, var text: CharacterIterator) {
    var position = text.getBeginIndex()
    var ruleStatusIndex = 0
    var dictionaryCharCount = 0
    var done = false
    private val lookAheadMatches = IntArray(data.fFTable!!.fLookAheadResultsSize)

    /** ICU4J clones scalar state but allocates a fresh lookahead array. */
    fun copyFor(text: CharacterIterator): RBBIRuleEngine = RBBIRuleEngine(data, text).also {
        it.position = position; it.ruleStatusIndex = ruleStatusIndex
        it.dictionaryCharCount = dictionaryCharCount; it.done = done
    }
    fun handleNext(): Int {
        ruleStatusIndex = 0
        dictionaryCharCount = 0
        val forward = data.fFTable!!
        val table = forward.fTable
        val initialPosition = position
        text.setIndex(initialPosition)
        var result = initialPosition
        var c = text.current().code
        if (c >= 0xd800) {
            c = CharacterIteration.nextTrail32(text, c)
            if (c == CharacterIteration.DONE32) { done = true; return -1 }
        }
        var state = 1
        var row = data.getRowIndex(state)
        var category = 3
        var mode = 1
        if (forward.fFlags and RBBIDataWrapper.RBBI_BOF_REQUIRED != 0) { category = 2; mode = 0 }
        while (state != 0) {
            if (c == CharacterIteration.DONE32) {
                if (mode == 2) break
                mode = 2
                category = 1
            } else if (mode == 1) {
                category = data.fTrie.get(c).toShort().toInt()
                if (category >= forward.fDictCategoriesStart) ++dictionaryCharCount
                c = text.next().code
                if (c >= 0xd800) c = CharacterIteration.nextTrail32(text, c)
            } else mode = 1
            state = table[row + RBBIDataWrapper.NEXTSTATES + category].code
            row = data.getRowIndex(state)
            val accepting = table[row + RBBIDataWrapper.ACCEPTING].code
            if (accepting == RBBIDataWrapper.ACCEPTING_UNCONDITIONAL) {
                result = text.getIndex()
                if (c in 0x10000..0x10ffff) --result
                ruleStatusIndex = table[row + RBBIDataWrapper.TAGSIDX].code
            } else if (accepting > RBBIDataWrapper.ACCEPTING_UNCONDITIONAL) {
                val lookaheadResult = lookAheadMatches[accepting]
                if (lookaheadResult >= 0) {
                    ruleStatusIndex = table[row + RBBIDataWrapper.TAGSIDX].code
                    position = lookaheadResult
                    return lookaheadResult
                }
            }
            val rule = table[row + RBBIDataWrapper.LOOKAHEAD].code
            if (rule != 0) {
                var pos = text.getIndex()
                if (c in 0x10000..0x10ffff) --pos
                lookAheadMatches[rule] = pos
            }
        }
        if (result == initialPosition) {
            text.setIndex(initialPosition)
            CharacterIteration.next32(text)
            result = text.getIndex()
            ruleStatusIndex = 0
        }
        position = result
        return result
    }
    fun handleSafePrevious(fromPosition: Int): Int {
        setIndex32(text, fromPosition)
        if (text.getIndex() == text.getBeginIndex()) return -1
        val table = data.fRTable!!.fTable
        var c = CharacterIteration.previous32(text)
        var state = 1
        var row = data.getRowIndex(state)
        while (c != CharacterIteration.DONE32) {
            val category = data.fTrie.get(c).toShort().toInt()
            state = table[row + RBBIDataWrapper.NEXTSTATES + category].code
            row = data.getRowIndex(state)
            if (state == 0) break
            c = CharacterIteration.previous32(text)
        }
        return text.getIndex()
    }
    companion object {
        fun setIndex32(text: CharacterIterator, index: Int): Int {
            if (index <= text.getBeginIndex()) text.first()
            else if (index >= text.getEndIndex()) text.setIndex(text.getEndIndex())
            else if (text.setIndex(index).code in 0xdc00..0xdfff) {
                if (text.previous().code !in 0xd800..0xdbff) text.next()
            }
            return text.getIndex()
        }
    }
}
