// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2014-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.BreakIterator
import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.FilteredBreakIteratorBuilder
import io.github.heapy.icu4k.text.UCharacterIterator
import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.BytesTrie
import io.github.heapy.icu4k.util.CharsTrie
import io.github.heapy.icu4k.util.CharsTrieBuilder
import io.github.heapy.icu4k.util.StringTrieBuilder

/** Original backwards-exact/forwards-partial sentence boundary filter. */
class SimpleFilteredSentenceBreakIterator(
    private val delegate: BreakIterator,
    private val forwardsPartialTrie: CharsTrie?,
    private val backwardsTrie: CharsTrie?,
) : BreakIterator() {
    private var text: UCharacterIterator? = null
    private fun resetState() { text = UCharacterIterator.getInstance(delegate.getText().clone()) }
    private fun breakExceptionAt(n: Int): Boolean {
        val input = text!!
        val backwards = backwardsTrie!!
        var bestPosition = -1
        var bestValue = -1
        input.setIndex(n)
        backwards.reset()
        if (input.previousCodePoint() != ' '.code) input.nextCodePoint()
        while (true) {
            val c = input.previousCodePoint()
            if (c < 0) break
            val result = backwards.nextForCodePoint(c)
            if (result.hasValue()) { bestPosition = input.getIndex(); bestValue = backwards.getValue() }
            if (!result.hasNext()) break
        }
        backwards.reset()
        if (bestPosition >= 0) {
            if (bestValue == Builder.MATCH) return true
            val forwards = forwardsPartialTrie
            if (bestValue == Builder.PARTIAL && forwards != null) {
                forwards.reset()
                var result = BytesTrie.Result.INTERMEDIATE_VALUE
                input.setIndex(bestPosition)
                while (true) {
                    val c = input.nextCodePoint()
                    if (c == DONE) break
                    result = forwards.nextForCodePoint(c)
                    if (!result.hasNext()) break
                }
                forwards.reset()
                if (result.matches()) return true
            }
        }
        return false
    }
    private fun internalNext(initial: Int): Int {
        if (initial == DONE || backwardsTrie == null) return initial
        resetState()
        var n = initial
        val length = text!!.getLength()
        while (n != DONE && n != length) {
            if (breakExceptionAt(n)) n = delegate.next() else return n
        }
        return n
    }
    private fun internalPrev(initial: Int): Int {
        if (initial == 0 || initial == DONE || backwardsTrie == null) return initial
        resetState()
        var n = initial
        while (n != DONE && n != 0) {
            if (breakExceptionAt(n)) n = delegate.previous() else return n
        }
        return n
    }
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (other !is SimpleFilteredSentenceBreakIterator) return false
        // Preserve upstream identity comparisons and nullable-state behavior.
        return delegate == other.delegate && text!!.equals(other.text) &&
            backwardsTrie!!.equals(other.backwardsTrie) && forwardsPartialTrie!!.equals(other.forwardsPartialTrie)
    }
    override fun hashCode(): Int = forwardsPartialTrie!!.hashCode() * 39 + backwardsTrie!!.hashCode() * 11 + delegate.hashCode()
    override fun clone(): SimpleFilteredSentenceBreakIterator =
        SimpleFilteredSentenceBreakIterator(delegate.clone(), forwardsPartialTrie?.clone(), backwardsTrie?.clone()).also { it.text = text?.clone() }
    override fun first(): Int = delegate.first()
    override fun preceding(offset: Int): Int = internalPrev(delegate.preceding(offset))
    override fun previous(): Int = internalPrev(delegate.previous())
    override fun current(): Int = delegate.current()
    override fun isBoundary(offset: Int): Boolean {
        if (!delegate.isBoundary(offset)) return false
        if (backwardsTrie == null) return true
        resetState()
        return !breakExceptionAt(offset)
    }
    override fun next(): Int = internalNext(delegate.next())
    override fun next(n: Int): Int = internalNext(delegate.next(n))
    override fun following(offset: Int): Int = internalNext(delegate.following(offset))
    override fun last(): Int = delegate.last()
    override fun getText(): CharacterIterator = delegate.getText()
    override fun setText(newText: CharacterIterator) { delegate.setText(newText) }

    class Builder : FilteredBreakIteratorBuilder() {
        private val filterSet = HashSet<CharSequence>()
        override fun suppressBreakAfter(str: CharSequence): Boolean = filterSet.add(str)
        override fun unsuppressBreakAfter(str: CharSequence): Boolean = filterSet.remove(str)
        override fun wrapIteratorWithFilter(wrappedBreakIterator: BreakIterator): BreakIterator {
            if (filterSet.isEmpty()) return wrappedBreakIterator
            val backwards = CharsTrieBuilder()
            val forwards = CharsTrieBuilder()
            var reverseCount = 0
            var forwardCount = 0
            val strings = filterSet.toList()
            val partials = IntArray(strings.size)
            for (i in strings.indices) {
                val current = strings[i].toString()
                val dot = current.indexOf('.')
                if (dot >= 0 && dot + 1 != current.length) {
                    var sameAs = -1
                    for (j in strings.indices) {
                        if (j == i) continue
                        if (current.regionMatches(0, strings[j].toString(), 0, dot + 1)) {
                            if (partials[j] == 0) partials[j] = SuppressInReverse or AddToForward
                            else if (partials[j] and SuppressInReverse != 0) sameAs = j
                        }
                    }
                    if (sameAs == -1 && partials[i] == 0) {
                        backwards.add(UTF16.reverse(current.substring(0, dot + 1)), PARTIAL)
                        reverseCount++
                        partials[i] = SuppressInReverse or AddToForward
                    }
                }
            }
            for (i in strings.indices) {
                val current = strings[i].toString()
                if (partials[i] == 0) {
                    backwards.add(UTF16.reverse(current), MATCH)
                    reverseCount++
                } else {
                    forwards.add(current, MATCH)
                    forwardCount++
                }
            }
            return SimpleFilteredSentenceBreakIterator(wrappedBreakIterator,
                if (forwardCount > 0) forwards.build(StringTrieBuilder.Option.FAST) else null,
                if (reverseCount > 0) backwards.build(StringTrieBuilder.Option.FAST) else null)
        }
        companion object {
            const val PARTIAL = 1
            const val MATCH = 2
            const val SuppressInReverse = 1
            const val AddToForward = 2
        }
    }
}
