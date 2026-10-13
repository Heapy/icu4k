package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetRankTest {
    @Test fun ranksSkipGapsAndIgnoreStringElements() {
        val set = UnicodeSet(65, 67).add(0x1f600).add("abc").add("").freeze()
        assertEquals(3, set.indexOf(0x1f600))
        assertEquals(-1, set.indexOf(68))
        assertEquals(65, set.charAt(0))
        assertEquals(0x1f600, set.charAt(3))
        assertEquals(-1, set.charAt(4))
        assertEquals(-1, set.charAt(-1))
        assertEquals(-1, UnicodeSet().charAt(0))
        assertEquals(0x10ffff, UnicodeSet(0, 0x10ffff).charAt(0x10ffff))
    }
}
