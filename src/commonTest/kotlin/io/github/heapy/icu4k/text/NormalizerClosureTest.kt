package io.github.heapy.icu4k.text

import kotlin.test.*

class NormalizerClosureTest {
    @Test fun compatibilityFoldingClosureAndPreflight() {
        assertEquals("", Normalizer.getFC_NFKC_Closure('A'.code))
        // Compatibility decomposition reveals capital letters only after the first fold.
        assertEquals("a", Normalizer.getFC_NFKC_Closure(0x1d400))
        assertEquals("tel", Normalizer.getFC_NFKC_Closure(0x2121))
        assertEquals(3, Normalizer.getFC_NFKC_Closure(0x2121, null))
        val short = CharArray(2) { '#' }
        assertEquals(3, Normalizer.getFC_NFKC_Closure(0x2121, short))
        assertContentEquals(charArrayOf('#', '#'), short)
        val full = CharArray(4) { '#' }
        assertEquals(3, Normalizer.getFC_NFKC_Closure(0x2121, full))
        assertEquals("tel#", full.concatToString())
    }
}
