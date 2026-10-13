package io.github.heapy.icu4k.util

import kotlin.test.*

class CodePointMapTest {
    private val map = object : CodePointMap() {
        override fun get(c: Int): Int = when {
            c !in 0..0x10ffff -> -1
            c < 0xd800 -> 1
            c < 0xe000 -> 2
            else -> 3
        }
        override fun getRange(start: Int, filter: ValueFilter?, range: Range): Boolean {
            if (start !in 0..0x10ffff) return false
            val value = filter?.apply(get(start)) ?: get(start)
            var end = start
            while (end < 0x10ffff && (filter?.apply(get(end + 1)) ?: get(end + 1)) == value) end++
            range.set(start, end, value)
            return true
        }
    }

    @Test fun iteratesAndReusesRange() {
        val iterator = map.iterator()
        val first = iterator.next()
        assertEquals(0, first.getStart())
        assertEquals(0xd7ff, first.getEnd())
        val second = iterator.next()
        assertSame(first, second)
        assertEquals(0xd800, second.getStart())
        // A caller can skip ahead by modifying the shared result.
        second.set(0xd800, 0x10ffff, 2)
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    @Test fun overridesSurrogatesWithoutFilteringTheOverride() {
        val range = CodePointMap.Range()
        assertTrue(map.getRange(0xd800, CodePointMap.RangeOption.FIXED_ALL_SURROGATES, 3, null, range))
        assertEquals(0xd800, range.getStart())
        assertEquals(0x10ffff, range.getEnd())
        assertEquals(3, range.getValue())
        assertTrue(map.getRange(0xdbff, CodePointMap.RangeOption.FIXED_LEAD_SURROGATES, 9, CodePointMap.ValueFilter { 0 }, range))
        assertEquals(0xdbff, range.getEnd())
        assertEquals(9, range.getValue())
    }

    @Test fun stringIteratorTreatsUnpairedSurrogatesIndividually() {
        val text = charArrayOf('a', 0xd800.toChar(), 0xdc00.toChar(), 0xd800.toChar()).concatToString()
        val iterator = map.stringIterator(text, 0)
        assertEquals(-1, iterator.getCodePoint())
        assertTrue(iterator.next())
        assertEquals(0x61, iterator.getCodePoint())
        assertTrue(iterator.next())
        assertEquals(0x10000, iterator.getCodePoint())
        assertEquals(3, iterator.getIndex())
        assertEquals(3, iterator.getValue())
        assertTrue(iterator.next())
        assertEquals(0xd800, iterator.getCodePoint())
        assertFalse(iterator.next())
        assertTrue(iterator.previous())
        assertEquals(0xd800, iterator.getCodePoint())
        iterator.reset(text, 2)
        assertTrue(iterator.next())
        assertEquals(0xdc00, iterator.getCodePoint())
    }
}
