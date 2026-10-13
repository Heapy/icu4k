package io.github.heapy.icu4k.text

import kotlin.test.*

class NormalizerCompareTest {
    @Test fun canonicalCaselessAndSurrogateOrdering() {
        assertEquals(0, Normalizer.compare("Å", "A\u030a", 0))
        assertEquals(0, Normalizer.compare("Straße", "STRASSE", Normalizer.COMPARE_IGNORE_CASE))
        assertEquals(0, Normalizer.compare("I", "ı", Normalizer.COMPARE_IGNORE_CASE or 1))
        assertTrue(Normalizer.compare("I", "ı", Normalizer.COMPARE_IGNORE_CASE) != 0)
        assertTrue(Normalizer.compare("😀", "\ue000", 0) < 0)
        assertTrue(Normalizer.compare("😀", "\ue000", Normalizer.COMPARE_CODE_POINT_ORDER) > 0)
        val first = charArrayOf(0xd800.toChar(), 0xd800.toChar(), 0xdc01.toChar()).concatToString()
        val second = charArrayOf(0xd800.toChar(), 0xdc00.toChar()).concatToString()
        assertTrue(Normalizer.compare(first, second, Normalizer.COMPARE_CODE_POINT_ORDER) < 0)
        assertEquals(0, Normalizer.compare(0xac01, "각", 0))
    }
}
