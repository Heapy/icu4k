package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import kotlin.test.*

class NormalizationBoundariesOracleTest {
    @Test fun allBoundariesAndInertPredicatesMatchOriginal() {
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            for (cp in -1..0x110000) {
                assertEquals(j.hasDecompBoundaryBefore(cp), k.hasDecompBoundaryBefore(cp), "$name $cp before")
                assertEquals(j.hasDecompBoundaryAfter(cp), k.hasDecompBoundaryAfter(cp))
                assertEquals(j.isDecompInert(cp), k.isDecompInert(cp))
                assertEquals(j.hasFCDBoundaryBefore(cp), k.hasFCDBoundaryBefore(cp))
                assertEquals(j.hasFCDBoundaryAfter(cp), k.hasFCDBoundaryAfter(cp))
                assertEquals(j.isFCDInert(cp), k.isFCDInert(cp))
                assertEquals(j.hasCompBoundaryBefore(cp), k.hasCompBoundaryBefore(cp))
                for (contiguous in listOf(false, true)) {
                    assertEquals(j.hasCompBoundaryAfter(cp, contiguous), k.hasCompBoundaryAfter(cp, contiguous))
                    assertEquals(j.isCompInert(cp, contiguous), k.isCompInert(cp, contiguous))
                }
                val n = j.getNorm16(cp)
                assertEquals(j.norm16HasDecompBoundaryBefore(n), k.norm16HasDecompBoundaryBefore(n))
                assertEquals(j.norm16HasDecompBoundaryAfter(n), k.norm16HasDecompBoundaryAfter(n))
            }
        }
    }
}
