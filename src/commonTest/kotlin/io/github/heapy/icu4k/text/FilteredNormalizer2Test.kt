package io.github.heapy.icu4k.text

import kotlin.test.*

class FilteredNormalizer2Test {
    @Test fun filteringAndAppendBoundaries() {
        val set = UnicodeSet().add(101).add(0x301).freeze()
        val n = FilteredNormalizer2(Normalizer2.getNFCInstance(), set)
        assertEquals("é!A\u0301", n.normalize("e\u0301!A\u0301"))
        val out = StringBuilder("!e")
        assertSame(out, n.normalizeSecondAndAppend(out, "\u0301!e\u0301"))
        assertEquals("!é!é", out.toString())
        assertEquals(0xe9, n.composePair(101, 0x301))
        assertEquals(-1, n.composePair(65, 0x301))
        assertTrue(n.hasBoundaryBefore(0x323))
        assertTrue(n.isInert(0x323))
        assertEquals(0, n.getCombiningClass(0x323))
        assertNull(n.getDecomposition(0xe9))
        assertTrue(n.isNormalized("A\u0301"))
        assertSame(Normalizer.MAYBE, n.quickCheck("A\u0301"))
        assertSame(Normalizer.YES, n.quickCheck("A"))
        assertFalse(n.isNormalized("e\u0301"))
        assertFailsWith<IllegalArgumentException> { n.normalize(out, out) }
        assertFailsWith<IllegalArgumentException> { n.append(out, out) }
    }
    @Test fun retainedSetAndStringElements() {
        val set = UnicodeSet().add("e\u0301")
        val n = FilteredNormalizer2(Normalizer2.getNFCInstance(), set)
        assertEquals("é!", n.normalize("e\u0301!"))
        set.clear()
        assertEquals("e\u0301!", n.normalize("e\u0301!"))
    }
}
