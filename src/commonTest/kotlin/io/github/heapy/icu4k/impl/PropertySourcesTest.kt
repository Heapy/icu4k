package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class PropertySourcesTest {
    @Test fun sourcesAndBoundaryHelpers() {
        val p = UCharacterProperty.INSTANCE
        assertEquals(UCharacterProperty.SRC_NFC_CANON_ITER, p.getSource(UProperty.SEGMENT_STARTER))
        assertEquals(UCharacterProperty.SRC_EMOJI, p.getSource(UProperty.RGI_EMOJI))
        assertEquals(UCharacterProperty.SRC_PROPSVEC, p.getSource(UProperty.SCRIPT))
        assertEquals(UCharacterProperty.SRC_NONE, p.getSource(Int.MAX_VALUE))
        val set = UnicodeSet().add(0x10ffff)
        assertSame(set, p.addPropertyStarts(set))
        assertTrue(set.contains(9))
        assertTrue(set.contains(10))
        assertTrue(set.contains(0x10ffff))
        UCharacterProperty.mathCompat_addPropertyStarts(set)
        assertTrue(set.contains(0xb2))
        assertTrue(set.contains(0xb4))
        assertFailsWith<IllegalStateException> { UCharacterProperty.ulayout_addPropertyStarts(-1, set) }
    }
}
