package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import io.github.heapy.icu4k.lang.CharSequences
import kotlin.random.Random
import kotlin.test.*

class ComposePairOracleTest {
    @Test fun rawDecompositionPairsAndArbitraryPairsMatchOriginal() {
        val random = Random(542)
        for (name in listOf("nfc.nrm", "nfkc.nrm", "nfkc_cf.nrm", "nfkc_scf.nrm", "uts46.nrm")) {
            val j = JavaNormalizer().load(name); val k = Normalizer2Impl().load(name)
            for (cp in 0..0x10ffff) {
                val raw = j.getRawDecomposition(cp) ?: continue
                val points = CharSequences.codePoints(raw)
                if (points.size == 2) assertEquals(j.composePair(points[0], points[1]), k.composePair(points[0], points[1]), "$name $cp")
            }
            repeat(100000) {
                val a = random.nextInt(-1, 0x110001)
                val b = if (random.nextBoolean()) random.nextInt(0x300, 0x370) else random.nextInt(-1, 0x110001)
                assertEquals(j.composePair(a, b), k.composePair(a, b), "$name $a $b")
            }
        }
    }
}
