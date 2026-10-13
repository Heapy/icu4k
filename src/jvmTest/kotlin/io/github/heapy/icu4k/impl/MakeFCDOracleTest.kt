package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import io.github.heapy.icu4k.text.UTF16
import kotlin.random.Random
import kotlin.test.*

class MakeFCDOracleTest {
    @Test fun fcdRepairQuickCheckAndAppendMatchOriginal() {
        val random = Random(712)
        val cps = listOf(65, 0xe9, 0x301, 0x323, 0x327, 0x334, 0xac01, 0xad, 0x1d165, 0x1f600, 0xd800, 0xdc00)
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            repeat(350) {
                val text = buildString { repeat(random.nextInt(20)) { append(UTF16.valueOf(cps.random(random))) } }
                for (start in 0..text.length) {
                    val jd = StringBuilder("prefix!"); val kd = StringBuilder("prefix!")
                    val jb = JavaNormalizer.ReorderingBuffer(j, jd, 20); val kb = Normalizer2Impl.ReorderingBuffer(k, kd, 20)
                    assertEquals(j.makeFCD(text, start, text.length, jb), k.makeFCD(text, start, text.length, kb))
                    assertEquals(jd.toString(), kd.toString(), "$name $start")
                    assertEquals(j.makeFCD(text, start, text.length, null), k.makeFCD(text, start, text.length, null))
                }
                for (repair in listOf(false, true)) {
                    val jd = StringBuilder("é"); val kd = StringBuilder("é")
                    j.makeFCDAndAppend(text, repair, JavaNormalizer.ReorderingBuffer(j, jd, 20))
                    k.makeFCDAndAppend(text, repair, Normalizer2Impl.ReorderingBuffer(k, kd, 20))
                    assertEquals(jd.toString(), kd.toString(), "$name append $repair")
                }
            }
        }
    }
}
