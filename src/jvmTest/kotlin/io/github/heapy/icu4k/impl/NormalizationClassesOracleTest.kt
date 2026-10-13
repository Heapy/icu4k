package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import kotlin.test.*

class NormalizationClassesOracleTest {
    @Test fun classesAndChecksMatchOriginalForEveryCodePoint() {
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            for (cp in 0..0x10ffff) {
                val n = j.getNorm16(cp)
                assertEquals(j.getCC(n), k.getCC(n), "$name $cp")
                assertEquals(j.getCompQuickCheck(n), k.getCompQuickCheck(n))
                assertEquals(j.isAlgorithmicNoNo(n), k.isAlgorithmicNoNo(n))
                assertEquals(j.isCompNo(n), k.isCompNo(n))
                assertEquals(j.isDecompYes(n), k.isDecompYes(n))
                assertEquals(j.getCCFromYesOrMaybeYesCP(cp), k.getCCFromYesOrMaybeYesCP(cp))
                if (cp <= 0xffff) assertEquals(j.singleLeadMightHaveNonZeroFCD16(cp), k.singleLeadMightHaveNonZeroFCD16(cp))
            }
            for (n in 0..0xffff) {
                assertEquals(JavaNormalizer.getCCFromNormalYesOrMaybe(n), Normalizer2Impl.getCCFromNormalYesOrMaybe(n))
                assertEquals(JavaNormalizer.getCCFromYesOrMaybeYes(n), Normalizer2Impl.getCCFromYesOrMaybeYes(n))
            }
        }
    }
}
