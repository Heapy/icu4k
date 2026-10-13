package io.github.heapy.icu4k.util

import kotlin.test.*

class CharsTrieBuilderTest {
    @Test fun buildBothOptionsWithPrefixesAndSharedSuffixes() {
        for (option in StringTrieBuilder.Option.entries) {
            val builder = CharsTrieBuilder()
            val entries = mapOf("abcde" to 1, "abc" to 2, "ab" to 3, "ax" to -1, "" to Int.MIN_VALUE, "zbcde" to 1)
            for ((key, value) in entries) builder.add(key, value)
            val trie = builder.build(option)
            for ((key, value) in entries) {
                assertTrue(trie.reset().next(key, 0, key.length).hasValue())
                assertEquals(value, trie.getValue())
            }
            val serialized = builder.buildCharSequence(option)
            assertEquals(serialized.toString(), serialized.subSequence(0, serialized.length).toString())
            val copy = serialized.toString()
            builder.clear().add("x", 4).build(option)
            assertEquals(copy, serialized.toString())
        }
    }
    @Test fun lifecycleErrorsAndRecovery() {
        val builder = CharsTrieBuilder()
        assertFailsWith<IndexOutOfBoundsException> { builder.build(StringTrieBuilder.Option.FAST) }
        assertFailsWith<IndexOutOfBoundsException> { builder.add("x".repeat(65536), 1) }
        builder.add("abc", 1)
        assertFailsWith<IllegalArgumentException> { builder.add("abc", 2) }
        builder.build(StringTrieBuilder.Option.SMALL)
        assertFailsWith<IllegalStateException> { builder.add("x", 2) }
        builder.clear().add("x", 2)
        assertEquals(2, builder.build(StringTrieBuilder.Option.FAST).apply { first(120) }.getValue())
    }
}
