package io.github.heapy.icu4k.util

import kotlin.test.*

class BytesTrieBuilderTest {
    @Test fun unsignedByteAlphabetSurvivesCommonCharSequenceConversion() {
        for (option in StringTrieBuilder.Option.entries) {
            val builder = BytesTrieBuilder()
            for (i in 0..255) {
                builder.add(byteArrayOf(i.toByte()), 1, i - 128)
                builder.add(byteArrayOf(i.toByte(), 0), 2, Int.MIN_VALUE + i)
            }
            val reader = builder.build(option)
            for (i in 0..255) {
                assertTrue(reader.first(i).hasValue())
                assertEquals(i - 128, reader.getValue())
                assertEquals(BytesTrie.Result.FINAL_VALUE, reader.next(0))
                assertEquals(Int.MIN_VALUE + i, reader.getValue())
            }
        }
    }
    @Test fun buildsPrefixesAndSharedSuffixesBothOptions() {
        for (option in StringTrieBuilder.Option.entries) {
            val entries = mapOf("abcde" to 1, "abc" to 2, "ab" to 3, "ax" to -1, "" to Int.MIN_VALUE, "zbcde" to 1)
            val builder = BytesTrieBuilder()
            for ((key, value) in entries) builder.add(key.encodeToByteArray(), key.length, value)
            val reader = builder.build(option)
            val raw = builder.buildByteArray(option)
            for ((key, value) in entries) {
                assertTrue(reader.reset().next(key.encodeToByteArray(), 0, key.length).hasValue())
                assertEquals(value, reader.getValue())
                assertEquals(value, BytesTrie(raw, 0).apply { next(key.encodeToByteArray(), 0, key.length) }.getValue())
            }
            builder.clear().add(byteArrayOf(-1), 1, 5).build(option)
            assertEquals(1, reader.reset().apply { next("abcde".encodeToByteArray(), 0, 5) }.getValue())
        }
    }
    @Test fun lifecycleAndInputCopiedDuringAdd() {
        val builder = BytesTrieBuilder()
        assertFailsWith<IndexOutOfBoundsException> { builder.build(StringTrieBuilder.Option.FAST) }
        assertFailsWith<IndexOutOfBoundsException> { builder.add(ByteArray(65536), 65536, 1) }
        val key = byteArrayOf(1, 2)
        builder.add(key, 2, 3)
        key[0] = 9
        assertFailsWith<IllegalArgumentException> { builder.add(byteArrayOf(1, 2), 2, 4) }
        assertEquals(3, builder.build(StringTrieBuilder.Option.SMALL).apply { next(byteArrayOf(1, 2), 0, 2) }.getValue())
        assertFailsWith<IllegalStateException> { builder.add(byteArrayOf(4), 1, 9) }
    }
}
