// © 2022 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.CharacterIteration
import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.text.UTF16

/** Original Japanese AdaBoost phrase model and six-code-point scoring window. */
internal class MlBreakEngine(private val digitOrOpenPunctuationOrAlphabetSet: UnicodeSet,
                             private val closePunctuationSet: UnicodeSet) {
    private val model = List(13) { HashMap<String, Int>() }
    private var negativeSum = 0
    init {
        val reader = ICUResourceBundleReader(ICUBinary.getRequiredData("brkitr/jaml.res"))
        val root = reader.getTable(reader.rootResource)!!
        val names = (1..6).map { "UW$it" } + (1..3).map { "BW$it" } + (1..4).map { "TW$it" }
        for ((index, name) in names.withIndex()) {
            val keys = reader.getArray(root.getResource(name + "Keys"))!!
            val values = reader.getIntVector(root.getResource(name + "Values"))!!
            for (i in 0 until keys.size) {
                negativeSum -= values[i]
                model[index][reader.getString(keys.getContainerResource(i))!!] = values[i]
            }
        }
        negativeSum /= 2
    }
    fun divideUpRange(text: CharacterIterator, startPos: Int, endPos: Int, normalized: CharacterIterator,
                      codePointLength: Int, charPositions: IntArray, foundBreaks: DictionaryBreakEngine.DequeI): Int {
        if (startPos >= endPos) return 0
        normalized.setIndex(0)
        val source = buildString {
            var c = normalized.first()
            while (c != CharacterIterator.DONE) { append(c); c = normalized.next() }
        }
        val indexes = IntArray(codePointLength + 4) { -1 }
        var numCodeUnits = 0
        normalized.setIndex(0)
        if (codePointLength > 0) {
            indexes[2] = 0
            numCodeUnits += UTF16.getCharCount(CharacterIteration.current32(normalized))
            for (i in 1..minOf(3, codePointLength - 1)) {
                indexes[i + 2] = numCodeUnits
                numCodeUnits += UTF16.getCharCount(CharacterIteration.next32(normalized))
            }
        }
        val boundaries = ArrayList<Int>(codePointLength)
        boundaries.add(0)
        for (idx in 0 until codePointLength - 1) {
            evaluateBreakpoint(source, indexes, idx, numCodeUnits, boundaries)
            if (idx + 4 < codePointLength) {
                indexes[idx + 6] = numCodeUnits
                numCodeUnits += UTF16.getCharCount(CharacterIteration.next32(normalized))
            }
        }
        if (boundaries.last() != codePointLength) boundaries.add(codePointLength)
        var corrected = 0
        var previous = -1
        for (boundary in boundaries) {
            val position = charPositions[boundary] + startPos
            text.setIndex(position)
            if (position > previous && (position != startPos ||
                    (position > 0 && closePunctuationSet.contains(CharacterIteration.previous32(text))))) {
                foundBreaks.push(position)
                corrected++
            }
            previous = position
        }
        if (!foundBreaks.isEmpty() && foundBreaks.peek() == endPos) {
            text.setIndex(endPos)
            val current = CharacterIteration.current32(text)
            if (current != CharacterIteration.DONE32 && !digitOrOpenPunctuationOrAlphabetSet.contains(current)) {
                foundBreaks.pop(); corrected--
            }
        }
        if (!foundBreaks.isEmpty()) text.setIndex(foundBreaks.peek())
        return corrected
    }
    private fun evaluateBreakpoint(source: String, indexes: IntArray, startIdx: Int, numCodeUnits: Int,
                                    boundaries: MutableList<Int>) {
        var score = negativeSum
        for (i in 0 until 6) {
            val start = startIdx + i
            if (indexes[start] != -1) {
                val end = if (indexes[start + 1] != -1) indexes[start + 1] else numCodeUnits
                score += model[i][source.substring(indexes[start], end)] ?: 0
            }
        }
        for (i in 0 until 3) {
            val start = startIdx + i + 1
            if (indexes[start] != -1 && indexes[start + 1] != -1) {
                val end = if (indexes[start + 2] != -1) indexes[start + 2] else numCodeUnits
                score += model[6 + i][source.substring(indexes[start], end)] ?: 0
            }
        }
        for (i in 0 until 4) {
            val start = startIdx + i
            if (indexes[start] != -1 && indexes[start + 1] != -1 && indexes[start + 2] != -1) {
                val end = if (indexes[start + 3] != -1) indexes[start + 3] else numCodeUnits
                score += model[9 + i][source.substring(indexes[start], end)] ?: 0
            }
        }
        if (score > 0) boundaries.add(startIdx + 1)
    }
}
