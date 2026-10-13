package io.github.heapy.icu4k.text

import com.ibm.icu.text.Normalizer as J
import kotlin.test.*

@Suppress("DEPRECATION")
class NormalizerClosureOracleTest {
    @Test fun completeUnicodeClosureAndDestinationCapacityMatchOriginal() {
        var nonempty = 0
        for (cp in 0..0x10ffff) {
            val expected = J.getFC_NFKC_Closure(cp)
            val actual = Normalizer.getFC_NFKC_Closure(cp)
            if (expected != actual) fail("FC_NFKC_Closure U+${cp.toString(16)}: $expected != $actual")
            if (expected.isNotEmpty()) {
                nonempty++
                assertEquals(J.getFC_NFKC_Closure(cp, null), Normalizer.getFC_NFKC_Closure(cp, null))
                for (size in 0..expected.length + 2) {
                    val j = CharArray(size) { '#' }; val k = j.copyOf()
                    assertEquals(J.getFC_NFKC_Closure(cp, j), Normalizer.getFC_NFKC_Closure(cp, k))
                    assertContentEquals(j, k, "cp=$cp size=$size")
                }
            }
        }
        assertTrue(nonempty > 0)
        for (cp in listOf(-1, Int.MIN_VALUE, 0x110000, Int.MAX_VALUE)) {
            val j = runCatching { J.getFC_NFKC_Closure(cp) }
            val k = runCatching { Normalizer.getFC_NFKC_Closure(cp) }
            assertEquals(j.getOrNull(), k.getOrNull())
            assertEquals(j.exceptionOrNull()?.javaClass, k.exceptionOrNull()?.javaClass)
        }
    }
}
