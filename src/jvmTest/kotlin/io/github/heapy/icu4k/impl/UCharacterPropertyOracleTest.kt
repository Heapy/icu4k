package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UCharacterProperty as JavaProps
import com.ibm.icu.lang.UCharacter
import kotlin.test.*

class UCharacterPropertyOracleTest {
    @Test fun allUnicodePropertyWordsAgesAndNumericValuesMatchIcu() {
        val expected = JavaProps.INSTANCE
        val actual = UCharacterProperty.INSTANCE
        for (cp in -1..0x110000) {
            if (expected.getProperty(cp) != actual.getProperty(cp)) fail("Raw property U+${cp.toString(16)}")
            if (expected.getType(cp) != actual.getType(cp)) fail("Category U+${cp.toString(16)}")
            if (expected.digit(cp) != actual.digit(cp)) fail("Digit U+${cp.toString(16)}")
            if (expected.getNumericValue(cp) != actual.getNumericValue(cp)) fail("Numeric value U+${cp.toString(16)}")
            if (expected.getUnicodeNumericValue(cp).toBits() != actual.getUnicodeNumericValue(cp).toBits()) fail("Unicode numeric value U+${cp.toString(16)}: ${expected.getUnicodeNumericValue(cp)} vs ${actual.getUnicodeNumericValue(cp)}")
            for (column in 0..3) {
                if (expected.getAdditional(cp, column) != actual.getAdditional(cp, column)) fail("Additional column $column U+${cp.toString(16)}")
            }
            if (UCharacterProperty.getEuropeanDigit(cp) != JavaProps.getEuropeanDigit(cp)) fail("European digit U+${cp.toString(16)}")
            val age = expected.getAge(cp)
            val actualAge = actual.getAge(cp)
            if (age.toString() != actualAge.toString()) fail("Age U+${cp.toString(16)}")
        }
        for (column in 0..4) assertEquals(expected.getMaxValues(column), actual.getMaxValues(column))
        assertContentEquals(expected.m_scriptExtensions_, actual.m_scriptExtensions_)
        assertEquals(UCharacter.getUnicodeVersion().toString(), actual.m_unicodeVersion_.toString())
    }
}
