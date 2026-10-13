package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetAlgebraTest {
    @Test fun intervalOperationsAndStringRetention() {
        val set = UnicodeSet(0, 10).add("ab")
        set.remove(3, 7)
        assertEquals(2, set.getRangeCount()); assertEquals(2, set.getRangeEnd(0)); assertEquals(8, set.getRangeStart(1))
        set.retain(1, 9)
        assertTrue(set.contains("ab")); assertFalse(set.contains(0)); assertFalse(set.contains(10))
        set.complement()
        assertTrue(set.contains("ab")); assertTrue(set.contains(0)); assertFalse(set.contains(1))
        set.retain("ab")
        assertEquals(1, set.size()); assertTrue(set.contains("ab"))
        set.complement("ab")
        assertTrue(set.isEmpty())
    }
    @Test fun wholeSetOperationsAndSelfAliases() {
        val a = UnicodeSet(0, 4).add("ab").add("xy")
        val b = UnicodeSet(3, 7).add("ab").add("")
        assertEquals(UnicodeSet(3, 4).add("ab"), UnicodeSet(a).retainAll(b))
        assertEquals(UnicodeSet(0, 2).add("xy"), UnicodeSet(a).removeAll(b))
        assertEquals(UnicodeSet(0, 2, 5, 7).add("xy").add(""), UnicodeSet(a).complementAll(b))
        assertEquals(a, UnicodeSet(a).retainAll(a))
        val self = UnicodeSet(a)
        self.complementAll(self)
        assertTrue(self.isEmpty())
        self.set(a).removeAll(self)
        assertTrue(self.isEmpty())
        assertTrue(UnicodeSet(a).removeAllStrings().contains(0, 4))
    }
}
