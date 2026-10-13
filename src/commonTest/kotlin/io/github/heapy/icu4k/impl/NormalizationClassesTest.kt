package io.github.heapy.icu4k.impl

import kotlin.test.*

class NormalizationClassesTest {
    @Test fun readsCombiningClassesAndQuickCheckValues() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        assertEquals(230, impl.getCC(impl.getNorm16(0x301)))
        assertEquals(0, impl.getCC(impl.getNorm16('A'.code)))
        assertEquals(1, impl.getCompQuickCheck(impl.getNorm16('A'.code)))
        assertEquals(2, impl.getCompQuickCheck(impl.getNorm16(0x301)))
        assertTrue(impl.singleLeadMightHaveNonZeroFCD16(0x301))
        assertTrue(impl.isDecompYes(impl.getNorm16('A'.code)))
        assertFalse(impl.isDecompYes(impl.getNorm16(0xe9)))
    }
}
