package io.github.heapy.icu4k.util

import com.ibm.icu.util.BytesTrieBuilder as JavaBuilder
import com.ibm.icu.util.StringTrieBuilder as JavaBase
import kotlin.random.Random
import kotlin.test.*

class BytesTrieBuilderOracleTest {
    @Test fun exactSerializationAndLifecycleMatchIcu() {
        val random = Random(683)
        val values = intArrayOf(-1, Int.MIN_VALUE, Int.MAX_VALUE, 0, 64, 65, 0x1aff, 0x1b00, 0x11ffff, 0x120000, 0xffffff, 0x1000000)
        for (option in StringTrieBuilder.Option.entries) repeat(40) { round ->
            val entries = linkedMapOf(emptyList<Byte>() to values[round % values.size])
            listOf("abcde", "abc", "ab", "abcd", "abf", "ax", "abcdff", "a", "z", "zx", "zxy").forEachIndexed { i, key -> entries[key.encodeToByteArray().toList()] = values[i % values.size] }
            repeat(500) {
                val key = List(random.nextInt(1, 120)) { if (round % 3 == 0) random.nextInt(10).toByte() else random.nextInt(256).toByte() }
                entries[key] = if (round % 2 == 0) 7 else values[random.nextInt(values.size)]
            }
            val j = JavaBuilder(); val k = BytesTrieBuilder()
            val order = entries.keys.shuffled(random)
            for (key in order) { j.add(key.toByteArray(), key.size, entries[key]!!); assertSame(k, k.add(key.toByteArray(), key.size, entries[key]!!)) }
            assertFailsWith<IllegalArgumentException> { j.add(order[0].toByteArray(), order[0].size, 5) }
            assertFailsWith<IllegalArgumentException> { k.add(order[0].toByteArray(), order[0].size, 5) }
            val expectedBuffer = j.buildByteBuffer(JavaBase.Option.valueOf(option.name))
            val actualBuffer = k.buildByteBuffer(option)
            assertEquals(expectedBuffer.position(), actualBuffer.position)
            assertEquals(expectedBuffer.capacity(), actualBuffer.capacity)
            val expected = ByteArray(expectedBuffer.remaining()).also { expectedBuffer.get(it) }
            assertContentEquals(expected, actualBuffer.readBytes(actualBuffer.remaining))
            assertContentEquals(expected, k.buildByteArray(if (option == StringTrieBuilder.Option.FAST) StringTrieBuilder.Option.SMALL else StringTrieBuilder.Option.FAST))
            assertFailsWith<IllegalStateException> { k.add(byteArrayOf(9), 1, 3) }
            val reader = k.build(option)
            for ((key, value) in entries) {
                assertTrue(reader.reset().next(key.toByteArray(), 0, key.size).hasValue())
                assertEquals(value, reader.getValue())
            }
            assertSame(k, k.clear())
            k.add(byteArrayOf(9), 1, 9)
            assertEquals(9, k.build(option).apply { first(9) }.getValue())
            assertEquals(entries[order[0]], reader.reset().apply { next(order[0].toByteArray(), 0, order[0].size) }.getValue())
        }
    }
    @Suppress("DEPRECATION")
    @Test fun deltaEncodingAllWidthsMatchesIcu() {
        for (delta in listOf(0, 1, 0xbf, 0xc0, 0x2fff, 0x3000, 0xdffff, 0xe0000, 0xffffff, 0x1000000, Int.MAX_VALUE)) {
            val j = ByteArray(5); val k = ByteArray(5)
            assertEquals(JavaBuilder.internalEncodeDelta(delta, j), BytesTrieBuilder.internalEncodeDelta(delta, k))
            assertContentEquals(j, k)
        }
    }
}
