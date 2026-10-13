package io.github.heapy.icu4k.impl

import kotlin.test.*

class UCharacterPropertyTest {
    @Test fun loadsUnicodePropertiesAndNumericEncodings() {
        val props = UCharacterProperty.INSTANCE
        assertEquals("17.0.0.0", props.m_unicodeVersion_.toString())
        assertEquals(1, props.getType('A'.code))
        assertEquals(27, props.getType(0x1f600))
        assertEquals(5, props.digit(0x0665))
        assertEquals(10, props.getNumericValue('A'.code))
        assertEquals(35, props.getNumericValue(0xff5a))
        assertEquals(-2, props.getNumericValue(0x00bd))
        assertEquals(0.5, props.getUnicodeNumericValue(0x00bd))
        assertEquals(10000.0, props.getUnicodeNumericValue(0x4e07))
        assertEquals(UCharacterProperty.NO_NUMERIC_VALUE, props.getUnicodeNumericValue('A'.code))
        assertEquals("1.1.0.0", props.getAge('A'.code).toString())
        assertEquals("6.1.0.0", props.getAge(0x1f600).toString())
        assertEquals(0, props.getAdditional(0x41, 100))
    }
}
