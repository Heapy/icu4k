package io.github.heapy.icu4k.lang

import kotlin.test.*

class IdentifierTypesTest {
    @Test fun identifierTypesClearAndFillCallerSet() {
        val types = mutableSetOf(UCharacter.IdentifierType.DEPRECATED)
        assertEquals(1, UCharacter.getIdentifierTypes('A'.code, types))
        assertEquals(setOf(UCharacter.IdentifierType.RECOMMENDED), types)
        assertTrue(UCharacter.hasIdentifierType('A'.code, UCharacter.IdentifierType.RECOMMENDED))
        assertFalse(UCharacter.hasIdentifierType('A'.code, UCharacter.IdentifierType.NOT_XID))
        UCharacter.getIdentifierTypes(0x200b, types)
        assertEquals(setOf(UCharacter.IdentifierType.DEFAULT_IGNORABLE), types)
        assertEquals(1, UCharacter.getIdentifierTypes(0x10ffff, types))
        assertEquals(setOf(UCharacter.IdentifierType.NOT_CHARACTER), types)
    }
}
