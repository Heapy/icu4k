package io.github.heapy.icu4k.text

import kotlin.test.*

class RBBIRuleScannerTest {
    @Test fun parsesVariablesChainingLookAheadAndTagTrees() {
        val result = RBBIRuleParseResult("!!chain; \$x=[a-z]; ^\$x+/b{123}; a;")
        val scanner = RBBIRuleScanner(result)
        scanner.parse()
        assertEquals(3, scanner.numRules()) // Assignments also increment the original rule counter.
        val root = assertNotNull(result.fTreeRoots[0])
        assertEquals(RBBINode.opOr, root.fType)
        assertTrue(root.fRightChild!!.fChainIn)
        assertFalse(root.fLeftChild!!.fChainIn)
        val tags = mutableListOf<RBBINode>()
        root.findNodes(tags, RBBINode.tag)
        assertEquals(listOf(123), tags.map { it.fVal })
        val ends = mutableListOf<RBBINode>()
        root.findNodes(ends, RBBINode.endMark)
        assertEquals(1, ends.size)
        assertTrue(ends.single().fLookAheadEnd)
        assertEquals(2, ends.single().fVal)
        assertNotNull(scanner.fSymbolTable.lookupNode("x"))
    }

    @Test fun errorLocationsAndCommentStrippingPreserveSourceOffsets() {
        val result = RBBIRuleParseResult("# comment\r\na; #end")
        RBBIRuleScanner(result).parse()
        assertEquals("         \r\na;    d", result.fStrippedRules.toString())
        val e = assertFailsWith<IllegalArgumentException> { RBBIRuleScanner(RBBIRuleParseResult("a;\n\$missing;")).parse() }
        assertEquals("Error 66058 at line 2 column 9", e.message)
        assertEquals("'ab';", RBBIRuleScanner.stripRules(" 'a b';\n"))
    }
}
