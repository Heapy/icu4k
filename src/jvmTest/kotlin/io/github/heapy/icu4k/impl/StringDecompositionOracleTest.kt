package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import kotlin.random.Random
import kotlin.test.*

class StringDecompositionOracleTest {
    @Test fun normalizationQuickCheckAndAppendMatchOriginal() {
        val random = Random(914)
        val cps = listOf(65, 0xe9, 0x301, 0x323, 0x334, 0xac01, 0xfb03, 0xad, 0x1d165, 0x1f600, 0xd800, 0xdc00)
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            repeat(300) {
                val text = buildString { repeat(random.nextInt(20)) { append(io.github.heapy.icu4k.text.UTF16.valueOf(cps.random(random))) } }
                val start = random.nextInt(text.length + 1); val end = random.nextInt(start, text.length + 1)
                val jd = StringBuilder("old"); val kd = StringBuilder("old")
                j.decompose(text, start, end, jd, -1); k.decompose(text, start, end, kd, -1)
                assertEquals(jd.toString(), kd.toString(), name)
                assertEquals(j.decompose(text, start, end, null), k.decompose(text, start, end, null))
                for (normalize in listOf(false, true)) {
                    val ja = StringBuilder("A\u0301"); val ka = StringBuilder("A\u0301")
                    j.decomposeAndAppend(text, normalize, JavaNormalizer.ReorderingBuffer(j, ja, 20))
                    k.decomposeAndAppend(text, normalize, Normalizer2Impl.ReorderingBuffer(k, ka, 20))
                    assertEquals(ja.toString(), ka.toString(), "$name append $normalize")
                }
            }
        }
    }
}
