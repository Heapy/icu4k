package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class NameMetadataTest {
    @Test fun metadataAndCharacterSetReplacement() {
        val data = UCharacterName.INSTANCE
        assertTrue(data.getMaxCharNameLength() >= "LATIN CAPITAL LETTER A".length)
        val set = UnicodeSet().add("old").add(0x1f600)
        data.getCharNameCharacters(set)
        assertTrue(set.contains('A'.code))
        assertTrue(set.contains('a'.code))
        assertTrue(set.contains('<'.code))
        assertTrue(set.contains('0'.code))
        assertFalse(set.contains(0))
        assertFalse(set.contains("old"))
        assertFalse(set.contains(0x1f600))
        val first = UnicodeSet(set)
        data.getCharNameCharacters(set)
        assertEquals(first, set)
        data.getISOCommentCharacters(set)
        assertTrue(data.getMaxISOCommentLength() >= 0)
        set.freeze()
        assertFailsWith<UnsupportedOperationException> { data.getCharNameCharacters(set) }
    }
}
