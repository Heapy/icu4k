package io.github.heapy.icu4k.util

import kotlin.test.*

class BytesTrieTest {
    @Test fun iteratorReusesEntriesAndTruncatesSuffixes() {
        val trie = BytesTrie(byteArrayOf(16, 97, 34, 16, 98, 37), 0)
        val iter = trie.iterator()
        val entry = iter.next()
        assertContentEquals(byteArrayOf(97), entry.toByteArray())
        assertEquals(1, entry.value)
        assertSame(entry, iter.next())
        assertContentEquals(byteArrayOf(97, 98), entry.toByteArray())
        assertEquals(2, entry.value)
        assertFalse(iter.hasNext())
        assertFailsWith<NoSuchElementException> { iter.next() }
        iter.reset()
        assertEquals(1, iter.next().value)
        val limited = trie.iterator(1)
        assertEquals(1, limited.next().value)
        assertFalse(limited.hasNext())
        val longTrie = BytesTrie(byteArrayOf(18, 97, 98, 99, 37), 0)
        assertEquals(BytesTrie.Result.NO_VALUE, longTrie.next(97))
        val suffix = longTrie.iterator(1)
        assertContentEquals(byteArrayOf(98), suffix.next().toByteArray())
        assertEquals(-1, suffix.reset().next().value)
    }
    @Test fun intermediateFinalAndSavedState() {
        val raw = byteArrayOf(16, 97, 34, 16, 98, 37)
        val trie = BytesTrie(raw, 0)
        assertEquals(BytesTrie.Result.NO_VALUE, trie.current())
        assertEquals(1, trie.getNextBytes(StringBuilder()))
        assertEquals(BytesTrie.Result.INTERMEDIATE_VALUE, trie.next(97))
        assertEquals(1, trie.getValue())
        assertEquals(0L, trie.getUniqueValue())
        val state = BytesTrie.State(); trie.saveState(state)
        val clone = trie.clone()
        assertEquals(BytesTrie.Result.FINAL_VALUE, trie.next(98))
        assertEquals(2, trie.getValue())
        assertEquals(5L, trie.getUniqueValue())
        assertEquals(BytesTrie.Result.NO_MATCH, trie.next(99))
        assertEquals(BytesTrie.Result.NO_MATCH, trie.current())
        trie.resetToState(state)
        assertEquals(BytesTrie.Result.INTERMEDIATE_VALUE, trie.current())
        assertEquals(clone.getState64(), trie.getState64())
        assertEquals(BytesTrie.Result.FINAL_VALUE, trie.reset().next(byteArrayOf(97, 98), 0, 2))
        assertFalse(BytesTrie.Result.FINAL_VALUE.hasNext())
        assertTrue(BytesTrie.Result.INTERMEDIATE_VALUE.hasValue())
        assertFalse(BytesTrie.Result.NO_MATCH.matches())
    }
}
