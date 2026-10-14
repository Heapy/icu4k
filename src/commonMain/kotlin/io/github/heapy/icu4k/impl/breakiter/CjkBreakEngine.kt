// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2012-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.CharacterIteration
import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.ICUConfig
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.Normalizer
import io.github.heapy.icu4k.text.StringCharacterIterator
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.text.UnicodeSetIterator
import io.github.heapy.icu4k.text.UTF16

/** ICU dictionary-based CJK segmentation with optional Japanese ML phrase breaking. */
internal class CjkBreakEngine(private val korean: Boolean, private val useMlPhraseBreaking: Boolean? = null) : DictionaryBreakEngine() {
    private val hangulWordSet = UnicodeSet(0xac00, 0xd7a3).freeze()
    private val digitOrOpenPunctuationOrAlphabetSet = propertyUnion("Nd", "Pi", "Ps")
        .addAll(UnicodeSet().applyPropertyAlias("Alphabetic", "")).freeze()
    private val closePunctuationSet = propertyUnion("Pc", "Pd", "Pe", "Pf", "Po").freeze()
    private val dictionary = checkNotNull(DictionaryData.loadDictionaryFor("Hira"))
    private val skipSet = HashSet<String>()
    private var mlBreakEngine: MlBreakEngine? = null
    init {
        if (korean) setCharacters(hangulWordSet)
        else {
            val cj = UnicodeSet().applyPropertyAlias("sc", "Han")
                .addAll(UnicodeSet().applyPropertyAlias("sc", "Hiragana"))
                .addAll(UnicodeSet().applyPropertyAlias("sc", "Katakana"))
                .add(0x30fc).add(0xff70).add(0xff9e).add(0xff9f)
            setCharacters(cj)
            if (useMl()) {
                mlBreakEngine = MlBreakEngine(digitOrOpenPunctuationOrAlphabetSet, closePunctuationSet)
            } else {
                val ja = ICUResourceBundleReader(ICUBinary.getRequiredData("brkitr/ja.res"))
                val extensions = ja.getArray(ja.getTable(ja.rootResource)!!.getResource("extensions"))!!
                for (i in 0 until extensions.size) skipSet.add(ja.getString(extensions.getContainerResource(i))!!)
                val hiragana = UnicodeSetIterator(UnicodeSet().applyPropertyAlias("sc", "Hiragana").freeze())
                while (hiragana.next()) skipSet.add(hiragana.getString()!!)
            }
        }
    }
    private fun useMl(): Boolean = useMlPhraseBreaking ?: "true".equals(
        ICUConfig.get("com.ibm.icu.impl.breakiter.useMLPhraseBreaking", "false"), ignoreCase = true)
    override fun equals(other: Any?): Boolean = other is CjkBreakEngine && korean == other.korean
    override fun hashCode(): Int = CjkBreakEngine::class.hashCode()

