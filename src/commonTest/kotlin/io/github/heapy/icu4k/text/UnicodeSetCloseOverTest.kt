package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetCloseOverTest {
    @Test fun rootMappingsUseWordTitleBoundariesAndRetainInputStrings() {
        val set = UnicodeSet().add("Straße TEST").closeOver(UnicodeSet.ADD_CASE_MAPPINGS)
        for (s in listOf("Straße TEST", "straße test", "Straße Test", "STRASSE TEST", "strasse test")) assertTrue(set.contains(s), s)
        val closure = UnicodeSet().add('k'.code).closeOver(UnicodeSet.CASE_INSENSITIVE)
        assertTrue(closure.contains(0x212a))
        val mappings = UnicodeSet().add('k'.code).closeOver(UnicodeSet.ADD_CASE_MAPPINGS)
        assertTrue(mappings.contains('K'.code)); assertFalse(mappings.contains(0x212a))
        assertFailsWith<UnsupportedOperationException> { UnicodeSet().freeze().closeOver(0) }
    }
}
