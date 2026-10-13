package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.StringCharacterIterator
import io.github.heapy.icu4k.text.UCharacterIterator
import kotlin.test.*

class BytesDictionaryMatcherTest {
    @Test fun readsCandidatesAndValues() {
        val matcher = BytesDictionaryMatcher(byteArrayOf(16, 1, 34, 16, 2, 37), 0x1000e00)
        val text = StringCharacterIterator("\u0e01\u0e02")
        val lengths = IntArray(3)
        val values = IntArray(3)
        val count = intArrayOf(91)
        assertEquals(2, matcher.matches(text, 10, lengths, count, 3, values))
        assertEquals(2, count[0])
        assertContentEquals(intArrayOf(1, 2, 0), lengths)
        assertContentEquals(intArrayOf(1, 2, 0), values)
        assertEquals(2, text.getIndex())
        assertEquals(0, matcher.matches(text, 10, lengths, count, 3, values))
        assertEquals(2, count[0]) // ICU leaves count untouched at end of text.
    }
    @Test fun adaptersShareOriginalCursorAndCloneIndependently() {
        val cursor = StringCharacterIterator("abc")
        val wrapped = UCharacterIterator.getInstance(cursor)
        assertEquals(97, wrapped.next())
        assertEquals(1, cursor.getIndex())
        val copy = wrapped.getCharacterIterator()
        copy.next()
        assertEquals(1, cursor.getIndex())
        assertEquals(99, wrapped.moveIndex(1)) // Original adapter returns the code unit, not the index.
        assertEquals(2, cursor.getIndex())
        val reverse = UCharacterIterator.getInstance("abc").getCharacterIterator()
        assertEquals('b', reverse.next())
        val clone = reverse.clone()
        clone.last()
        assertEquals(1, reverse.getIndex())
    }
}
