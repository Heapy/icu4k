package io.github.heapy.icu4k.impl

import com.ibm.icu.lang.UCharacter as JavaCharacter
import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class DerivedIntPropertiesOracleTest {
    @Test fun derivedValuesMatchForEveryUnicodeCodePoint() {
        val properties = listOf(UProperty.GENERAL_CATEGORY, UProperty.NUMERIC_TYPE, UProperty.HANGUL_SYLLABLE_TYPE, UProperty.IDENTIFIER_STATUS)
        for (which in properties) {
            assertEquals(JavaCharacter.getIntPropertyMaxValue(which), UCharacterProperty.INSTANCE.getDerivedIntPropertyMaxValue(which))
            for (cp in -1..0x110000) assertEquals(JavaCharacter.getIntPropertyValue(cp, which), UCharacterProperty.INSTANCE.getDerivedIntPropertyValue(cp, which), "$which $cp")
        }
    }
}
