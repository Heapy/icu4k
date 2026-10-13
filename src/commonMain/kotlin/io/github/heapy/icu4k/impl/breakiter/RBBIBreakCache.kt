// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.RBBIRuleEngine

/** Original 128-entry circular boundary cache, bound to explicit iterator state. */
internal class RBBIBreakCache(private val engine: RBBIRuleEngine, private val dictionary: RBBIDictionaryCache) {
    var startBufIdx = 0
        private set
    var endBufIdx = 0
        private set
    var textIdx = 0
        private set
    var bufIdx = 0
        private set
    var boundaries = IntArray(CACHE_SIZE)
        private set
    var statuses = ShortArray(CACHE_SIZE)
        private set
    private val sideBuffer = DictionaryBreakEngine.DequeI()
    init { reset() }
    fun reset(pos: Int = 0, ruleStatus: Int = 0) {
        startBufIdx = 0; endBufIdx = 0; textIdx = pos; bufIdx = 0
        boundaries[0] = pos; statuses[0] = ruleStatus.toShort()
    }
    fun copyFor(engine: RBBIRuleEngine, dictionary: RBBIDictionaryCache): RBBIBreakCache =
        RBBIBreakCache(engine, dictionary).also {
            it.startBufIdx = startBufIdx; it.endBufIdx = endBufIdx; it.textIdx = textIdx; it.bufIdx = bufIdx
            it.boundaries = boundaries.copyOf(); it.statuses = statuses.copyOf()
        }
    fun next() {
        if (bufIdx == endBufIdx) {
            engine.done = !populateFollowing()
            engine.position = textIdx; engine.ruleStatusIndex = statuses[bufIdx].toInt()
        } else {
            bufIdx = mod(bufIdx + 1)
            textIdx = boundaries[bufIdx]; engine.position = textIdx; engine.ruleStatusIndex = statuses[bufIdx].toInt()
        }
    }
    fun previous() {
        val initialBufIdx = bufIdx
        if (bufIdx == startBufIdx) populatePreceding()
        else { bufIdx = mod(bufIdx - 1); textIdx = boundaries[bufIdx] }
        engine.done = bufIdx == initialBufIdx
        engine.position = textIdx; engine.ruleStatusIndex = statuses[bufIdx].toInt()
    }
    fun following(startPos: Int) {
        if (startPos == textIdx || seek(startPos) || populateNear(startPos)) { engine.done = false; next() }
    }
    fun preceding(startPos: Int) {
        if (startPos == textIdx || seek(startPos) || populateNear(startPos)) {
            if (startPos == textIdx) previous() else current()
        }
    }
    fun current(): Int {
        engine.position = textIdx; engine.ruleStatusIndex = statuses[bufIdx].toInt(); engine.done = false
        return textIdx
    }
    fun populateNear(position: Int): Boolean {
        val text = engine.text
        val startOfText = text.getBeginIndex()
        var boundary = -1
        var ruleStatusIndex = 0
        val retainCache: Boolean
        if (position > boundaries[startBufIdx] - 15 && position < boundaries[endBufIdx] + 15) retainCache = true
        else if (position <= startOfText + 15) { retainCache = false; boundary = startOfText }
        else {
            val backupPos = engine.handleSafePrevious(position)
            if (boundaries[endBufIdx] < position && boundaries[endBufIdx] >= backupPos - 15) retainCache = true
            else if (backupPos < startOfText + 15) {
                boundary = startOfText
                retainCache = boundaries[startBufIdx] <= position + 15
            } else {
                retainCache = false
                engine.position = backupPos
                boundary = engine.handleNext()
                if (advancedOneCodePoint(backupPos, boundary)) boundary = engine.handleNext()
                if (boundary == -1) boundary = text.getEndIndex()
                ruleStatusIndex = engine.ruleStatusIndex
            }
        }
        if (!retainCache) reset(boundary, ruleStatusIndex)
        if (boundaries[endBufIdx] < position) {
            while (boundaries[endBufIdx] < position) if (!populateFollowing()) return false
            bufIdx = endBufIdx; textIdx = boundaries[bufIdx]
            while (textIdx > position) previous()
            return true
        }
        if (boundaries[startBufIdx] > position) {
            while (boundaries[startBufIdx] > position) populatePreceding()
            bufIdx = startBufIdx; textIdx = boundaries[bufIdx]
            while (textIdx < position) next()
            if (textIdx > position) previous()
            return true
        }
        return true
    }
    private fun advancedOneCodePoint(start: Int, end: Int): Boolean = end == start + 1 ||
        (end == start + 2 && engine.text.setIndex(start).code in 0xd800..0xdbff && engine.text.next().code in 0xdc00..0xdfff)
    fun populateFollowing(): Boolean {
        val fromPosition = boundaries[endBufIdx]
        val fromStatus = statuses[endBufIdx].toInt()
        if (dictionary.following(fromPosition)) { addFollowing(dictionary.boundary, dictionary.statusIndex, true); return true }
        engine.position = fromPosition
        var pos = engine.handleNext()
        if (pos == -1) return false
        val ruleStatus = engine.ruleStatusIndex
        if (engine.dictionaryCharCount > 0) {
            dictionary.populateDictionary(fromPosition, pos, fromStatus, ruleStatus)
            if (dictionary.following(fromPosition)) { addFollowing(dictionary.boundary, dictionary.statusIndex, true); return true }
        }
        addFollowing(pos, ruleStatus, true)
        repeat(6) {
            pos = engine.handleNext()
            if (pos == -1 || engine.dictionaryCharCount > 0) return true
            addFollowing(pos, engine.ruleStatusIndex, false)
        }
        return true
    }
    fun populatePreceding(): Boolean {
        val textBegin = engine.text.getBeginIndex()
        val fromPosition = boundaries[startBufIdx]
        if (fromPosition == textBegin) return false
        var position = textBegin
        var positionStatus = 0
        if (dictionary.preceding(fromPosition)) { addPreceding(dictionary.boundary, dictionary.statusIndex, true); return true }
        var backupPosition = fromPosition
        do {
            backupPosition -= 30
            backupPosition = if (backupPosition <= textBegin) textBegin else engine.handleSafePrevious(backupPosition)
            if (backupPosition == -1 || backupPosition == textBegin) { position = textBegin; positionStatus = 0 }
            else {
                engine.position = backupPosition
                position = engine.handleNext()
                if (advancedOneCodePoint(backupPosition, position)) position = engine.handleNext()
                positionStatus = engine.ruleStatusIndex
            }
        } while (position >= fromPosition)
        sideBuffer.removeAllElements()
        sideBuffer.push(position); sideBuffer.push(positionStatus)
        do {
            var prevPosition = position
            engine.position = position
            val prevStatus = positionStatus
            position = engine.handleNext()
            positionStatus = engine.ruleStatusIndex
            if (position == -1) break
            var handledByDictionary = false
            if (engine.dictionaryCharCount != 0) {
                val dictSegEnd = position
                dictionary.populateDictionary(prevPosition, dictSegEnd, prevStatus, positionStatus)
                while (dictionary.following(prevPosition)) {
                    position = dictionary.boundary; positionStatus = dictionary.statusIndex
                    handledByDictionary = true
                    if (position >= fromPosition) break
                    sideBuffer.push(position); sideBuffer.push(positionStatus)
                    prevPosition = position
                }
            }
            if (!handledByDictionary && position < fromPosition) { sideBuffer.push(position); sideBuffer.push(positionStatus) }
        } while (position < fromPosition)
        var success = false
        if (!sideBuffer.isEmpty()) {
            positionStatus = sideBuffer.pop(); position = sideBuffer.pop()
            addPreceding(position, positionStatus, true)
            success = true
        }
        while (!sideBuffer.isEmpty()) {
            positionStatus = sideBuffer.pop(); position = sideBuffer.pop()
            if (!addPreceding(position, positionStatus, false)) break
        }
        return success
    }
    fun addFollowing(position: Int, ruleStatusIdx: Int, update: Boolean) {
        val nextIdx = mod(endBufIdx + 1)
        if (nextIdx == startBufIdx) startBufIdx = mod(startBufIdx + 6)
        boundaries[nextIdx] = position; statuses[nextIdx] = ruleStatusIdx.toShort(); endBufIdx = nextIdx
        if (update) { bufIdx = nextIdx; textIdx = position }
    }
    fun addPreceding(position: Int, ruleStatusIdx: Int, update: Boolean): Boolean {
        val nextIdx = mod(startBufIdx - 1)
        if (nextIdx == endBufIdx) {
            if (bufIdx == endBufIdx && !update) return false
            endBufIdx = mod(endBufIdx - 1)
        }
        boundaries[nextIdx] = position; statuses[nextIdx] = ruleStatusIdx.toShort(); startBufIdx = nextIdx
        if (update) { bufIdx = nextIdx; textIdx = position }
        return true
    }
    fun seek(pos: Int): Boolean {
        if (pos < boundaries[startBufIdx] || pos > boundaries[endBufIdx]) return false
        if (pos == boundaries[startBufIdx]) { bufIdx = startBufIdx; textIdx = boundaries[bufIdx]; return true }
        if (pos == boundaries[endBufIdx]) { bufIdx = endBufIdx; textIdx = boundaries[bufIdx]; return true }
        var min = startBufIdx
        var max = endBufIdx
        while (min != max) {
            val probe = mod((min + max + if (min > max) CACHE_SIZE else 0) / 2)
            if (boundaries[probe] > pos) max = probe else min = mod(probe + 1)
        }
        bufIdx = mod(max - 1); textIdx = boundaries[bufIdx]
        return true
    }
    companion object {
        const val CACHE_SIZE = 128
        private fun mod(index: Int): Int = index and (CACHE_SIZE - 1)
    }
}
