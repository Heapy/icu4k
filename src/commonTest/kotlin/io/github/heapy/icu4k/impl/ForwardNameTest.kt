package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import kotlin.test.*

class ForwardNameTest {
    @Test fun publicNamesAndExtendedFallbacks() {
        assertEquals("LATIN CAPITAL LETTER A", UCharacter.getName(65))
        assertEquals("CJK UNIFIED IDEOGRAPH-4E00", UCharacter.getName(0x4e00))
        assertEquals("HANGUL SYLLABLE GAG", UCharacter.getName(0xac01))
        assertEquals("LATIN CAPITAL LETTER GHA", UCharacter.getNameAlias(0x1a2))
        assertNull(UCharacter.getName(0))
        assertEquals("<control-0000>", UCharacter.getExtendedName(0))
        assertEquals("<lead surrogate-D800>", UCharacter.getExtendedName(0xd800))
        assertEquals("<trail surrogate-DC00>", UCharacter.getExtendedName(0xdc00))
        assertEquals("<noncharacter-10FFFF>", UCharacter.getExtendedName(0x10ffff))
        assertEquals("<private use area-E000>", UCharacter.getExtendedName(0xe000))
        assertEquals("<unassigned-0378>", UCharacter.getExtendedName(0x378))
        assertNull(UCharacter.getExtendedName(-1))
        assertEquals("", UCharacter.getName("", ","))
        assertEquals("LATIN CAPITAL LETTER A,GRINNING FACE", UCharacter.getName("A😀", ","))
        assertNull(UCharacter.getName("\u0000", ","))
        assertEquals("null,LATIN CAPITAL LETTER A", UCharacter.getName("\u0000A", ","))
    }
}
