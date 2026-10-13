package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class CanonicalDataTest {
    @Test fun canonicalStartersSetsAndInitialization() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        assertFailsWith<NullPointerException> { impl.isCanonSegmentStarter(65) }
        assertSame(impl, impl.ensureCanonIterData())
        assertSame(impl, impl.ensureCanonIterData())
        assertTrue(impl.isCanonSegmentStarter(65))
        assertFalse(impl.isCanonSegmentStarter(0x301))
        val set = UnicodeSet().add(0x10ffff)
        assertTrue(impl.getCanonStartSet(65, set))
        assertTrue(set.contains(0xc1))
        assertFalse(set.contains(0x10ffff))
        assertTrue(impl.getCanonStartSet(0x1100, set))
        assertTrue(set.contains(0xac00, 0xac00 + 588 - 1))
        set.clear().add(0x10ffff)
        assertFalse(impl.getCanonStartSet(0x1f600, set))
        assertTrue(set.contains(0x10ffff))
        assertFalse(UCharacterProperty.INSTANCE.hasNormalizationBinaryProperty(0x301, UProperty.SEGMENT_STARTER))
    }
}
