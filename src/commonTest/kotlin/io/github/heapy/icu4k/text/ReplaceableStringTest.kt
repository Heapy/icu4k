package io.github.heapy.icu4k.text

import kotlin.test.*

class ReplaceableStringTest {
    @Test fun sharesBuilderAndCopiesText() {
        val builder = StringBuilder("a\ud83d\ude00b")
        val text = ReplaceableString(builder)
        assertEquals(0x1f600, text.char32At(2))
        assertEquals(0x1f600, UTF16.charAt(text, 2))
        text.copy(1, 3, 4)
        assertEquals("a\ud83d\ude00b\ud83d\ude00", builder.toString())
        text.replace(0, 1, charArrayOf('x', 'y', 'z'), 1, 2)
        assertEquals("yz\ud83d\ude00b\ud83d\ude00", text.toString())
        builder.append('!')
        assertEquals('!', text.charAt(text.length() - 1))
        assertFalse(text.hasMetaData())
    }

    @Test fun extractsAndReplacesRanges() {
        val text = ReplaceableString("abcd")
        val chars = CharArray(4) { '_' }
        text.getChars(1, 3, chars, 1)
        assertContentEquals(charArrayOf('_', 'b', 'c', '_'), chars)
        text.replace(2, 100, "!")
        assertEquals("ab!", text.toString())
        assertEquals("b!", text.substring(1, 3))
        assertFailsWith<IndexOutOfBoundsException> { text.getChars(0, 4, chars, 0) }
    }
}
