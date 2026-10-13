package io.github.heapy.icu4k.impl

import kotlin.test.*

class NormalizationDataTest {
    @Test fun loadsEveryNormalizationResourceAndTreatsLeadCodePointsAsInert() {
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val impl = Normalizer2Impl()
            assertSame(impl, impl.load(name))
            assertEquals(1, impl.getNorm16(0xd800))
            assertEquals(impl.getRawNorm16(65), impl.getNorm16(65))
            assertTrue(ICUData.exists(name))
        }
    }
}
