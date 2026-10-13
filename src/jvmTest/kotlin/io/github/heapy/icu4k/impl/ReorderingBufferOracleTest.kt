package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import kotlin.random.Random
import kotlin.test.*

class ReorderingBufferOracleTest {
    @Test fun mutationsAndReorderingMatchBothDestinationModes() {
        val random = Random(441)
        val cps = listOf(65, 0x301, 0x323, 0x315, 0x334, 0x1d165, 0x1f600, 0xd800, 0xdc00)
        for (indirect in listOf(false, true)) repeat(60) {
            val ji = JavaNormalizer().load("nfc.nrm"); val ki = Normalizer2Impl().load("nfc.nrm")
            val jd = StringBuilder("A\u0301"); val kd = StringBuilder("A\u0301")
            fun sink(builder: StringBuilder): Appendable = object : Appendable {
                override fun append(c: Char): Appendable { builder.append(c); return this }
                override fun append(s: CharSequence?): Appendable { builder.append(s); return this }
                override fun append(s: CharSequence?, start: Int, end: Int): Appendable { builder.append(s, start, end); return this }
            }
            val j = JavaNormalizer.ReorderingBuffer(ji, if (indirect) sink(jd) else jd, 20)
            val k = Normalizer2Impl.ReorderingBuffer(ki, if (indirect) sink(kd) else kd, 20)
            repeat(250) {
                when (random.nextInt(9)) {
                    0, 1, 2 -> { val cp = cps.random(random); val cc = ji.getCC(ji.getNorm16(cp)); j.append(cp, cc); k.append(cp, cc) }
                    3 -> { j.append('!'); k.append('!') }
                    4 -> { j.flush(); k.flush() }
                    5 -> { j.flushAndAppendZeroCC("xy", 0, 2); k.flushAndAppendZeroCC("xy", 0, 2) }
                    6 -> { val length = random.nextInt(j.length() + 1); j.removeSuffix(length); k.removeSuffix(length) }
                    7 -> { j.remove(); k.remove() }
                    else -> {
                        val text = "\u0323\u0301"
                        j.append(text, 0, 2, true, 220, 230); k.append(text, 0, 2, true, 220, 230)
                    }
                }
                assertEquals(j.stringBuilder.toString(), k.getStringBuilder().toString())
                assertEquals(j.lastCC, k.getLastCC())
                assertEquals(j.length(), k.length())
                assertEquals(j.isEmpty, k.isEmpty())
                assertEquals(jd.toString(), kd.toString())
                assertTrue(k.equals(j.stringBuilder, 0, j.length()))
            }
        }
    }
}
