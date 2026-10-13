package io.github.heapy.icu4k.impl

import kotlin.test.*

class NormalizationDecompositionTest {
    @Test fun decompositionDistinguishesMissingEmptyRawAndRecursiveMappings() {
        val nfc = Normalizer2Impl().load("nfc.nrm")
        assertNull(nfc.getDecomposition('A'.code))
        assertEquals("e\u0301", nfc.getDecomposition(0xe9))
        assertEquals("\u1100\u1161\u11a8", nfc.getDecomposition(0xac01))
        assertEquals("\uac00\u11a8", nfc.getRawDecomposition(0xac01))
        val nfkc = Normalizer2Impl().load("nfkc.nrm")
        assertEquals("ffi", nfkc.getDecomposition(0xfb03))
        assertEquals("", Normalizer2Impl().load("nfkc_cf.nrm").getDecomposition(0xad))
        assertNull(nfc.getDecomposition(-1))
        assertNull(nfc.getDecomposition(0xd800))
    }
}
