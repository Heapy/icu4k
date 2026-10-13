package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetCaseClosureTest {
    @Test fun fullAndSimpleClosureOfCodePointsAndStrings() {
        val full = UnicodeSet().add('s'.code).add(0xdf).add("Bc")
        full.closeOverCaseInsensitive(false)
        for (c in listOf('s'.code, 'S'.code, 0x17f, 0xdf, 0x1e9e)) assertTrue(full.contains(c))
        assertTrue(full.contains("ss")); assertTrue(full.contains("bc")); assertFalse(full.contains("Bc"))
        val simple = UnicodeSet().add(0xdf).add("Bc")
        simple.closeOverCaseInsensitive(true)
        assertTrue(simple.contains(0xdf)); assertTrue(simple.contains(0x1e9e))
        assertTrue(simple.contains("bc")); assertFalse(simple.contains("ss"))
        val buffer = StringBuilder("untouched")
        assertFalse(UnicodeSet.scfString("abc", buffer)); assertEquals("untouched", buffer.toString())
        assertTrue(UnicodeSet.scfString("A😀B", buffer)); assertEquals("a😀b", buffer.toString())
        simple.freeze()
        assertFailsWith<UnsupportedOperationException> { simple.closeOverCaseInsensitive(true) }
    }
}
