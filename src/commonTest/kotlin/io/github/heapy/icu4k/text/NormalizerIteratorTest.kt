package io.github.heapy.icu4k.text

import kotlin.test.*

class NormalizerIteratorTest {
    @Test fun expandedCharactersKeepOriginalSegmentPositions() {
        val n = Normalizer("ﬁA\u030a", Normalizer.NFKC, 0)
        assertEquals('f'.code, n.next()); assertEquals(0, n.getIndex())
        val clone = n.clone()
        assertEquals('i'.code, n.next()); assertEquals(1, n.getIndex())
        assertEquals('i'.code, clone.next())
        assertEquals(0xc5, n.next()); assertEquals(3, n.getIndex())
        assertEquals(Normalizer.DONE, n.next())
        assertEquals(0xc5, n.previous()); assertEquals(1, n.getIndex())
        assertEquals('i'.code, n.previous()); assertEquals(0, n.getIndex())
        assertEquals('f'.code, n.previous())
        assertEquals(Normalizer.DONE, n.previous())
        assertEquals("fiÅ", Normalizer.normalize("ﬁA\u030a", Normalizer.NFKC))
    }
}
