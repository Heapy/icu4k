package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import kotlin.test.*

class NormalizationDecompositionOracleTest {
    @Test fun everyCodePointDecompositionMatchesOriginalData() {
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            for (cp in -1..0x110000) {
                assertEquals(j.getDecomposition(cp), k.getDecomposition(cp), "$name $cp recursive")
                assertEquals(j.getRawDecomposition(cp), k.getRawDecomposition(cp), "$name $cp raw")
            }
        }
    }
}
