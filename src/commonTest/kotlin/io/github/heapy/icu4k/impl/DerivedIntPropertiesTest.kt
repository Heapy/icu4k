package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class DerivedIntPropertiesTest {
    @Test fun hangulTypesExcludeSupplementaryGcbVowels() {
        val props = UCharacterProperty.INSTANCE
        assertEquals(UCharacter.HangulSyllableType.LEADING_JAMO, props.getDerivedIntPropertyValue(0x1100, UProperty.HANGUL_SYLLABLE_TYPE))
        assertEquals(UCharacter.HangulSyllableType.LV_SYLLABLE, props.getDerivedIntPropertyValue(0xac00, UProperty.HANGUL_SYLLABLE_TYPE))
        assertEquals(UCharacter.HangulSyllableType.LVT_SYLLABLE, props.getDerivedIntPropertyValue(0xac01, UProperty.HANGUL_SYLLABLE_TYPE))
        assertEquals(0, props.getDerivedIntPropertyValue(0x16d63, UProperty.HANGUL_SYLLABLE_TYPE))
        assertEquals(UCharacter.NumericType.DECIMAL, props.getDerivedIntPropertyValue('5'.code, UProperty.NUMERIC_TYPE))
        assertEquals(1, props.getDerivedIntPropertyValue('A'.code, UProperty.IDENTIFIER_STATUS))
    }
}
