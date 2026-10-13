package io.github.heapy.icu4k.util

import kotlin.test.*

class CharsTrieTest {
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
