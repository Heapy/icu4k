package io.github.heapy.icu4k.text

import kotlin.test.*

class NormalizerArrayTest {
    @Test fun arrayNormalizationAndBoundaryComposition() {
        val dest = CharArray(8) { '#' }
        assertEquals(2, Normalizer.normalize("XﬁY".toCharArray(), 1, 2, dest, 2, 6, Normalizer.NFKC, 0))
        assertEquals("##fi####", dest.concatToString())
        assertSame(Normalizer.NO, Normalizer.quickCheck("ﬁ".toCharArray(), Normalizer.NFKC, 0))
        assertEquals("Å", Normalizer.concatenate("A", "\u030a", Normalizer.NFC, 0))
        val small = CharArray(1) { '#' }
        val failure = assertFailsWith<IndexOutOfBoundsException> {
            Normalizer.normalize("ﬁ".toCharArray(), small, Normalizer.NFKC, 0)
        }
        assertEquals("2", failure.message)
        assertEquals('#', small[0])
        assertSame(Normalizer.NFC, Normalizer.COMPOSE)
    }
}
