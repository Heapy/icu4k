package io.github.heapy.icu4k.util

import com.ibm.icu.util.CharsTrieBuilder as JavaBuilder
import com.ibm.icu.util.StringTrieBuilder as JavaBase
import kotlin.random.Random
import kotlin.test.*

class CharsTrieBuilderOracleTest {
    @Test fun exactSerializationAndLifecycleMatchIcu() {
        val random = Random(383)
        val values = intArrayOf(-1, Int.MIN_VALUE, Int.MAX_VALUE, 0, 255, 256, 0x3fff, 0x4000, 0x3ffeffff, 0x3fff0000, 0xfdffff, 0xfe0000)
        for (option in StringTrieBuilder.Option.entries) repeat(40) { round ->
            val entries = linkedMapOf("" to values[round % values.size])
            listOf("abcde", "abc", "ab", "abcd", "abf", "ax", "abcdff", "a", "z", "zx", "zxy").forEachIndexed { i, key -> entries[key] = values[i % values.size] }
            repeat(500) {
                val key = CharArray(random.nextInt(1, 120)) { if (round % 3 == 0) random.nextInt(10).toChar() else random.nextInt(0x10000).toChar() }.concatToString()
                entries[key] = if (round % 2 == 0) 7 else values[random.nextInt(values.size)]
            }
            val j = JavaBuilder(); val k = CharsTrieBuilder()
            val order = entries.keys.shuffled(random)
            for (key in order) { j.add(key, entries[key]!!); assertSame(k, k.add(key, entries[key]!!)) }
            assertFailsWith<IllegalArgumentException> { j.add(order[0], 5) }
            assertFailsWith<IllegalArgumentException> { k.add(order[0], 5) }
            val jo = JavaBase.Option.valueOf(option.name)
            val expected = j.buildCharSequence(jo).toString()
            val actual = k.buildCharSequence(option)
            assertEquals(expected, actual.toString(), "$option $round")
            assertEquals(expected, k.buildCharSequence(if (option == StringTrieBuilder.Option.FAST) StringTrieBuilder.Option.SMALL else StringTrieBuilder.Option.FAST).toString())
            assertFailsWith<IllegalStateException> { k.add("new", 3) }
            val reader = k.build(option)
            for ((key, value) in entries) {
                assertTrue(reader.reset().next(key, 0, key.length).hasValue())
                assertEquals(value, reader.getValue())
            }
            assertSame(k, k.clear())
            k.add("replacement", 9)
            assertEquals(9, k.build(option).apply { next("replacement", 0, 11) }.getValue())
            assertEquals(expected, actual.toString()) // Previously returned view survives clear.
        }
    }
    @Test fun largeBranchesLongKeysAndValueWidthsMatch() {
        for (option in StringTrieBuilder.Option.entries) {
            val j = JavaBuilder(); val k = CharsTrieBuilder()
            for (i in 0..4095) {
                val key = i.toChar().toString() + "shared-suffix".repeat(4)
                j.add(key, 7); k.add(key, 7)
            }
            j.add("long".repeat(500), Int.MIN_VALUE); k.add("long".repeat(500), Int.MIN_VALUE)
            assertEquals(j.buildCharSequence(JavaBase.Option.valueOf(option.name)).toString(), k.buildCharSequence(option).toString())
        }
    }
}
