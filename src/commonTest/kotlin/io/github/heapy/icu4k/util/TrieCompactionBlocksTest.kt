package io.github.heapy.icu4k.util

import kotlin.test.*

class TrieCompactionBlocksTest {
    @Test fun mixedBlocksTrackAppendAndReset() {
        val blocks = MutableCodePointTrie.MixedBlocks()
        val data = intArrayOf(1, 2, 3, 4, 1, 2, 3, 4, 8, 8, 8, 8)
        blocks.init(64, 4)
        blocks.extend(data, 0, 0, 8)
        assertEquals(0, blocks.findBlock(data, intArrayOf(1, 2, 3, 4), 0))
        assertEquals(-1, blocks.findAllSameBlock(data, 8))
        blocks.extend(data, 0, 8, 12)
        assertEquals(8, blocks.findAllSameBlock(data, 8))
        blocks.init(64, 4)
        assertEquals(-1, blocks.findBlock(data, data, 0))
        val chars = CharArray(data.size) { data[it].toChar() }
        blocks.extend(chars, 0, 0, chars.size)
        assertEquals(0, blocks.findBlock(chars, data, 0))
        assertEquals(0, blocks.findBlock(chars, chars, 4))
    }
    @Test fun constantBlockCountsChooseMostUsedAndEvictLeastUsed() {
        val blocks = MutableCodePointTrie.AllSameBlocks()
        assertEquals(-1, blocks.findMostUsed())
        for (i in 0 until 32) assertEquals(-1, blocks.findOrAdd(i * 16, i + 1, i))
        assertEquals(31 * 16, blocks.findMostUsed())
        assertEquals(-2, blocks.findOrAdd(512, 100, 99))
        blocks.add(512, 100, 99)
        assertEquals(512, blocks.findMostUsed())
        assertEquals(512, blocks.findOrAdd(999, 1, 99))
        assertEquals(-2, blocks.findOrAdd(1000, 1, 0))
    }
}
