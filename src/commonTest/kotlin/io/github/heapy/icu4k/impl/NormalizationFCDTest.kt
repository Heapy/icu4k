package io.github.heapy.icu4k.impl

import kotlin.test.*

class NormalizationFCDTest {
    @Test fun decompositionLeadingAndTrailingClasses() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        assertEquals(0, impl.getFCD16('A'.code))
        assertEquals(0xe6e6, impl.getFCD16(0x301))
        assertEquals(230, impl.getFCD16(0xe9))
        assertEquals(0, impl.getFCD16(0xac01))
        assertEquals(0, impl.getFCD16(0xd800))
        assertEquals(impl.getFCD16(0xe9), impl.getFCD16FromNormData(0xe9))
    }
}
