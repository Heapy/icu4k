// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.CharacterIterator

/** Shared dictionary helpers; UnicodeSet and language-engine integration are pending. */
internal abstract class DictionaryBreakEngine {
    class PossibleWord {
        private val lengths = IntArray(20)
        private val count = IntArray(1)
        private var prefix = 0
        private var offset = -1
        private var mark = 0
        private var current = 0
        fun candidates(iter: CharacterIterator, dictionary: DictionaryMatcher, rangeEnd: Int): Int {
            val start = iter.getIndex()
            if (start != offset) {
                offset = start
                prefix = dictionary.matches(iter, rangeEnd - start, lengths, count, lengths.size)
                if (count[0] <= 0) iter.setIndex(start)
            }
            if (count[0] > 0) iter.setIndex(start + lengths[count[0] - 1])
            current = count[0] - 1
            mark = current
            return count[0]
        }
        fun acceptMarked(iter: CharacterIterator): Int {
            iter.setIndex(offset + lengths[mark])
            return lengths[mark]
        }
        fun backUp(iter: CharacterIterator): Boolean {
            if (current <= 0) return false
            iter.setIndex(offset + lengths[--current])
            return true
        }
        fun longestPrefix(): Int = prefix
        fun markCurrent() { mark = current }
    }
    /** Deliberately limited deque: ICU uses at most four front offers per reset. */
    class DequeI {
        private var data = IntArray(50)
        private var lastIdx = 4
        private var firstIdx = 4
        fun clone(): DequeI = DequeI().also {
            it.data = data.copyOf(); it.lastIdx = lastIdx; it.firstIdx = firstIdx
        }
        fun size(): Int = firstIdx - lastIdx
        fun isEmpty(): Boolean = size() == 0
        fun offer(value: Int) { data[--lastIdx] = value }
        fun push(value: Int) {
            if (firstIdx >= data.size) data = data.copyOf(data.size * 2)
            data[firstIdx++] = value
        }
        fun pop(): Int = data[--firstIdx]
        fun peek(): Int = data[firstIdx - 1]
        fun peekLast(): Int = data[lastIdx]
        fun pollLast(): Int = data[lastIdx++]
        fun contains(value: Int): Boolean {
            for (i in lastIdx until firstIdx) if (data[i] == value) return true
            return false
        }
        fun elementAt(index: Int): Int = data[lastIdx + index]
        fun removeAllElements() { lastIdx = 4; firstIdx = 4 }
    }
}
