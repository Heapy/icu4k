package io.github.heapy.icu4k.util

import com.ibm.icu.util.BytesTrie as JavaTrie
import com.ibm.icu.util.BytesTrieBuilder
import com.ibm.icu.util.StringTrieBuilder
import kotlin.random.Random
import kotlin.test.*

class BytesTrieOracleTest {
    private fun compare(j: JavaTrie, k: BytesTrie) {
        assertEquals(j.current().name, k.current().name)
        assertEquals(j.state64, k.getState64())
        if (j.current().hasValue()) assertEquals(j.value, k.getValue())
        assertEquals(j.uniqueValue, k.getUniqueValue())
        val a = StringBuilder(); val b = StringBuilder()
        assertEquals(j.getNextBytes(a), k.getNextBytes(b))
        assertEquals(a.toString(), b.toString())
    }
    @Test fun generatedTriesAndMixedStateOperationsMatchIcu() {
        val random = Random(988)
        for (option in StringTrieBuilder.Option.entries) repeat(20) { round ->
            val entries = linkedMapOf<List<Byte>, Int>()
            val values = intArrayOf(0, 1, 64, 65, 0x1aff, 0x1b00, 0x11ffff, 0x120000, 0xffffff, 0x1000000, Int.MIN_VALUE, Int.MAX_VALUE, -1)
            entries[emptyList()] = values[round % values.size]
            repeat(300) {
                val key = List(random.nextInt(1, 40)) { if (random.nextBoolean()) random.nextInt(10).toByte() else random.nextInt(256).toByte() }
                entries[key] = if (round % 3 == 0) -1 else values[random.nextInt(values.size)]
            }
            val builder = BytesTrieBuilder()
            for ((key, value) in entries) builder.add(key.toByteArray(), key.size, value)
            val buffer = builder.buildByteBuffer(option)
            val serialized = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val raw = ByteArray(serialized.size + 7)
            serialized.copyInto(raw, 7)
            val j = JavaTrie(raw, 7)
            val k = BytesTrie(raw, 7)
            compare(j, k)
            for ((key, _) in entries) {
                j.reset(); k.reset()
                for (unit in key) {
                    assertEquals(j.next(unit.toInt()).name, k.next(unit.toInt()).name)
                    compare(j, k)
                    val state = k.getState64()
                    val js = JavaTrie.State(); val ks = BytesTrie.State()
                    j.saveState(js); k.saveState(ks)
                    assertEquals(j.next(256).name, k.next(256).name)
                    j.resetToState(js); k.resetToState(ks)
                    assertEquals(state, k.getState64())
                }
                val savedJ = JavaTrie(j); val savedK = BytesTrie(k)
                compare(savedJ, savedK)
                j.reset(); k.reset()
                val input = key.toByteArray()
                assertEquals(j.next(input, 0, input.size).name, k.next(input, 0, input.size).name)
                compare(j, k)
                assertEquals(j.next(input, 2, 1).name, k.next(input, 2, 1).name)
                val tail = random.nextInt(256)
                assertEquals(j.first(tail).name, k.first(tail).name)
                compare(j, k)
                val state = k.getState64()
                j.next(0); k.next(0)
                j.resetToState64(state); k.resetToState64(state)
                compare(j, k)
            }
            assertFailsWith<IllegalArgumentException> { k.resetToState(BytesTrie.State()) }
            val other = BytesTrie(raw.copyOf(), 7)
            val state = BytesTrie.State(); k.saveState(state)
            assertFailsWith<IllegalArgumentException> { other.resetToState(state) }
        }
    }
}
