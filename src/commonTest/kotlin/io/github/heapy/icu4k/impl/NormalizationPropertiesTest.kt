package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class NormalizationPropertiesTest {
    @Test fun canonicalClassesInertnessAndCasefolding() {
        val p = UCharacterProperty.INSTANCE
        assertEquals(230, p.getNormalizationIntPropertyValue(0x301, UProperty.CANONICAL_COMBINING_CLASS))
        assertEquals(0, p.getNormalizationIntPropertyValue(0xe9, UProperty.LEAD_CANONICAL_COMBINING_CLASS))
        assertEquals(230, p.getNormalizationIntPropertyValue(0xe9, UProperty.TRAIL_CANONICAL_COMBINING_CLASS))
        assertTrue(p.hasNormalizationBinaryProperty(0x212b, UProperty.FULL_COMPOSITION_EXCLUSION))
        assertFalse(p.hasNormalizationBinaryProperty(0xe9, UProperty.NFD_INERT))
        assertTrue(p.hasNormalizationBinaryProperty(0xdf, UProperty.CHANGES_WHEN_CASEFOLDED))
        assertFalse(p.hasNormalizationBinaryProperty(0x301, UProperty.CHANGES_WHEN_CASEFOLDED))
        assertFalse(p.hasNormalizationBinaryProperty(-1, UProperty.CHANGES_WHEN_CASEFOLDED))
        assertTrue(p.hasNormalizationBinaryProperty(0xad, UProperty.CHANGES_WHEN_NFKC_CASEFOLDED))
    }
}
