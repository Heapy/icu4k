// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.jvm.JvmField

/** Stateful base protocol for forward/backward text search, with optional boundaries. */
abstract class SearchIterator protected constructor(target: CharacterIterator?, breaker: BreakIterator?) {
    @JvmField protected var breakIterator: BreakIterator? = null
    @JvmField protected var targetText: CharacterIterator? = null
    @JvmField protected var matchLength: Int = 0

    internal inner class Search {
        fun text(): CharacterIterator? = targetText
        fun setTarget(text: CharacterIterator?) { targetText = text }
        var isOverlap_ = false
        var isCanonicalMatch_ = false
        var elementComparisonType_: ElementComparisonType? = null
        var internalBreakIter_: BreakIterator? = null
        fun breakIter(): BreakIterator? = breakIterator
        fun setBreakIter(value: BreakIterator?) { breakIterator = value }
        var matchedIndex_ = 0
        fun matchedLength(): Int = matchLength
        fun setMatchedLength(value: Int) { matchLength = value }
        var isForwardSearching_ = false
        var reset_ = false
        fun beginIndex(): Int = targetText?.getBeginIndex() ?: 0
        fun endIndex(): Int = targetText?.getEndIndex() ?: 0
    }
    internal val search_ = Search()

    init {
        if (target == null || target.getEndIndex() - target.getBeginIndex() == 0) {
            throw IllegalArgumentException("Illegal argument target.  Argument can not be null or of length 0")
        }
        search_.setTarget(target)
        search_.setBreakIter(breaker)
        search_.breakIter()?.setText(target.clone())
        search_.elementComparisonType_ = ElementComparisonType.STANDARD_ELEMENT_COMPARISON
        search_.isForwardSearching_ = true
        search_.reset_ = true
        search_.matchedIndex_ = DONE
        search_.setMatchedLength(0)
    }

    open fun setIndex(position: Int) {
        if (position < search_.beginIndex() || position > search_.endIndex()) {
            throw IndexOutOfBoundsException("setIndex(int) expected position to be between ${search_.beginIndex()} and ${search_.endIndex()}")
        }
        search_.reset_ = false
        search_.setMatchedLength(0)
        search_.matchedIndex_ = DONE
    }
    open fun setOverlapping(allowOverlap: Boolean) { search_.isOverlap_ = allowOverlap }
    open fun setBreakIterator(breakiter: BreakIterator?) {
        search_.setBreakIter(breakiter)
        if (search_.breakIter() != null && search_.text() != null) {
            search_.breakIter()!!.setText(search_.text()!!.clone())
        }
    }
    open fun setTarget(text: CharacterIterator?) {
        // ICU checks the current position, rather than the begin index, here.
        if (text == null || text.getEndIndex() == text.getIndex()) {
            throw IllegalArgumentException("Illegal null or empty text")
        }
        text.setIndex(text.getBeginIndex())
        search_.setTarget(text)
        search_.matchedIndex_ = DONE
        search_.setMatchedLength(0)
        search_.reset_ = true
        search_.isForwardSearching_ = true
        search_.breakIter()?.setText(text.clone())
        search_.internalBreakIter_?.setText(text.clone())
    }
    open fun getMatchStart(): Int = search_.matchedIndex_
    abstract fun getIndex(): Int
    open fun getMatchLength(): Int = search_.matchedLength()
    open fun getBreakIterator(): BreakIterator? = search_.breakIter()
    open fun getTarget(): CharacterIterator? = search_.text()
    open fun getMatchedText(): String? {
        if (search_.matchedLength() <= 0) return null
        val limit = search_.matchedIndex_ + search_.matchedLength()
        val result = StringBuilder(search_.matchedLength())
        val it = search_.text()!!
        it.setIndex(search_.matchedIndex_)
        while (it.getIndex() < limit) { result.append(it.current()); it.next() }
        it.setIndex(search_.matchedIndex_)
        return result.toString()
    }
    open fun next(): Int {
        var index = getIndex()
        val matchindex = search_.matchedIndex_
        val matchlength = search_.matchedLength()
        search_.reset_ = false
        if (search_.isForwardSearching_) {
            val end = search_.endIndex()
            if (index == end || matchindex == end || (matchindex != DONE && matchindex + matchlength >= end)) {
                setMatchNotFound(); return DONE
            }
        } else {
            search_.isForwardSearching_ = true
            if (search_.matchedIndex_ != DONE) return matchindex
        }
        if (matchlength > 0) index += if (search_.isOverlap_) 1 else matchlength
        return handleNext(index)
    }
    open fun previous(): Int {
        val index: Int
        if (search_.reset_) {
            index = search_.endIndex()
            search_.isForwardSearching_ = false
            search_.reset_ = false
            setIndex(index)
        } else index = getIndex()
        var matchindex = search_.matchedIndex_
        if (search_.isForwardSearching_) {
            search_.isForwardSearching_ = false
            if (matchindex != DONE) return matchindex
        } else {
            val start = search_.beginIndex()
            if (index == start || matchindex == start) { setMatchNotFound(); return DONE }
        }
        if (matchindex != DONE) {
            if (search_.isOverlap_) matchindex += search_.matchedLength() - 2
            return handlePrevious(matchindex)
        }
        return handlePrevious(index)
    }
    open fun isOverlapping(): Boolean = search_.isOverlap_
    open fun reset() {
        setMatchNotFound()
        setIndex(search_.beginIndex())
        search_.isOverlap_ = false
        search_.isCanonicalMatch_ = false
        search_.elementComparisonType_ = ElementComparisonType.STANDARD_ELEMENT_COMPARISON
        search_.isForwardSearching_ = true
        search_.reset_ = true
    }
    fun first(): Int { val start = search_.beginIndex(); setIndex(start); return handleNext(start) }
    fun following(position: Int): Int { setIndex(position); return handleNext(position) }
    fun last(): Int { val end = search_.endIndex(); setIndex(end); return handlePrevious(end) }
    fun preceding(position: Int): Int { setIndex(position); return handlePrevious(position) }
    protected open fun setMatchLength(length: Int) { search_.setMatchedLength(length) }
    protected abstract fun handleNext(start: Int): Int
    protected abstract fun handlePrevious(startAt: Int): Int
    protected open fun setMatchNotFound() { search_.matchedIndex_ = DONE; search_.setMatchedLength(0) }
    enum class ElementComparisonType {
        STANDARD_ELEMENT_COMPARISON,
        PATTERN_BASE_WEIGHT_IS_WILDCARD,
        ANY_BASE_WEIGHT_IS_WILDCARD,
    }
    open fun setElementComparisonType(type: ElementComparisonType?) { search_.elementComparisonType_ = type }
    open fun getElementComparisonType(): ElementComparisonType? = search_.elementComparisonType_
    companion object { const val DONE = -1 }
}
