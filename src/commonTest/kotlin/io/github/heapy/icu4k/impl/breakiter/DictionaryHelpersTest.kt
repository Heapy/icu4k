package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class DictionaryHelpersTest {
    @Test fun choosesMarkedWordAndCachesCandidatesAtSameOffset() {
        var calls = 0
        val matcher = object : DictionaryMatcher() {
            override fun getType() = 0
            override fun matches(text: CharacterIterator, maxLength: Int, lengths: IntArray, count: IntArray, limit: Int, values: IntArray?): Int {
                ++calls
                assertEquals(20, limit)
                assertNull(values)
                if (text.getIndex() == 0) {
                    lengths[0] = 1; lengths[1] = 3; lengths[2] = 5; count[0] = 3
                    text.setIndex(6)
                    return 6
                }
                count[0] = 0
                text.setIndex(text.getIndex() + 1)
                return 1
            }
        }
        val iter = StringCharacterIterator("abcdefghi")
        val word = DictionaryBreakEngine.PossibleWord()
        assertEquals(3, word.candidates(iter, matcher, 9))
        assertEquals(5, iter.getIndex())
        assertEquals(6, word.longestPrefix())
        assertTrue(word.backUp(iter)); assertEquals(3, iter.getIndex())
        word.markCurrent()
        assertTrue(word.backUp(iter)); assertEquals(1, iter.getIndex())
        assertFalse(word.backUp(iter))
        assertEquals(3, word.acceptMarked(iter)); assertEquals(3, iter.getIndex())
        iter.setIndex(0)
        assertEquals(3, word.candidates(iter, matcher, 9)); assertEquals(1, calls)
        iter.setIndex(7)
        assertEquals(0, word.candidates(iter, matcher, 9))
        assertEquals(7, iter.getIndex())
        assertEquals(1, word.longestPrefix())
    }
    @Test fun dequeGrowsAndClonesIndependentStorage() {
        val deque = DictionaryBreakEngine.DequeI()
        repeat(200) { deque.push(it) }
        deque.offer(-1)
        assertEquals(-1, deque.peekLast())
        val copy = deque.clone()
        assertEquals(-1, deque.pollLast())
        assertEquals(199, deque.pop())
        assertEquals(201, copy.size())
        assertEquals(-1, copy.elementAt(0))
        assertTrue(copy.contains(199))
        assertEquals(199, copy.peek())
        deque.removeAllElements()
        assertTrue(deque.isEmpty())
        assertFalse(copy.isEmpty())
    }
}
