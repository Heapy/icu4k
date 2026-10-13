package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import kotlin.test.*

class InverseNameTest {
    @Test fun publicInverseNamesAndValidation() {
        assertEquals(65, UCharacter.getCharFromName("latin capital letter a"))
        assertEquals(0x4e00, UCharacter.getCharFromName("CJK UNIFIED IDEOGRAPH-4E00"))
        assertEquals(0xac01, UCharacter.getCharFromName("hangul syllable gag"))
        assertEquals(0x1a2, UCharacter.getCharFromNameAlias("LATIN CAPITAL LETTER GHA"))
        assertEquals(0x1a2, UCharacter.getCharFromExtendedName("LATIN CAPITAL LETTER GHA"))
        assertEquals(0, UCharacter.getCharFromExtendedName("<CONTROL-0000>"))
        assertEquals(0xd800, UCharacter.getCharFromExtendedName("<lead surrogate-D800>"))
        assertEquals(0x10ffff, UCharacter.getCharFromExtendedName("<noncharacter-10FFFF>"))
        for (name in listOf(null, "", "not a name", "<control-0041>", "<unassigned-110000>", "<control-000000000>")) {
            assertEquals(-1, UCharacter.getCharFromExtendedName(name))
        }
        assertEquals(-1, UCharacter.getCharFromName("<control-0000>"))
        assertEquals(-1, UCharacter.getCharFromName(" LATIN CAPITAL LETTER A"))
    }
}
