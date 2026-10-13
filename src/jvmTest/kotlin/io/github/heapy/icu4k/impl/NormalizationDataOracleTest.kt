package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import kotlin.test.*

class NormalizationDataOracleTest {
    @Test fun dataFieldsAndTriesMatchAllOriginalResources() {
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            for (field in listOf("minDecompNoCP", "minCompNoMaybeCP", "minLcccCP", "minYesNo", "minYesNoMappingsOnly", "minNoNo", "minNoNoCompBoundaryBefore", "minNoNoCompNoMaybeCC", "minNoNoEmpty", "limitNoNo", "minMaybeNo", "minMaybeNoCombinesFwd", "minMaybeYes", "centerNoNoDelta", "extraData")) {
                val jf = j.javaClass.getDeclaredField(field).apply { isAccessible = true }
                val kf = k.javaClass.getDeclaredField(field).apply { isAccessible = true }
                assertEquals(jf.get(j), kf.get(k), "$name $field")
            }
            val jf = j.javaClass.getDeclaredField("smallFCD").apply { isAccessible = true }
            val kf = k.javaClass.getDeclaredField("smallFCD").apply { isAccessible = true }
            assertContentEquals(jf.get(j) as ByteArray, kf.get(k) as ByteArray)
            for (cp in -1..0x110000) {
                assertEquals(j.getNorm16(cp), k.getNorm16(cp), "$name $cp")
                assertEquals(j.getRawNorm16(cp), k.getRawNorm16(cp))
            }
        }
    }
}
