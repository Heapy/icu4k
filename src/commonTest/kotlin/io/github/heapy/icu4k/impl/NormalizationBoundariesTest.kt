package io.github.heapy.icu4k.impl

import kotlin.test.*

class NormalizationBoundariesTest {
    @Test fun boundariesDistinguishAccentsAndComposingStarters() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        assertTrue(impl.hasCompBoundaryBefore('A'.code))
        assertFalse(impl.hasCompBoundaryAfter('A'.code, false))
        assertFalse(impl.hasDecompBoundaryBefore(0x301))
        assertFalse(impl.hasDecompBoundaryAfter(0x301))
        assertTrue(impl.hasDecompBoundaryBefore(0xe9))
        assertFalse(impl.hasDecompBoundaryAfter(0xe9))
        assertTrue(impl.isCompInert('!'.code, true))
        assertTrue(impl.isDecompInert('A'.code))
        assertFalse(impl.isFCDInert(0xe9))
    }
}
