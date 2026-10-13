package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ICUException
import kotlin.test.*

class StringRangeTest {
    @Test fun rectangularRangesAndBothCompactors() {
        val strings = linkedSetOf<String>()
        assertSame(strings, StringRange.expand("ab", "cd", true, strings))
        assertEquals(listOf("ab", "ac", "ad", "bb", "bc", "bd", "cb", "cc", "cd"), strings.toList())
        val ranges = ArrayList<Pair<String?, String?>>()
        StringRange.compact(strings, { a, b -> ranges.add(a to b) }, false, true)
        assertEquals(listOf<Pair<String?, String?>>("ab" to "cd"), ranges)
        ranges.clear()
        StringRange.compact(linkedSetOf("abc", "abd", "abe"), { a, b -> ranges.add(a to b) }, true)
        assertEquals(listOf<Pair<String?, String?>>("abc" to "e"), ranges)
        assertEquals(listOf("a😀", "a😁"), StringRange.expand("a😀", "😁", false, arrayListOf()))
        assertFailsWith<ICUException> { StringRange.expand("b", "a", true, arrayListOf()) }
    }
}
