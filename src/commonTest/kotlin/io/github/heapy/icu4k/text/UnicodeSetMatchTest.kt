package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetMatchTest {
    @Test fun longestMatchAndPartialMatchDoNotConfuseOffsets() {
        val set = UnicodeSet().add("ab").add("abc").add(0xffff)
        val offset = intArrayOf(0)
        assertEquals(UnicodeMatcher.U_MATCH, set.matches(ReplaceableString("abcd"), offset, 4, false))
        assertEquals(3, offset[0])
        offset[0] = 0
        assertEquals(UnicodeMatcher.U_PARTIAL_MATCH, set.matches(ReplaceableString("ab"), offset, 2, true))
        assertEquals(0, offset[0])
        assertEquals(UnicodeMatcher.U_MATCH, set.matches(ReplaceableString(""), offset, 0, false))
        assertTrue(set.matchesIndexValue('a'.code))
        val union = UnicodeSet()
        set.addMatchSetTo(union)
        assertEquals(set, union)
    }
}
