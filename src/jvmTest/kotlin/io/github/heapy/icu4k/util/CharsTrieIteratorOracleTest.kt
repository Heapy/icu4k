package io.github.heapy.icu4k.util

import com.ibm.icu.util.CharsTrie as JavaTrie
import com.ibm.icu.util.CharsTrieBuilder
import com.ibm.icu.util.StringTrieBuilder
import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.breakiter.DictionaryData
import kotlin.random.Random
import kotlin.test.*

class CharsTrieIteratorOracleTest {
    private fun compare(j: JavaTrie.Iterator, k: CharsTrie.Iterator) {
        var previousJ: JavaTrie.Entry? = null
        var previousK: CharsTrie.Entry? = null
        while (j.hasNext()) {
            assertTrue(k.hasNext())
            val a = j.next(); val b = k.next()
            if (previousJ != null) { assertSame(previousJ, a); assertSame(previousK, b) }
            previousJ = a; previousK = b
            assertEquals(a.value, b.value)
            assertEquals(a.chars.toString(), b.chars.toString())
        }
        assertFalse(k.hasNext())
        assertFailsWith<NoSuchElementException> { j.next() }
        assertFailsWith<NoSuchElementException> { k.next() }
        assertFailsWith<UnsupportedOperationException> { k.remove() }
    }
    @Test fun truncationSuffixesSharedEntriesAndResetMatch() {
        val random = Random(859)
        for (option in StringTrieBuilder.Option.entries) repeat(10) {
            val keys = linkedMapOf("" to -1)
            repeat(300) { keys[CharArray(random.nextInt(1, 80)) { random.nextInt(0x10000).toChar() }.concatToString()] = random.nextInt() }
            repeat(25) { keys["*".repeat(35) + it.toChar()] = it }
            keys["ab"] = 7; keys["abc"] = -1; keys["abcdef"] = Int.MIN_VALUE
            val builder = CharsTrieBuilder()
            for ((key, value) in keys) builder.add(key, value)
            val raw = builder.buildCharSequence(option)
            val j = JavaTrie(raw, 0); val k = CharsTrie(raw, 0)
            for (limit in listOf(0, 1, 2, 15, 16, 17, 35, 80)) {
                val ji = j.iterator(limit); val ki = k.iterator(limit)
                compare(ji, ki)
                assertSame(ji, ji.reset()); assertSame(ki, ki.reset()); compare(ji, ki)
                compare(JavaTrie.iterator(raw, 0, limit), CharsTrie.iterator(raw, 0, limit))
            }
            for (prefix in 1..35) {
                j.next(42); k.next(42)
                for (limit in listOf(0, 1, 5, 16, 40)) {
                    val ji = j.iterator(limit); val ki = k.iterator(limit)
                    compare(ji, ki); ji.reset(); ki.reset(); compare(ji, ki)
                }
            }
            j.next(-1); k.next(-1); compare(j.iterator(), k.iterator())
        }
    }
    @Test fun entireCjkDictionaryEnumerationMatches() {
        val data = DictionaryData.read(ICUBinary.getRequiredData("brkitr/cjdict.dict")) as DictionaryData.TrieData.Chars
        compare(JavaTrie(data.data, 0).iterator(), CharsTrie(data.data, 0).iterator())
    }
}
