package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import io.github.heapy.icu4k.text.UTF16
import kotlin.random.Random
import kotlin.test.*

class ComposeOracleTest {
    @Test fun compositionCheckingAndAppendMatchOriginal() {
        val random = Random(419)
        val cps = listOf(65, 101, 0xe9, 0x301, 0x323, 0x327, 0x334, 0x1100, 0x1161, 0x11a8, 0xac00, 0xac01, 0xad, 0xfb01, 0xffa1, 0x1d165, 0x1f600, 0xd800, 0xdc00)
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            repeat(1500) {
                val text = buildString { repeat(random.nextInt(24)) { append(UTF16.valueOf(if (random.nextInt(4) == 0) random.nextInt(0x110000) else cps.random(random))) } }
                var start = random.nextInt(text.length + 1)
                if (start > 0 && start < text.length && text[start - 1].isHighSurrogate() && text[start].isLowSurrogate()) --start
                for (contiguous in listOf(false, true)) {
                    for (normalize in listOf(false, true)) {
                        val jd = StringBuilder(); val kd = StringBuilder()
                        val jr = j.compose(text, start, text.length, contiguous, normalize, JavaNormalizer.ReorderingBuffer(j, jd, 24))
                        val kr = k.compose(text, start, text.length, contiguous, normalize, Normalizer2Impl.ReorderingBuffer(k, kd, 24))
                        assertEquals(jr, kr, "$name contiguous=$contiguous normalize=$normalize text=$text")
                        assertEquals(jd.toString(), kd.toString(), "$name contiguous=$contiguous normalize=$normalize text=$text")
                    }
                    for (normalize in listOf(false, true)) {
                        val jd = StringBuilder("éA"); val kd = StringBuilder("éA")
                        j.composeAndAppend(text, normalize, contiguous, JavaNormalizer.ReorderingBuffer(j, jd, 24))
                        k.composeAndAppend(text, normalize, contiguous, Normalizer2Impl.ReorderingBuffer(k, kd, 24))
                        assertEquals(jd.toString(), kd.toString(), "$name append=$normalize contiguous=$contiguous")
                    }
                }
            }
        }
    }
}
