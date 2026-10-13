package io.github.heapy.icu4k.text

import kotlin.test.*

class RBBISymbolTableTest {
    @Test fun duplicateDefinitionRetainsOriginalAndMalformedSetReportsCode() {
        val errors = mutableListOf<Int>()
        val symbols = RBBISymbolTable { errors.add(it) }
        val expression = RBBINode(RBBINode.opCat).also { it.fText = "ab" }
        val variable = RBBINode(RBBINode.varRef).also { it.fLeftChild = expression }
        symbols.addEntry("v", variable)
        symbols.addEntry("v", RBBINode(RBBINode.varRef))
        assertSame(variable, symbols.lookupNode("v"))
        assertContentEquals("ab".toCharArray(), symbols.lookup("v"))
        assertEquals(listOf(0x10207, 0x1020f), errors)
        assertNull(symbols.lookupMatcher(0xffff))
        val pos = ParsePosition(0)
        assertEquals("", symbols.parseReference("1abc", pos, 4))
        assertEquals(0, pos.index)
        assertEquals("α2", symbols.parseReference("α2!", pos, 3))
        assertEquals(2, pos.index)
    }
}
