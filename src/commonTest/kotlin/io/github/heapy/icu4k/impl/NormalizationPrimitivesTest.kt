package io.github.heapy.icu4k.impl

import kotlin.test.*

class NormalizationPrimitivesTest {
    @Test fun rawAndRecursiveHangulDecompositionDiffer() {
        val recursive = StringBuilder("prefix")
        assertEquals(3, Normalizer2Impl.Hangul.decompose(0xac01, recursive))
        assertEquals("prefix\u1100\u1161\u11a8", recursive.toString())
        val raw = StringBuilder()
        Normalizer2Impl.Hangul.getRawDecomposition(0xac01, raw)
        assertEquals("\uac00\u11a8", raw.toString())
        assertTrue(Normalizer2Impl.Hangul.isHangulLV(0xac00))
        assertFalse(Normalizer2Impl.Hangul.isJamoT(0x11a7))
        assertTrue(Normalizer2Impl.UTF16Plus.equal(StringBuilder("abc"), "abc"))
        assertTrue(Normalizer2Impl.UTF16Plus.equal("xabc", 1, 4, "abcy", 0, 3))
        assertFalse(Normalizer2Impl.UTF16Plus.equal("abc", "abd"))
    }
}
