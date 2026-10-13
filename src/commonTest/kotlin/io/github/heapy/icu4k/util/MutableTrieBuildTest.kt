package io.github.heapy.icu4k.util

import kotlin.test.*

class MutableTrieBuildTest {
    @Test fun buildsEveryTypeAndWidthAndResetsBuilder() {
        for (type in CodePointTrie.Type.entries) for (width in CodePointTrie.ValueWidth.entries) {
            val builder = MutableCodePointTrie(0x1234, -1)
            builder.setRange(65, 90, 0xa581)
            builder.set(0x10ffff, 0x5678)
            val mask = when (width) { CodePointTrie.ValueWidth.BITS_8 -> 255; CodePointTrie.ValueWidth.BITS_16 -> 65535; else -> -1 }
            val trie = builder.buildImmutable(type, width)
            assertEquals(0xa581 and mask, trie[65])
            assertEquals(0x1234 and mask, trie[64])
            assertEquals(0x5678 and mask, trie[0x10ffff])
            assertEquals(-1 and mask, trie[-1])
            assertEquals(0x1234, builder[65])
            val decoded = CodePointTrie.fromBinary(type, width, trie.toBinary())
            assertEquals(trie[65], decoded[65])
            builder.set(65, 1)
            assertEquals(0xa581 and mask, trie[65])
        }
    }
    @Test fun nullArgumentsDoNotClearBuilder() {
        val builder = MutableCodePointTrie(0, -1)
        builder.set(65, 3)
        assertFailsWith<IllegalArgumentException> { builder.buildImmutable(null, CodePointTrie.ValueWidth.BITS_32) }
        assertEquals(3, builder[65])
        assertFailsWith<IllegalArgumentException> { builder.buildImmutable(CodePointTrie.Type.FAST, null) }
        assertEquals(3, builder[65])
    }
}
