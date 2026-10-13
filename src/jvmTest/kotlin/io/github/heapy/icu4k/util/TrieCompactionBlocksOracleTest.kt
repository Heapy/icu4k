package io.github.heapy.icu4k.util

import kotlin.random.Random
import kotlin.test.*

class TrieCompactionBlocksOracleTest {
    private class Original(name: String) {
        private val type = Class.forName("com.ibm.icu.util.MutableCodePointTrie\$$name")
        private val instance = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        fun call(name: String, vararg args: Any): Any? {
            val types = args.map { if (it is Int) Int::class.javaPrimitiveType!! else it.javaClass }.toTypedArray()
            return type.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(instance, *args)
        }
    }
    @Test fun mixedBlockHashesAndProbesMatchOriginal() {
        val random = Random(833)
        val j = Original("MixedBlocks")
        val k = MutableCodePointTrie.MixedBlocks()
        for (capacity in listOf(2048, 8192, 40000, 150000, 2048)) {
            for (blockLength in listOf(4, 16, 32, 64)) {
                val data = IntArray(1024) { if (it in 300..399) -17 else random.nextInt() }
                j.call("init", capacity, blockLength); k.init(capacity, blockLength)
                for (limit in listOf(128, 512, 1024)) {
                    val previous = if (limit == 128) 0 else if (limit == 512) 128 else 512
                    j.call("extend", data, 3, previous, limit); k.extend(data, 3, previous, limit)
                    repeat(200) {
                        val start = random.nextInt(data.size - blockLength + 1)
                        assertEquals(j.call("findBlock", data, data, start), k.findBlock(data, data, start))
                    }
                    assertEquals(j.call("findAllSameBlock", data, -17), k.findAllSameBlock(data, -17))
                    val absent = IntArray(blockLength) { Int.MIN_VALUE }
                    assertEquals(j.call("findBlock", data, absent, 0), k.findBlock(data, absent, 0))
                }
                val chars = CharArray(1024) { random.nextInt(0x10000).toChar() }
                val ints = IntArray(chars.size) { chars[it].code }
                j.call("init", capacity, blockLength); k.init(capacity, blockLength)
                j.call("extend", chars, 0, 0, chars.size); k.extend(chars, 0, 0, chars.size)
                repeat(200) {
                    val start = random.nextInt(chars.size - blockLength + 1)
                    assertEquals(j.call("findBlock", chars, chars, start), k.findBlock(chars, chars, start))
                    assertEquals(j.call("findBlock", chars, ints, start), k.findBlock(chars, ints, start))
                }
            }
        }
    }
    @Test fun constantBlockCountsMatchOriginalAcrossOverflow() {
        val random = Random(114)
        val j = Original("AllSameBlocks"); val k = MutableCodePointTrie.AllSameBlocks()
        repeat(3000) { index ->
            val count = random.nextInt(1, 5)
            val value = random.nextInt(48)
            val expected = j.call("findOrAdd", index, count, value)
            assertEquals(expected, k.findOrAdd(index, count, value))
            if (expected == -2) { j.call("add", index, count, value); k.add(index, count, value) }
            assertEquals(j.call("findMostUsed"), k.findMostUsed())
        }
    }
}
