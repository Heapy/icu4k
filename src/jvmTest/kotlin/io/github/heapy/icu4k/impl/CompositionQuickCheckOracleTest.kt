package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import io.github.heapy.icu4k.text.UTF16
import kotlin.random.Random
import kotlin.test.*

class CompositionQuickCheckOracleTest {
    @Test fun quickChecksMatchAtEverySliceBoundary() {
        val random = Random(914)
        val cps = listOf(65, 97, 0xe9, 0x301, 0x323, 0x327, 0x334, 0x340, 0xac01, 0x1100, 0x1161, 0x11a8, 0xad, 0x1d165, 0x1f600, 0xd800, 0xdc00)
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            repeat(160) {
                val text = buildString { repeat(random.nextInt(15)) { append(UTF16.valueOf(cps.random(random))) } }
                for (start in 0..text.length) for (end in start..text.length) for (contiguous in listOf(false, true)) for (span in listOf(false, true)) {
                    val expected = runCatching { j.composeQuickCheck(text, start, end, contiguous, span) }
                    val actual = runCatching { k.composeQuickCheck(text, start, end, contiguous, span) }
                    assertEquals(expected.getOrNull(), actual.getOrNull(), "$name $start $end $contiguous $span")
                    assertEquals(expected.isSuccess, actual.isSuccess)
                }
            }
        }
    }
}
