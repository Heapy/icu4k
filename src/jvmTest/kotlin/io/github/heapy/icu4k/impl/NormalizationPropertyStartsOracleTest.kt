package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class NormalizationPropertyStartsOracleTest {
    @Test fun allRangesMatchOriginal() {
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name)
            val k = Normalizer2Impl().load(name)
            for (starts in listOf(false, true)) {
                val js = JavaSet().add(0x10ffff)
                val ks = UnicodeSet().add(0x10ffff)
                if (starts) { j.addPropertyStarts(js); k.addPropertyStarts(ks) }
                else { j.addLcccChars(js); k.addLcccChars(ks) }
                assertEquals(js.getRangeCount(), ks.getRangeCount(), "$name starts=$starts")
                for (r in 0 until js.getRangeCount()) {
                    assertEquals(js.getRangeStart(r), ks.getRangeStart(r), "$name range=$r")
                    assertEquals(js.getRangeEnd(r), ks.getRangeEnd(r), "$name range=$r")
                }
            }
        }
    }
}
