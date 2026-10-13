package io.github.heapy.icu4k.text

import com.ibm.icu.text.Normalizer2 as JavaNormalizer
import com.ibm.icu.text.Normalizer as JavaLegacy
import io.github.heapy.icu4k.impl.ICUBinary
import kotlin.random.Random
import kotlin.test.*

class Normalizer2OracleTest {
    @Test fun allDataAndModesMatchPublicApi() {
        val random = Random(982)
        val cps = listOf(65, 101, 0xe9, 0x301, 0x323, 0x327, 0x334, 0x1100, 0x1161, 0x11a8, 0xac00, 0xad, 0xfb03, 0xdf, 0x1d165, 0x1f600, 0xd800, 0xdc00)
        for (name in listOf("nfc", "nfkc", "nfkc_cf", "nfkc_scf", "uts46")) {
            for (mode in Normalizer2.Mode.entries) {
                val j = JavaNormalizer.getInstance(null, name, JavaNormalizer.Mode.valueOf(mode.name))
                val k = Normalizer2.getInstance(null, name, mode)
                repeat(250) {
                    val text = buildString { repeat(random.nextInt(24)) { append(UTF16.valueOf(if (random.nextInt(4) == 0) random.nextInt(0x110000) else cps.random(random))) } }
                    assertEquals(j.normalize(text), k.normalize(text), "$name $mode")
                    assertEquals(j.normalize(StringBuilder(text)), k.normalize(StringBuilder(text)), "$name $mode mutable")
                    assertEquals(j.isNormalized(text), k.isNormalized(text))
                    assertEquals(j.spanQuickCheckYes(text), k.spanQuickCheckYes(text))
                    val jq = j.quickCheck(text)
                    val expected = if (jq === JavaLegacy.YES) Normalizer.YES else if (jq === JavaLegacy.NO) Normalizer.NO else Normalizer.MAYBE
                    assertSame(expected, k.quickCheck(text))
                    for (normalize in listOf(false, true)) {
                        val first = j.normalize("éA")
                        val second = if (normalize) text else j.normalize(text)
                        val jd = StringBuilder(first); val kd = StringBuilder(first)
                        if (normalize) { j.normalizeSecondAndAppend(jd, second); k.normalizeSecondAndAppend(kd, second) }
                        else { j.append(jd, second); k.append(kd, second) }
                        assertEquals(jd.toString(), kd.toString(), "$name $mode append=$normalize")
                    }
                    val c = random.nextInt(0x110000)
                    assertEquals(j.getDecomposition(c), k.getDecomposition(c))
                    assertEquals(j.getRawDecomposition(c), k.getRawDecomposition(c))
                    assertEquals(j.getCombiningClass(c), k.getCombiningClass(c))
                    assertEquals(j.hasBoundaryBefore(c), k.hasBoundaryBefore(c))
                    assertEquals(j.hasBoundaryAfter(c), k.hasBoundaryAfter(c))
                    assertEquals(j.isInert(c), k.isInert(c))
                }
            }
        }
    }
    @Test fun concurrentCustomDataPublication() {
        val bytes = ICUBinary.getRequiredData("nfc.nrm").bytes
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        val gate = java.util.concurrent.CyclicBarrier(8)
        try {
            val futures = (0 until 8).map {
                pool.submit<Normalizer2> {
                    gate.await(10, java.util.concurrent.TimeUnit.SECONDS)
                    val instance = Normalizer2.getInstance(io.github.heapy.icu4k.util.BinaryBuffer(bytes), "concurrent-custom-nfc", Normalizer2.Mode.COMPOSE)
                    repeat(100) { assertEquals("é", instance.normalize("e\u0301")) }
                    instance
                }
            }
            val instances = futures.map { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
            instances.forEach { assertSame(instances.first(), it) }
        } finally {
            pool.shutdownNow()
        }
    }
    @Test fun customBinaryDataAndNameCache() {
        val bytes = ICUBinary.getRequiredData("nfkc.nrm").bytes
        val j = JavaNormalizer.getInstance(bytes.inputStream(), "oracle-custom-nfkc", JavaNormalizer.Mode.COMPOSE)
        val k = Normalizer2.getInstance(io.github.heapy.icu4k.util.BinaryBuffer(bytes), "oracle-custom-nfkc", Normalizer2.Mode.COMPOSE)
        assertEquals(j.normalize("ﬃe\u0301"), k.normalize("ﬃe\u0301"))
        assertSame(k, Normalizer2.getInstance(null, "oracle-custom-nfkc", Normalizer2.Mode.COMPOSE))
    }
}
