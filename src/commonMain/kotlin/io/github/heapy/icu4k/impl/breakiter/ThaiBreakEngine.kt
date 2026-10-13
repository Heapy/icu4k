// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.UnicodeSet

/** ICU's three-word Thai dictionary lookahead and unknown-word resynchronization. */
internal class ThaiBreakEngine : DictionaryBreakEngine() {
    private val wordSet = UnicodeSet().applyIntPropertyValue(UProperty.SCRIPT, UScript.THAI)
        .retainAll(UnicodeSet().applyPropertyAlias("LineBreak", "SA")).compact().freeze()
    private val beginWordSet = UnicodeSet(0x0e01, 0x0e2e).add(0x0e40, 0x0e44).compact().freeze()
    private val endWordSet = UnicodeSet(wordSet).remove(0x0e31).remove(0x0e40, 0x0e44).compact().freeze()
    private val suffixSet = UnicodeSet().add(0x0e2f).add(0x0e46).compact().freeze()
    private val markSet = UnicodeSet(wordSet)
        .retainAll(UnicodeSet().applyPropertyAlias("gc", "M"))
        .add(0x20).compact().freeze()
    // The pinned root break resource maps Thai to thaidict.dict. Resource-bundle lookup
    // is pending; this reads the same embedded dictionary through the common binary decoder.
    private val dictionary = (DictionaryData.read(ICUBinary.getRequiredData("brkitr/thaidict.dict"))
        as DictionaryData.TrieData.Bytes).matcher()

    init { setCharacters(wordSet) }
    override fun equals(other: Any?): Boolean = other is ThaiBreakEngine
    override fun hashCode(): Int = ThaiBreakEngine::class.hashCode()
    override fun handles(c: Int): Boolean = UCharacter.getIntPropertyValue(c, UProperty.SCRIPT) == UScript.THAI

    override fun divideUpDictionaryRange(text: CharacterIterator, rangeStart: Int, rangeEnd: Int,
                                         foundBreaks: DequeI, isPhraseBreaking: Boolean): Int {
        if (rangeEnd - rangeStart < 4) return 0
        var wordsFound = 0
        val words = Array(3) { PossibleWord() }
        text.setIndex(rangeStart)
        while (text.getIndex() < rangeEnd) {
            val current = text.getIndex()
            var wordLength = 0
            val candidates = words[wordsFound % 3].candidates(text, dictionary, rangeEnd)
            if (candidates == 1) {
                wordLength = words[wordsFound % 3].acceptMarked(text)
                wordsFound++
            } else if (candidates > 1) {
                if (text.getIndex() < rangeEnd) {
                    foundBest@ do {
                        if (words[(wordsFound + 1) % 3].candidates(text, dictionary, rangeEnd) > 0) {
                            words[wordsFound % 3].markCurrent()
                            if (text.getIndex() >= rangeEnd) break@foundBest
                            do {
                                if (words[(wordsFound + 2) % 3].candidates(text, dictionary, rangeEnd) > 0) {
                                    words[wordsFound % 3].markCurrent()
                                    break@foundBest
                                }
                            } while (words[(wordsFound + 1) % 3].backUp(text))
                        }
                    } while (words[wordsFound % 3].backUp(text))
                }
                wordLength = words[wordsFound % 3].acceptMarked(text)
                wordsFound++
            }
            if (text.getIndex() < rangeEnd && wordLength < 3) {
                if (words[wordsFound % 3].candidates(text, dictionary, rangeEnd) <= 0 &&
                    (wordLength == 0 || words[wordsFound % 3].longestPrefix() < 3)) {
                    var remaining = rangeEnd - (current + wordLength)
                    var pc = text.current().code
                    var chars = 0
                    while (true) {
                        text.next()
                        val uc = text.current().code
                        chars++
                        if (--remaining <= 0) break
                        if (endWordSet.contains(pc) && beginWordSet.contains(uc)) {
                            val candidate = words[(wordsFound + 1) % 3].candidates(text, dictionary, rangeEnd)
                            text.setIndex(current + wordLength + chars)
                            if (candidate > 0) break
                        }
                        pc = uc
                    }
                    if (wordLength <= 0) wordsFound++
                    wordLength += chars
                } else {
                    text.setIndex(current + wordLength)
                }
            }
            while (text.getIndex() < rangeEnd && markSet.contains(text.current().code)) {
                val currPos = text.getIndex()
                text.next()
                wordLength += text.getIndex() - currPos
            }
            // Attach PAIYANNOI and MAIYAMOK only when a dictionary word does not follow.
            if (text.getIndex() < rangeEnd && wordLength > 0) {
                if (words[wordsFound % 3].candidates(text, dictionary, rangeEnd) <= 0 &&
                    suffixSet.contains(text.current().code)) {
                    var uc = text.current().code
                    if (uc == 0x0e2f) {
                        if (!suffixSet.contains(text.previous().code)) {
                            text.next()
                            text.next()
                            wordLength++
                            uc = text.current().code
                        } else {
                            text.next()
                        }
                    }
                    if (uc == 0x0e46) {
                        if (text.previous().code != 0x0e46) {
                            text.next()
                            text.next()
                            wordLength++
                        } else {
                            text.next()
                        }
                    }
                } else {
                    text.setIndex(current + wordLength)
                }
            }
            if (wordLength > 0) foundBreaks.push(current + wordLength)
        }
        if (foundBreaks.peek() >= rangeEnd) {
            foundBreaks.pop()
            wordsFound--
        }
        return wordsFound
    }
}
