package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetRelationsTest {
    @Test fun distinguishesMembershipFromStringSegmentation() {
        val set = UnicodeSet().add('a'.code).add("bc").add("cd")
        assertTrue(set.containsAll("bcd") == false)
        assertTrue(set.containsAll("abc"))
        assertFalse(set.containsAll("acb"))
        assertTrue(set.containsNone("cb"))
        assertTrue(set.containsSome("xybc"))
        assertTrue(set.containsAll(UnicodeSet().add("bc")))
        assertFalse(set.containsAll(UnicodeSet().add('b'.code)))
        assertTrue(set.containsNone(UnicodeSet().add('b'.code)))
        assertFalse(set.containsNone(UnicodeSet().add("bc")))
        assertTrue(set.containsNone(0x10000, 0x10ffff))
        assertTrue(UnicodeSet().containsAll(""))
        assertTrue(UnicodeSet().containsNone(""))
    }
}
