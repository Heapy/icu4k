package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import kotlin.test.*

class NormalizationFCDOracleTest {
    @Test fun fcdMatchesOriginalForAllDataAndCodePoints() {
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            val method = j.javaClass.getDeclaredMethod("getTrailCCFromCompYesAndZeroCC", Int::class.javaPrimitiveType).apply { isAccessible = true }
            for (cp in -1..0x110000) {
                assertEquals(j.getFCD16(cp), k.getFCD16(cp), "$name $cp")
                assertEquals(j.getFCD16FromNormData(cp), k.getFCD16FromNormData(cp))
                val n = j.getNorm16(cp)
                // The trail helper requires composition-yes and zero combining class.
                if (j.getCompQuickCheck(n) == 1 && j.getCC(n) == 0) assertEquals(method.invoke(j, n), k.getTrailCCFromCompYesAndZeroCC(n))
            }
        }
    }
}
