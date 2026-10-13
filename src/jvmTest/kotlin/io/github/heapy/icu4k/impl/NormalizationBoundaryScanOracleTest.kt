package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import io.github.heapy.icu4k.text.UTF16
import kotlin.random.Random
import kotlin.test.*

class NormalizationBoundaryScanOracleTest {
    @Test fun privateBoundaryScansAndShortDecompositionMatchOriginal() {
        val random = Random(781)
        val cps = listOf(65, 0xe9, 0x301, 0x323, 0x327, 0x334, 0xac01, 0xad, 0x1d165, 0x1f600, 0xd800, 0xdc00)
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            val intType = Int::class.javaPrimitiveType!!; val boolType = Boolean::class.javaPrimitiveType!!
            fun method(name: String, vararg args: Class<*>) = j.javaClass.getDeclaredMethod(name, *args).apply { isAccessible = true }
            val previousComp = method("findPreviousCompBoundary", CharSequence::class.java, intType, boolType)
            val nextComp = method("findNextCompBoundary", CharSequence::class.java, intType, intType, boolType)
            val previousFcd = method("findPreviousFCDBoundary", CharSequence::class.java, intType)
            val nextFcd = method("findNextFCDBoundary", CharSequence::class.java, intType, intType)
            val short = method("decomposeShort", CharSequence::class.java, intType, intType, boolType, boolType, JavaNormalizer.ReorderingBuffer::class.java)
            repeat(150) {
                val text = buildString { repeat(random.nextInt(15)) { append(UTF16.valueOf(cps.random(random))) } }
                for (p in 0..text.length) {
                    assertEquals(previousFcd.invoke(j, text, p), k.findPreviousFCDBoundary(text, p))
                    assertEquals(nextFcd.invoke(j, text, p, text.length), k.findNextFCDBoundary(text, p, text.length))
                    for (contiguous in listOf(false, true)) {
                        assertEquals(previousComp.invoke(j, text, p, contiguous), k.findPreviousCompBoundary(text, p, contiguous))
                        assertEquals(nextComp.invoke(j, text, p, text.length, contiguous), k.findNextCompBoundary(text, p, text.length, contiguous))
                        for (stop in listOf(false, true)) {
                            val jd = StringBuilder("A\u0301"); val kd = StringBuilder("A\u0301")
                            val jb = JavaNormalizer.ReorderingBuffer(j, jd, 20); val kb = Normalizer2Impl.ReorderingBuffer(k, kd, 20)
                            assertEquals(short.invoke(j, text, p, text.length, stop, contiguous, jb), k.decomposeShort(text, p, text.length, stop, contiguous, kb))
                            assertEquals(jd.toString(), kd.toString())
                        }
                    }
                }
            }
        }
    }
}
