package io.github.heapy.icu4k.util

import kotlin.test.*

class CharsTrieTest {
    @Test fun iteratorReusesEntryAndMutableSequence() {
        val raw = charArrayOf(0x30.toChar(), 'a', 0xb0.toChar(), 'b', 0x8002.toChar()).concatToString()
        val trie = CharsTrie(raw, 0)
        val iter = trie.iterator()
        val entry = iter.next()
        val chars = entry.chars
        assertEquals("a", chars.toString())
        assertEquals(1, entry.value)
        assertSame(entry, iter.next())
        assertSame(chars, entry.chars)
        assertEquals("ab", chars.toString())
        assertEquals(2, entry.value)
        assertFalse(iter.hasNext())
        assertEquals("a", iter.reset().next().chars.toString())
        val limited = trie.iterator(1)
        assertEquals(1, limited.next().value)
        assertFalse(limited.hasNext())
        val longRaw = charArrayOf(0x32.toChar(), 'a', 'b', 'c', 0x8002.toChar()).concatToString()
        val longTrie = CharsTrie(longRaw, 0)
        longTrie.next(97)
        val suffix = longTrie.iterator(1)
        assertEquals("b", suffix.next().chars.toString())
        assertEquals(-1, suffix.reset().next().value)
    }
    @Test fun readsIntermediateFinalAndRestoresState() {
        // a=1, ab=2; intermediate value and linear node type share a unit.
        val raw = charArrayOf(0x30.toChar(), 'a', 0xb0.toChar(), 'b', 0x8002.toChar()).concatToString()
        val trie = CharsTrie(raw, 0)
        assertEquals(BytesTrie.Result.NO_VALUE, trie.current())
        assertEquals(BytesTrie.Result.INTERMEDIATE_VALUE, trie.first(97))
        assertEquals(1, trie.getValue())
        val state = CharsTrie.State(); trie.saveState(state)
        assertEquals(0L, trie.getUniqueValue())
        assertEquals(BytesTrie.Result.FINAL_VALUE, trie.next(98))
        assertEquals(2, trie.getValue())
        assertEquals(5L, trie.getUniqueValue())
        assertEquals(BytesTrie.Result.NO_MATCH, trie.next(98))
        trie.resetToState(state)
        val out = StringBuilder()
        assertEquals(1, trie.getNextChars(out)); assertEquals("b", out.toString())
        assertEquals(BytesTrie.Result.FINAL_VALUE, trie.reset().next("ab", 0, 2))
        assertEquals(BytesTrie.Result.INTERMEDIATE_VALUE, trie.reset().nextForCodePoint(97))
        assertEquals(trie.getState64(), trie.clone().getState64())
    }
}
