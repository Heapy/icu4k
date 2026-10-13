package io.github.heapy.icu4k.util

import kotlin.test.*

class MutableCodePointTrieTest {
    @Test fun blocksRangesClonesAndCopies() {
        val trie = MutableCodePointTrie(7, -99)
        trie.setRange(15, 65, 9)
        trie.set(16, 10)
        trie.setRange(0xffff, 0x10011, 11)
        trie.set(0x10ffff, Int.MIN_VALUE)
        assertEquals(7, trie[14])
        assertEquals(9, trie[15])
        assertEquals(10, trie[16])
        assertEquals(9, trie[65])
        assertEquals(7, trie[66])
        assertEquals(11, trie[0x10011])
        assertEquals(7, trie[0x10012])
        assertEquals(Int.MIN_VALUE, trie[0x10ffff])
        assertEquals(-99, trie[-1])
        val range = CodePointMap.Range()
        assertTrue(trie.getRange(17, null, range))
        assertEquals(65, range.getEnd())
        val clone = trie.clone()
        val copy = MutableCodePointTrie.fromCodePointMap(trie)
        trie.setRange(0, 0x10ffff, 1)
        assertEquals(10, clone[16])
        assertEquals(10, copy[16])
        clone.set(16, 42)
        assertEquals(10, copy[16])
        assertFailsWith<IllegalArgumentException> { trie.set(-1, 1) }
        assertFailsWith<IllegalArgumentException> { trie.setRange(2, 1, 0) }
        assertFalse(trie.getRange(0x110000, null, range))
    }
    @Test fun filterMergesDifferentRawValuesAndHighTail() {
        val trie = MutableCodePointTrie(8, -1)
        trie.setRange(15, 511, 10)
        trie.setRange(512, 1023, 12)
        val range = CodePointMap.Range()
        assertTrue(trie.getRange(0, CodePointMap.ValueFilter { it and 1 }, range))
        assertEquals(0x10ffff, range.getEnd())
        assertEquals(0, range.getValue())
        trie.set(513, 11)
        assertTrue(trie.getRange(0, CodePointMap.ValueFilter { it and 1 }, range))
        assertEquals(512, range.getEnd())
    }
}
