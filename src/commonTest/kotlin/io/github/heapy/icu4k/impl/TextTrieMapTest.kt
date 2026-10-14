package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class TextTrieMapTest {
    private fun <V> all(iterator: MutableIterator<V>?): List<V>? = iterator?.asSequence()?.toList()
    @Test fun compressedBranchesLongestPartialAndLiveValues() {
        val trie = TextTrieMap<Int?>(false)
        trie.put("", 0).put("ab", 1).put("abc", 2).put("ad", 3).put("ab", null)
        assertEquals(listOf(1, null), all(trie.get("ab!")))
        assertEquals(listOf(2), all(trie.get("abc!")))
        val out = TextTrieMap.Output()
        assertNull(trie.get("a", 0, out)); assertEquals(0, out.matchLength); assertTrue(out.partialMatch)
        trie.get("abc!", 0, out); assertTrue(out.partialMatch); assertEquals(3, out.matchLength)
        val values = trie.get("ab")!!; values.next(); values.remove()
        assertEquals(listOf<Int?>(null), all(trie.get("ab")))
        val seen = mutableListOf<Int>()
        trie.find("abc", TextTrieMap.ResultHandler { length, _ -> seen.add(length); true })
        assertEquals(listOf(0, 2, 3), seen)
    }
    @Test fun unicodeFoldingSurrogatesAndReentrantCallbacks() {
        val trie = TextTrieMap<Int>(true).put("ẞ", 1).put("𐐀", 2)
        assertEquals(listOf(1), all(trie.get("ß"))); assertNull(trie.get("ss"))
        assertEquals(listOf(2), all(trie.get("𐐨")))
        val lead = UnicodeSet(); trie.putLeadCodePoints(lead)
        assertTrue(lead.contains('ß'.code)); assertTrue(lead.contains(0x10428))
        trie.find("ß", TextTrieMap.ResultHandler { _, _ -> assertEquals(listOf(2), all(trie.get("𐐨"))); false })
        val iterator = TextTrieMap.CharIterator("𐐀", 0, true)
        iterator.next(); assertFailsWith<IllegalStateException> { iterator.processedLength() }
        iterator.next(); assertEquals(2, iterator.processedLength()); assertNull(iterator.next())
    }
    @Test fun collectionSetIsALiveIdentityView() {
        val data = mutableListOf("a", "a")
        val view = CollectionSet(data)
        assertEquals(2, view.size); assertFalse(view.equals(setOf("a")))
        view.add("b"); assertEquals(listOf("a", "a", "b"), data)
        val iterator = view.iterator(); iterator.next(); iterator.remove()
        assertEquals(listOf("a", "b"), data)
        val array = arrayOf<String?>("x", "x", "x", "x")
        assertSame(array, view.toArray(array)); assertContentEquals(arrayOf("a", "b", null, "x"), array)
    }
    @Test fun staleIteratorsObserveLiveSizeAndFailFastOnAccess() {
        val trie = TextTrieMap<Int>(false).put("a", 1).put("a", 2)
        val stale = trie.get("a")!!
        stale.next(); stale.next()
        val other = trie.get("a")!!
        other.next(); other.remove(); other.next(); other.remove()
        assertFalse(stale.hasNext())
        assertFailsWith<ConcurrentModificationException> { stale.next() }
        assertFailsWith<ConcurrentModificationException> { stale.remove() }
        val empty = trie.get("a")!!
        assertFalse(empty.hasNext()); assertFailsWith<NoSuchElementException> { empty.next() }
        trie.put("a", 3)
        assertTrue(empty.hasNext()); assertFailsWith<ConcurrentModificationException> { empty.next() }
    }

}
