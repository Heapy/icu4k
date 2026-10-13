// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.UnicodeSet

/** ICU's three-word Myanmar dictionary lookahead and unknown-word resynchronization. */
internal class BurmeseBreakEngine : DictionaryBreakEngine() {
    private val beginWordSet = UnicodeSet(0x1000, 0x102a).compact().freeze()
    private val endWordSet = UnicodeSet().applyIntPropertyValue(UProperty.SCRIPT, UScript.MYANMAR)
        .retainAll(UnicodeSet().applyPropertyAlias("LineBreak", "SA"))
        .compact().freeze()
    private val markSet = UnicodeSet(endWordSet)
        .retainAll(UnicodeSet().applyPropertyAlias("gc", "M"))
        .add(0x20).compact().freeze()
    private val dictionary = checkNotNull(DictionaryData.loadDictionaryFor("Mymr"))

    init { setCharacters(endWordSet) }
    override fun equals(other: Any?): Boolean = other is BurmeseBreakEngine
    override fun hashCode(): Int = BurmeseBreakEngine::class.hashCode()
    override fun handles(c: Int): Boolean = UCharacter.getIntPropertyValue(c, UProperty.SCRIPT) == UScript.MYANMAR

    override fun divideUpDictionaryRange(text: CharacterIterator, rangeStart: Int, rangeEnd: Int,
                                         foundBreaks: DequeI, isPhraseBreaking: Boolean): Int {
        if (rangeEnd - rangeStart < 2) return 0
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
                var foundBest = false
                if (text.getIndex() < rangeEnd) {
                    do {
                        if (words[(wordsFound + 1) % 3].candidates(text, dictionary, rangeEnd) > 0) {
                            words[wordsFound % 3].markCurrent()
                            if (text.getIndex() >= rangeEnd) break
                            do {
                                if (words[(wordsFound + 2) % 3].candidates(text, dictionary, rangeEnd) > 0) {
                                    words[wordsFound % 3].markCurrent()
                                    foundBest = true
                                    break
                                }
                            } while (words[(wordsFound + 1) % 3].backUp(text))
                        }
                    } while (words[wordsFound % 3].backUp(text) && !foundBest)
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
            if (wordLength > 0) foundBreaks.push(current + wordLength)
        }
        if (foundBreaks.peek() >= rangeEnd) {
            foundBreaks.pop()
            wordsFound--
        }
        return wordsFound
    }
}
