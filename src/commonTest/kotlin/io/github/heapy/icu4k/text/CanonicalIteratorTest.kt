package io.github.heapy.icu4k.text

import kotlin.test.*

class CanonicalIteratorTest {
    private fun collect(iterator: CanonicalIterator): Set<String> = buildSet {
        while (true) add(iterator.next() ?: break)
    }
    @Test fun equivalentsResetReuseAndEmpty() {
        val iterator = CanonicalIterator("Å")
        assertEquals("A\u030a", iterator.getSource())
        val expected = setOf("Å", "Å", "A\u030a")
        assertEquals(expected, collect(iterator))
        assertNull(iterator.next())
        iterator.reset()
        assertEquals(expected, collect(iterator))
        iterator.setSource("각")
        assertEquals(setOf("각", "가\u11a8", "\u1100\u1161\u11a8"), collect(iterator))
        iterator.setSource("")
        assertEquals(setOf(""), collect(iterator))
    }
    @Test fun permutationDepthLimitAndOutputRetention() {
        val output = mutableSetOf("existing")
        CanonicalIterator.permute("a\u0301\u0323", true, output)
        assertEquals(setOf("existing", "a\u0301\u0323", "a\u0323\u0301", "\u0301a\u0323", "\u0323a\u0301", "\u0301\u0323a", "\u0323\u0301a"), output)
        assertFailsWith<UnsupportedOperationException> { CanonicalIterator.permute("a".repeat(10), false, mutableSetOf()) }
    }
}
