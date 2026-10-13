package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class UPropertyAliasesTest {
    @Test fun resolvesPropertyAndValueAliasesWithoutPlatformData() {
        val aliases = UPropertyAliases.INSTANCE
        assertEquals(UProperty.GENERAL_CATEGORY, aliases.getPropertyEnum(" General-Category "))
        assertEquals("gc", aliases.getPropertyName(UProperty.GENERAL_CATEGORY, 0))
        assertEquals("Uppercase_Letter", aliases.getPropertyValueName(UProperty.GENERAL_CATEGORY, 1, 1))
        assertEquals(1, aliases.getPropertyValueEnum(UProperty.GENERAL_CATEGORY, "upperCASE letter"))
        assertEquals(-1, aliases.getPropertyEnum("unknown"))
        assertEquals(-1, aliases.getPropertyValueEnumNoThrow(-1, "unknown"))
        assertEquals(0, UPropertyAliases.compare(" General-Category ", "general_category"))
        assertFailsWith<IllegalIcuArgumentException> { aliases.getPropertyName(UProperty.GENERAL_CATEGORY, 99) }
    }
}
