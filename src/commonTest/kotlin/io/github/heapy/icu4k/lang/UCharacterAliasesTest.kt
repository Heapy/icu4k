package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.impl.IllegalIcuArgumentException
import kotlin.test.*

class UCharacterAliasesTest {
    @Test fun exposesAliasesAndUnnamedCombiningClassValues() {
        assertEquals(UProperty.GENERAL_CATEGORY, UCharacter.getPropertyEnum("General Category"))
        assertEquals("General_Category", UCharacter.getPropertyName(UProperty.GENERAL_CATEGORY, 1))
        assertEquals(1, UCharacter.getPropertyValueEnum(UProperty.GENERAL_CATEGORY, "Lu"))
        assertNull(UCharacter.getPropertyValueName(UProperty.CANONICAL_COMBINING_CLASS, 254, 1))
        assertEquals(-1, UCharacter.getPropertyValueEnumNoThrow(-1, "x"))
        assertFailsWith<IllegalIcuArgumentException> { UCharacter.getPropertyEnum("invalid") }
    }
}
