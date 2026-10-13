package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetCollectionTest {
    @Test fun overloadsDistinguishCodePointsAndWholeStrings() {
        assertFalse(UnicodeSet.from("ab").contains('a'.code))
        assertTrue(UnicodeSet.fromAll("ab😀").contains(0x1f600))
        val strings = UnicodeSet().addAll(listOf("ab", "cd", ""))
        assertTrue(strings.containsAll(listOf("ab", "")))
        assertTrue(strings.containsNone(listOf("a", "c")))
        assertTrue(strings.containsSome(listOf("a", "cd")))
        assertFalse(strings.containsAll("abcdx"))
        assertTrue(strings.containsAll("abcd"))
        assertEquals(UnicodeSet.from("ab"), strings.cloneAsThawed().retainAll(listOf("ab")))
        assertEquals(UnicodeSet().addAll("cd", ""), strings.cloneAsThawed().removeAll(listOf("ab")))
        assertTrue(UnicodeSet().addAll(listOf(123)).contains("123"))
        assertEquals(UnicodeSet.fromAll("a"), UnicodeSet.fromAll("aba").complementAll("b"))
        assertFailsWith<UnsupportedOperationException> { strings.freeze().addAll(emptyList<String>()) }
    }
}
