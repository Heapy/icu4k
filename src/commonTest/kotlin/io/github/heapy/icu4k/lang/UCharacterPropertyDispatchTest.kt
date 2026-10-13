package io.github.heapy.icu4k.lang

import kotlin.test.*

class UCharacterPropertyDispatchTest {
    @Test fun publicRoutingAndStringProperties() {
        assertTrue(UCharacter.isUAlphabetic(65))
        assertTrue(UCharacter.isULowercase(97))
        assertTrue(UCharacter.isUUppercase(65))
        assertTrue(UCharacter.isUWhiteSpace(0xa0))
        assertTrue(UCharacter.hasBinaryProperty(0x1f600, UProperty.EMOJI))
        assertFalse(UCharacter.hasBinaryProperty(0x301, UProperty.SEGMENT_STARTER))
        assertEquals(230, UCharacter.getIntPropertyValue(0x301, UProperty.CANONICAL_COMBINING_CLASS))
        assertEquals(1, UCharacter.getIntPropertyValue(65, UProperty.ALPHABETIC))
        assertEquals(2, UCharacter.getIntPropertyValue(65, UProperty.GENERAL_CATEGORY_MASK))
        assertEquals(0, UCharacter.getIntPropertyValue(65, -1))
        assertFalse(UCharacter.hasBinaryProperty(65, UProperty.INT_START))
        assertTrue(UCharacter.hasBinaryProperty("👩‍💻", UProperty.RGI_EMOJI))
        assertTrue(UCharacter.hasBinaryProperty("😀", UProperty.EMOJI))
        assertFalse(UCharacter.hasBinaryProperty("AA", UProperty.ALPHABETIC))
        assertFalse(UCharacter.hasBinaryProperty("", UProperty.EMOJI))
    }
}
