package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.UCharacter as JavaCharacter
import kotlin.test.*

class UCharacterAliasesOracleTest {
    @Test fun publicNameAdaptersMatchIncludingUnnamedCombiningClasses() {
        for (property in listOf(0, UProperty.GENERAL_CATEGORY, UProperty.SCRIPT, UProperty.CANONICAL_COMBINING_CLASS, UProperty.LEAD_CANONICAL_COMBINING_CLASS, UProperty.TRAIL_CANONICAL_COMBINING_CLASS, -1)) {
            for (value in -1..256) for (choice in -1..2) {
                val expected = runCatching { JavaCharacter.getPropertyValueName(property, value, choice) }
                val actual = runCatching { UCharacter.getPropertyValueName(property, value, choice) }
                assertEquals(expected.getOrNull(), actual.getOrNull())
                assertEquals(expected.isSuccess, actual.isSuccess)
                if (expected.isFailure) assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message)
            }
            for (alias in listOf("Lu", "Uppercase-Letter", "Latin", "Yes", "invalid", "")) {
                val expected = runCatching { JavaCharacter.getPropertyValueEnum(property, alias) }
                val actual = runCatching { UCharacter.getPropertyValueEnum(property, alias) }
                assertEquals(expected.getOrNull(), actual.getOrNull())
                assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message)
                assertEquals(JavaCharacter.getPropertyValueEnumNoThrow(property, alias), UCharacter.getPropertyValueEnumNoThrow(property, alias))
            }
        }
        for (alias in listOf("gc", "GENERAL-CATEGORY", "sc", "invalid", "")) {
            val expected = runCatching { JavaCharacter.getPropertyEnum(alias) }
            val actual = runCatching { UCharacter.getPropertyEnum(alias) }
            assertEquals(expected.getOrNull(), actual.getOrNull())
            assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message)
        }
    }
}
