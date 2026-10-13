package io.github.heapy.icu4k.util

import com.ibm.icu.util.CharsTrie as JavaTrie
import com.ibm.icu.util.CharsTrieBuilder
import com.ibm.icu.util.StringTrieBuilder
import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.breakiter.DictionaryData
import kotlin.random.Random
import kotlin.test.*

class CharsTrieOracleTest {
    private fun compare(j: JavaTrie, k: CharsTrie) {
        assertEquals(j.current().name, k.current().name)
        assertEquals(j.state64, k.getState64())
        if (j.current().hasValue()) assertEquals(j.value, k.getValue())
        assertEquals(j.uniqueValue, k.getUniqueValue())
        val a = StringBuilder(); val b = StringBuilder()
        assertEquals(j.getNextChars(a), k.getNextChars(b)); assertEquals(a.toString(), b.toString())
    }
    private fun check(raw: CharSequence, keys: Iterable<String>) {
        val j = JavaTrie(raw, 0); val k = CharsTrie(raw, 0)
        compare(j, k)
        for (key in keys) {
            j.reset(); k.reset()
            for (unit in key) {
                assertEquals(j.next(unit.code).name, k.next(unit.code).name)
                compare(j, k)
                val js = JavaTrie.State(); val ks = CharsTrie.State()
                j.saveState(js); k.saveState(ks)
                assertEquals(j.next(-1).name, k.next(-1).name)
                j.resetToState(js); k.resetToState(ks)
                compare(JavaTrie(j), CharsTrie(k))
            }
            j.reset(); k.reset()
            assertEquals(j.next(key, 0, key.length).name, k.next(key, 0, key.length).name)
            compare(j, k)
            assertEquals(j.next(key, 2, 1).name, k.next(key, 2, 1).name)
            val state = k.getState64()
            j.next(0); k.next(0)
            j.resetToState64(state); k.resetToState64(state); compare(j, k)
            val cp = if (key.isNotEmpty()) key.codePointAt(0) else 0x10000
            assertEquals(j.firstForCodePoint(cp).name, k.firstForCodePoint(cp).name)
            compare(j, k)
            assertEquals(j.nextForCodePoint(0x10000).name, k.nextForCodePoint(0x10000).name)
            compare(j, k)
        }
        for (cp in listOf(-1, 0, 0xffff, 0x10000, 0x10ffff, 0x110000, Int.MAX_VALUE)) {
            assertEquals(j.firstForCodePoint(cp).name, k.firstForCodePoint(cp).name)
            compare(j, k)
        }
        assertFailsWith<IllegalArgumentException> { k.resetToState(CharsTrie.State()) }
    }
    @Test fun randomizedBuilderOutputAndAllValueWidthsMatchIcu() {
        val random = Random(789)
        val values = intArrayOf(0, 255, 256, 0x3fff, 0x4000, 0x7ffeffff, 0x7fff0000, Int.MAX_VALUE, Int.MIN_VALUE, -1, 0xfdffff, 0xfe0000)
        for (option in StringTrieBuilder.Option.entries) repeat(15) { round ->
            val entries = linkedMapOf("" to values[round % values.size])
            repeat(200) {
                val key = CharArray(random.nextInt(1, 40)) { if (random.nextBoolean()) random.nextInt(10).toChar() else random.nextInt(0x10000).toChar() }.concatToString()
                entries[key] = if (round % 3 == 0) -1 else values[random.nextInt(values.size)]
            }
            entries["abc"] = 1; entries["abcdef"] = -1; entries["😀"] = Int.MIN_VALUE
            val builder = CharsTrieBuilder()
            for ((key, value) in entries) builder.add(key, value)
            check(builder.buildCharSequence(option), entries.keys)
        }
    }
    @Test fun actualCjkDictionaryMatches() {
        val data = DictionaryData.read(ICUBinary.getRequiredData("brkitr/cjdict.dict")) as DictionaryData.TrieData.Chars
        val iterator = JavaTrie(data.data, 0).iterator()
        val keys = ArrayList<String>()
        while (iterator.hasNext() && keys.size < 1000) keys.add(iterator.next().chars.toString())
        check(data.data, keys)
    }
}
