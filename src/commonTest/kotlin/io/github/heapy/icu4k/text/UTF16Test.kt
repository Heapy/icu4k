package io.github.heapy.icu4k.text

import kotlin.test.*

class UTF16Test {
    // Kotlin/JS 2.4.21 replaces unpaired surrogate string literals in generated JS.
    // Construct code units at runtime so these tests exercise the intended input.
    private fun units(vararg values: Int): String = CharArray(values.size) { values[it].toChar() }.concatToString()
    private val text = units(0x61, 0xd83d, 0xde00, 0xd800, 0x62, 0xdc00)

    @Test fun accessesBothHalvesAndPreservesUnpairedSurrogates() {
        assertEquals(0x1f600, UTF16.charAt(text, 1))
        assertEquals(0x1f600, UTF16.charAt(text, 2))
        assertEquals(0xd800, UTF16.charAt(text, 3))
        assertEquals(0xdc00, UTF16.charAt(text, 5))
        assertEquals(UTF16.LEAD_SURROGATE_BOUNDARY, UTF16.bounds(text, 1))
        assertEquals(UTF16.TRAIL_SURROGATE_BOUNDARY, UTF16.bounds(text, 2))
        assertEquals(5, UTF16.countCodePoint(text))
        assertEquals(1, UTF16.findCodePointOffset(text, 2))
        assertEquals(3, UTF16.findOffsetFromCodePoint(text, 2))
        assertEquals(1, UTF16.moveCodePointOffset(text, 3, -1))
        assertEquals(3, UTF16.moveCodePointOffset(text, 2, 1))
    }

    @Test fun respectsArraySliceBoundaries() {
        val array = "\ud800\udc00".toCharArray()
        assertEquals(0x10000, UTF16.charAt(array, 0, 2, 1))
        assertEquals(0xdc00, UTF16.charAt(array, 1, 2, 0))
        assertEquals(0xd800, UTF16.charAt(array, 0, 1, 0))
        assertEquals(1, UTF16.countCodePoint(array, 1, 2))
        assertFailsWith<IndexOutOfBoundsException> { UTF16.charAt(array, 1, 2, 1) }
    }

    @Test fun editsWholePairsFromEitherHalf() {
        val target = StringBuilder("a\ud83d\ude00b")
        UTF16.setCharAt(target, 2, 0x10000)
        assertEquals("a\ud800\udc00b", target.toString())
        UTF16.insert(target, 2, 'x'.code)
        assertEquals("a\ud800\udc00xb", target.toString())
        UTF16.delete(target, 2)
        assertEquals("axb", target.toString())
        assertEquals(units(0xdc00, 0x62, 0xd800, 0xd83d, 0xde00, 0x61), UTF16.reverse(text).toString())
    }

    @Test fun searchesWholeCodePoints() {
        assertEquals(1, UTF16.indexOf(text, 0x1f600))
        assertEquals(-1, UTF16.indexOf(text, 0xd83d))
        assertEquals(3, UTF16.indexOf(text, 0xd800))
        assertEquals(5, UTF16.lastIndexOf(text, 0xdc00))
        assertEquals("axb" + units(0xdc00), UTF16.replace("a" + units(0xd800, 0xdc00) + "b" + units(0xdc00), 0x10000, 'x'.code))
    }

    @Test fun validatesCodePointsAndOffsets() {
        assertEquals("\udbff\udfff", UTF16.valueOf(0x10ffff))
        assertEquals(units(0xd800), UTF16.valueOf(0xd800))
        assertEquals(-1, UTF16.getSingleCodePoint("ab"))
        assertEquals(0x1f600, UTF16.getSingleCodePoint("\ud83d\ude00"))
        assertFailsWith<IllegalArgumentException> { UTF16.valueOf(0x110000) }
        assertFailsWith<IllegalArgumentException> { UTF16.valueOf(-1) }
        assertFailsWith<IndexOutOfBoundsException> { UTF16.moveCodePointOffset(text, 0, Int.MIN_VALUE) }
        assertFailsWith<IndexOutOfBoundsException> { UTF16.moveCodePointOffset(text, text.length, Int.MAX_VALUE) }
        assertEquals(0, UTF16.countCodePoint(null))
        assertTrue(UTF16.hasMoreCodePointsThan(null, -1))
        assertFalse(UTF16.hasMoreCodePointsThan(text, 5))
        assertTrue(UTF16.hasMoreCodePointsThan(text, 4))
    }
}
