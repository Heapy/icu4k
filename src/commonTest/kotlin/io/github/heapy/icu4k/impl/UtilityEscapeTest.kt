package io.github.heapy.icu4k.impl

import kotlin.test.*

class UtilityEscapeTest {
    @Test fun numericEscapesAndSurrogateJoining() {
        assertEquals("A😀!", Utility.unescape("\\u0041\\uD83D\\uDE00\\x{21}"))
        assertEquals("\n\tA", Utility.unescape("\\n\\t\\101"))
        assertEquals("\\x{}", Utility.unescapeLeniently("\\x{}"))
        assertFailsWith<IllegalArgumentException> { Utility.unescape("\\U00110000") }
        val decoded = Utility.unescapeAndLengthAt("uD83D\\uDE00", 0)
        assertEquals(0x1f600, Utility.cpFromCodePointAndLength(decoded))
        assertEquals(11, Utility.lengthFromCodePointAndLength(decoded))
        assertEquals(-1, Utility.unescapeAndLengthAt("", 0))
    }
}
