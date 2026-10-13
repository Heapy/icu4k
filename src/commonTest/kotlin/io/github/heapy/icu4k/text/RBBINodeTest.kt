package io.github.heapy.icu4k.text

import kotlin.test.*

class RBBINodeTest {
    @Test fun variableExpansionAndSharedSetsPreserveOriginalOwnership() {
        val leaf = RBBINode(RBBINode.leafChar).also { it.fVal = 42 }
        val variable = RBBINode(RBBINode.varRef).also { it.fLeftChild = leaf; it.fRuleRoot = true; it.fChainIn = true }
        val root = RBBINode(RBBINode.opCat).also { it.fLeftChild = variable }
        assertSame(root, root.flattenVariables(0))
        val expanded = assertNotNull(root.fLeftChild)
        assertNotSame(leaf, expanded); assertEquals(42, expanded.fVal)
        assertSame(root, expanded.fParent); assertTrue(expanded.fRuleRoot); assertTrue(expanded.fChainIn)
        val set = RBBINode(RBBINode.uset).also { it.fInputSet = UnicodeSet("[a-z]") }
        assertSame(set, set.cloneTree())
        val copy = RBBINode(set)
        assertSame(set.fInputSet, copy.fInputSet)
        assertNotSame(set.fFirstPosSet, copy.fFirstPosSet)
        assertFailsWith<IllegalArgumentException> { root.flattenVariables(3501) }
        assertFailsWith<IllegalStateException> { RBBINode(RBBINode.nodeTypeLimit) }
    }
}
