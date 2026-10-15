// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

internal class SearchProtocolFixture(target: CharacterIterator?, breaker: BreakIterator? = null) : SearchIterator(target, breaker) {
    val calls = mutableListOf<Pair<String, Int>>()
    override fun getIndex(): Int = targetText!!.getIndex()
    override fun setIndex(position: Int) { super.setIndex(position); targetText!!.setIndex(position) }
    private fun match(index: Int): Int {
        search_.matchedIndex_ = index
        setMatchLength(if (index == DONE) 0 else 2)
        targetText!!.setIndex(if (index == DONE) (if (search_.isForwardSearching_) search_.endIndex() else search_.beginIndex()) else index)
        return index
    }
    private fun accepts(index: Int): Boolean = targetText!!.setIndex(index) == 'a' && targetText!!.next() == 'a'
    override fun handleNext(start: Int): Int {
        calls.add("next" to start)
        for (i in start..search_.endIndex()-2) if (accepts(i)) return match(i)
        return match(DONE)
    }
    override fun handlePrevious(startAt: Int): Int {
        calls.add("previous" to startAt)
        for (i in startAt-2 downTo search_.beginIndex()) if (accepts(i)) return match(i)
        return match(DONE)
    }
}

class SearchIteratorTest {
    @Test fun directionChangesOverlapAndReset() {
        val s = SearchProtocolFixture(StringCharacterIterator("aaa aa"))
        assertEquals(0, s.next()); assertEquals("aa", s.getMatchedText())
        assertEquals(0, s.previous()) // direction change repeats the match
        assertEquals(0, s.next())
        assertEquals(4, s.next()); assertEquals(-1, s.next())
        s.reset(); s.setOverlapping(true)
        assertEquals(0, s.next()); assertEquals(1, s.next()); assertEquals(4, s.next())
        s.setElementComparisonType(SearchIterator.ElementComparisonType.ANY_BASE_WEIGHT_IS_WILDCARD)
        s.reset()
        assertFalse(s.isOverlapping())
        assertEquals(SearchIterator.ElementComparisonType.STANDARD_ELEMENT_COMPARISON, s.getElementComparisonType())
        assertEquals(4, s.previous()); assertEquals("aa", s.getMatchedText())
    }
    @Test fun boundaryCursorClonesAndPartialFailure() {
        val text = StringCharacterIterator("aaaa", 2)
        val breaker = BreakIterator.getCharacterInstance()
        val s = SearchProtocolFixture(text, breaker)
        assertNotSame(text, breaker.getText())
        breaker.first()
        assertEquals(2, text.getIndex())
        s.first()
        val failing = object : CharacterIterator by StringCharacterIterator("aaaa") {
            override fun clone(): CharacterIterator = throw IllegalStateException("clone failed")
        }
        assertFailsWith<IllegalStateException> { s.setTarget(failing) }
        // Target replacement and match reset precede the boundary iterator clone.
        assertSame(failing, s.getTarget())
        assertEquals(SearchIterator.DONE, s.getMatchStart())
        assertEquals(0, s.getMatchLength())
        s.setBreakIterator(null)
        assertNull(s.getBreakIterator())
        val replacement = BreakIterator.getCharacterInstance()
        assertFailsWith<IllegalStateException> { s.setBreakIterator(replacement) }
        assertSame(replacement, s.getBreakIterator())
    }
    @Test fun absoluteRangesTargetAliasingAndValidation() {
        val text = StringCharacterIterator("!aaaa?", 1, 5, 3)
        val breaker = BreakIterator.getCharacterInstance()
        val s = SearchProtocolFixture(text, breaker)
        assertSame(text, s.getTarget()); assertSame(breaker, s.getBreakIterator())
        assertEquals(3, text.getIndex()) // constructor does not move the supplied cursor
        assertEquals(1, s.first()); assertEquals(1, text.getIndex())
        assertEquals("aa", s.getMatchedText()); assertEquals(1, text.getIndex())
        assertFailsWith<IndexOutOfBoundsException> { s.following(0) }
        val atEnd = StringCharacterIterator("abc", 3)
        assertFailsWith<IllegalArgumentException> { s.setTarget(atEnd) }
        assertSame(text, s.getTarget())
        val replacement = StringCharacterIterator("aaa", 1)
        s.setTarget(replacement)
        assertSame(replacement, s.getTarget()); assertEquals(0, replacement.getIndex())
        assertEquals(-1, s.getMatchStart()); assertNull(s.getMatchedText())
        s.setElementComparisonType(null); assertNull(s.getElementComparisonType())
        assertFailsWith<IllegalArgumentException> { SearchProtocolFixture(null) }
        assertFailsWith<IllegalArgumentException> { SearchProtocolFixture(StringCharacterIterator("")) }
    }
}
