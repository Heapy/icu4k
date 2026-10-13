package io.github.heapy.icu4k.impl

import kotlin.test.*

class GroupNameTest {
    @Test fun modernNamesAliasesAndGroupBoundaries() {
        val data = UCharacterName.INSTANCE
        assertEquals("LATIN CAPITAL LETTER A", data.getGroupName(65, UCharacterNameChoice.UNICODE_CHAR_NAME))
        assertEquals("GRINNING FACE", data.getGroupName(0x1f600, UCharacterNameChoice.UNICODE_CHAR_NAME))
        assertNull(data.getGroupName(0x4e00, UCharacterNameChoice.UNICODE_CHAR_NAME))
        assertNull(data.getGroupName(0xfeff, UCharacterNameChoice.CHAR_NAME_ALIAS))
        assertEquals("LATIN CAPITAL LETTER GHA", data.getGroupName(0x1a2, UCharacterNameChoice.CHAR_NAME_ALIAS))
        val group = data.getGroup(65)
        assertEquals(2, data.getGroupMSB(group))
        val offsets = CharArray(33); val lengths = CharArray(33)
        val index = data.getGroupLengths(group, offsets, lengths)
        assertEquals(data.getGroupName(65, 0), data.getGroupName(index + offsets[1].code, lengths[1].code, 0))
        assertEquals(64, UCharacterName.getGroupMinFromCodepoint(65))
        assertEquals(96, UCharacterName.getGroupLimit(2))
        assertEquals(-1, data.getGroupMSB(data.m_groupcount_))
    }
}
