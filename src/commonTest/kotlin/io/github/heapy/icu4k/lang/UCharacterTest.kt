package io.github.heapy.icu4k.lang

import kotlin.test.*

class UCharacterTest {
    @Test fun usesIcuCategoriesAndNumericRules() {
        assertEquals(UCharacter.UPPERCASE_LETTER.toInt(), UCharacter.getType('A'.code))
        assertTrue(UCharacter.isLetter(0x10400))
        assertTrue(UCharacter.isDigit(0x0665))
        assertEquals(10, UCharacter.digit('A'.code, 16))
        assertEquals(-1, UCharacter.digit('A'.code, 10))
        assertEquals(-1, UCharacter.digit('1'.code, 1))
        assertEquals(-1, UCharacter.digit('1'.code, 37))
        assertEquals(0.5, UCharacter.getUnicodeNumericValue(0x00bd))
        assertTrue(UCharacter.isUnicodeIdentifierStart(0x03b1))
        assertTrue(UCharacter.isUnicodeIdentifierPart(0x0301))
        assertFalse(UCharacter.isUnicodeIdentifierStart(0x0301))
        assertFalse(UCharacter.isWhitespace(0x00a0))
        assertTrue(UCharacter.isSpaceChar(0x00a0))
        assertEquals("17.0.0.0", UCharacter.getUnicodeVersion().toString())
    }

    @Test fun preservesLegalitySurrogatesAndNullableConversion() {
        assertFalse(UCharacter.isLegal(0xd800))
        assertFalse(UCharacter.isLegal(0xfdd0))
        assertFalse(UCharacter.isLegal(0x1ffff))
        assertTrue(UCharacter.isLegal(0x1f600))
        assertTrue(UCharacter.isLegal("\ud83d\ude00"))
        assertFalse(UCharacter.isLegal(charArrayOf(0xd800.toChar()).concatToString()))
        assertNull(UCharacter.toString(-1))
        assertNull(UCharacter.toString(0x110000))
        assertEquals(0xd800, UCharacter.toString(0xd800)!![0].code)
        assertFailsWith<IllegalArgumentException> { UCharacter.getAge(-1) }
        assertEquals(')'.code, UCharacter.getBidiPairedBracket('('.code))
    }
}
