package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import io.github.heapy.icu4k.text.UTF16
import kotlin.random.Random
import kotlin.test.*

class RecompositionOracleTest {
    @Test fun recompositionOfDecomposedBuffersMatchesOriginal() {
        val random = Random(817)
        val cps = listOf(65, 97, 101, 0xe9, 0x301, 0x323, 0x327, 0x334, 0x315, 0xac01, 0x1100, 0x1161, 0x11a8, 0x1d165, 0x1f600, 0xd800, 0xdc00)
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            val method = j.javaClass.getDeclaredMethod("recompose", JavaNormalizer.ReorderingBuffer::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            repeat(600) {
                val source = buildString { repeat(random.nextInt(25)) { append(UTF16.valueOf(cps.random(random))) } }
                for (contiguous in listOf(false, true)) {
                    val jd = StringBuilder(); val kd = StringBuilder()
                    j.decompose(source, 0, source.length, jd, -1); k.decompose(source, 0, source.length, kd, -1)
                    jd.insert(0, "prefix!"); kd.insert(0, "prefix!")
                    val jb = JavaNormalizer.ReorderingBuffer(j, jd, 30); val kb = Normalizer2Impl.ReorderingBuffer(k, kd, 30)
                    method.invoke(j, jb, 7, contiguous); k.recompose(kb, 7, contiguous)
                    assertEquals(jd.toString(), kd.toString(), "$name $contiguous")
                    assertEquals(jb.lastCC, kb.getLastCC())
                }
            }
        }
    }
}
