package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.CodePointTrie
import kotlin.test.*

class RBBISetBuilderTest {
    @Test fun partitionsDictionaryRangesAndSpecialBoundarySymbols() {
        val result = RBBIRuleParseResult("\$dictionary=[b-d]; [a-c{bof}{eof}]; \$dictionary; .;")
        RBBIRuleScanner(result).parse()
        val builder = RBBISetBuilder(result)
        builder.buildRanges()
        assertTrue(builder.sawBOF())
        assertEquals(-1, builder.getFirstChar(1))
        assertEquals(-1, builder.getFirstChar(2))
        var previous = -1
        var range = builder.fRangeList
        while (range != null) {
            assertEquals(previous + 1, range.fStartChar)
            assertEquals(range.isDictionaryRange(), range.fNum >= builder.getDictCategoriesStart())
            previous = range.fEndChar
            range = range.fNext
        }
        assertEquals(0x10ffff, previous)
        builder.buildTrie()
        val trie = assertNotNull(builder.fTrie)
        assertTrue(trie.get('a'.code) < builder.getDictCategoriesStart())
        assertTrue(trie.get('b'.code) >= builder.getDictCategoriesStart())
        assertEquals(0, trie.get(-1))
        val bytes = builder.serializeTrie()
        assertEquals(bytes.size, builder.getTrieSize())
        assertNull(builder.fTrie)
        assertEquals(CodePointTrie.ValueWidth.BITS_8, builder.fFrozenTrie!!.getValueWidth())
        assertContentEquals(bytes, builder.serializeTrie())
    }

    @Test fun splittingCopiesMembershipButKeepsIndependentContainers() {
        val range = RBBISetBuilder.RangeDescriptor().also { it.fEndChar = 100; it.fNum = 7; it.fIncludesDict = true; it.fFirstInGroup = true }
        val set = RBBINode(RBBINode.uset)
        range.fIncludesSets.add(set)
        range.split(50)
        val next = assertNotNull(range.fNext)
        assertEquals(49, range.fEndChar); assertEquals(50, next.fStartChar); assertEquals(100, next.fEndChar)
        assertEquals(7, next.fNum); assertTrue(next.fIncludesDict); assertTrue(next.fFirstInGroup)
        assertSame(set, next.fIncludesSets.single())
        next.fIncludesSets.clear()
        assertSame(set, range.fIncludesSets.single())
        assertFailsWith<IllegalStateException> { range.split(0) }
    }
}