    override fun divideUpDictionaryRange(text: CharacterIterator, rangeStart: Int, rangeEnd: Int,
                                         foundBreaks: DequeI, isPhraseBreaking: Boolean): Int {
        if (rangeStart >= rangeEnd) return 0
        text.setIndex(rangeStart)
        val source = buildString {
            while (text.getIndex() < rangeEnd) { append(text.current()); text.next() }
        }
        val normalized = Normalizer.quickCheck(source, Normalizer.NFKC) === Normalizer.YES ||
            Normalizer.isNormalized(source, Normalizer.NFKC, 0)
        var positions = IntArray(rangeEnd - rangeStart + 1)
        val cursor: CharacterIterator
        var numCodePoints = 0
        if (normalized) {
            cursor = StringCharacterIterator(source)
            var index = 0
            while (index < source.length) {
                index += UTF16.getCharCount(UTF16.charAt(source, index))
                positions[++numCodePoints] = index
            }
        } else {
            val normString = Normalizer.normalize(source, Normalizer.NFKC)
            cursor = StringCharacterIterator(normString)
            positions = IntArray(normString.length + 1)
            val normalizer = Normalizer(source, Normalizer.NFKC, 0)
            var index = 0
            while (index < normalizer.endIndex()) {
                normalizer.next()
                positions[++numCodePoints] = normalizer.getIndex()
                index = normalizer.getIndex()
            }
        }
        if (useMl() && isPhraseBreaking && !korean) {
            return mlBreakEngine!!.divideUpRange(text, rangeStart, rangeEnd, cursor, numCodePoints, positions, foundBreaks)
        }
        val best = IntArray(numCodePoints + 1) { Int.MAX_VALUE }
        best[0] = 0
        val prev = IntArray(numCodePoints + 1) { -1 }
        val values = IntArray(numCodePoints)
        val lengths = IntArray(numCodePoints)
        cursor.setIndex(0)
        var previousKatakana = false
        for (i in 0 until numCodePoints) {
            val ix = cursor.getIndex()
            if (best[i] != Int.MAX_VALUE) {
                val maximum = minOf(20, numCodePoints - i)
                val countArray = IntArray(1)
                dictionary.matches(cursor, maximum, lengths, countArray, maximum, values)
                var count = countArray[0]
                cursor.setIndex(ix)
                val cp = CharacterIteration.current32(cursor)
                if ((count == 0 || lengths[0] != 1) && cp != CharacterIteration.DONE32 && !hangulWordSet.contains(cp)) {
                    values[count] = 255
                    lengths[count++] = 1
                }
                for (j in 0 until count) {
                    val cost = best[i] + values[j]
                    if (cost < best[lengths[j] + i]) { best[lengths[j] + i] = cost; prev[lengths[j] + i] = i }
                }
                val katakana = isKatakana(CharacterIteration.current32(cursor))
                if (!previousKatakana && katakana) {
                    var j = i + 1
                    CharacterIteration.next32(cursor)
                    while (j < numCodePoints && j - i < 20 && isKatakana(CharacterIteration.current32(cursor))) {
                        CharacterIteration.next32(cursor)
                        j++
                    }
                    if (j - i < 20) {
                        val cost = best[i] + katakanaCost(j - i)
                        if (cost < best[j]) { best[j] = cost; prev[j] = i }
                    }
                }
                previousKatakana = katakana
            }
            cursor.setIndex(ix)
            CharacterIteration.next32(cursor)
        }
        val boundaries = IntArray(numCodePoints + 1)
        var numBreaks = 0
        if (best[numCodePoints] == Int.MAX_VALUE) boundaries[numBreaks++] = numCodePoints
        else if (isPhraseBreaking) {
            boundaries[numBreaks++] = numCodePoints
            var previousIndex = numCodePoints
            var i = prev[numCodePoints]
            while (i > 0) {
                val codeUnitIndex = UTF16.moveCodePointOffset(source, 0, i)
                val previousCodeUnitIndex = UTF16.moveCodePointOffset(source, 0, previousIndex)
                val length = previousCodeUnitIndex - codeUnitIndex
                previousIndex = i
                val pattern = buildString {
                    if (length > 0) {
                        cursor.setIndex(codeUnitIndex)
                        append(cursor.current())
                        for (n in 1 until length) append(cursor.next())
                    }
                }
                cursor.setIndex(codeUnitIndex)
                if (pattern !in skipSet && (!isKatakana(CharacterIteration.current32(cursor)) ||
                        !isKatakana(CharacterIteration.previous32(cursor)))) boundaries[numBreaks++] = i
                i = prev[i]
            }
        } else {
            var i = numCodePoints
            while (i > 0) { boundaries[numBreaks++] = i; i = prev[i] }
            check(prev[boundaries[numBreaks - 1]] == 0)
        }
        if (foundBreaks.isEmpty() || foundBreaks.peek() < rangeStart) boundaries[numBreaks++] = 0
        var corrected = 0
        var previous = -1
        for (i in numBreaks - 1 downTo 0) {
            val position = positions[boundaries[i]] + rangeStart
            text.setIndex(position)
            if (position > previous && (position != rangeStart || (isPhraseBreaking && position > 0 &&
                    closePunctuationSet.contains(CharacterIteration.previous32(text))))) {
                foundBreaks.push(position)
                corrected++
            }
            previous = position
        }
        if (!foundBreaks.isEmpty() && foundBreaks.peek() == rangeEnd) {
            if (isPhraseBreaking) {
                text.setIndex(rangeEnd)
                val current = CharacterIteration.current32(text)
                if (current != CharacterIteration.DONE32 && !digitOrOpenPunctuationOrAlphabetSet.contains(current)) {
                    foundBreaks.pop(); corrected--
                }
            } else { foundBreaks.pop(); corrected-- }
        }
        if (!foundBreaks.isEmpty()) text.setIndex(foundBreaks.peek())
        return corrected
    }
    companion object {
        private fun propertyUnion(vararg values: String): UnicodeSet = UnicodeSet().also { set ->
            for (value in values) set.addAll(UnicodeSet().applyPropertyAlias("gc", value))
        }
        private fun isKatakana(value: Int): Boolean = (value in 0x30a1..0x30fe && value != 0x30fb) || value in 0xff66..0xff9f
        private fun katakanaCost(length: Int): Int = if (length > 8) 8192 else
            intArrayOf(8192, 984, 408, 240, 204, 252, 300, 372, 480)[length]
    }
}
