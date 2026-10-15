// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

class CollationNodeGraphTest {
    @Test fun temporaryEncodingRoundTripsAllIndexBits() {
        for(index in listOf(0,1,63,64,8191,8192,0x7ffff,0xfffff)) for(strength in 0..3) {
            val ce=CollationNodeGraph.tempCEFromIndexAndStrength(index,strength)
            assertEquals(index,CollationNodeGraph.indexFromTempCE(ce));assertEquals(strength,CollationNodeGraph.ceStrength(ce))
            val ce32=CollationDataBuilder.encodeOneCEAsCE32(ce)
            assertTrue(CollationNodeGraph.isTempCE32(ce32));assertEquals(index,CollationNodeGraph.indexFromTempCE32(ce32))
        }
    }
    @Test fun stableIndexesLinksAndAssignedWeights() {
        val graph=CollationNodeGraph(CollationRoot.getData());val rootCE=CollationRoot.getData().getSingleCE('a'.code)
        val root=graph.findOrInsertNodeForRootCE(rootCE,0);val first=graph.insertTailoredNodeAfter(root,0);val second=graph.insertTailoredNodeAfter(first,0)
        val secondary=graph.insertTailoredNodeAfter(first,1);val tertiary=graph.insertTailoredNodeAfter(secondary,2)
        assertEquals(second,CollationNodeGraph.nextIndexFromNode(graph.nodes.elementAti(tertiary)))
        assertEquals(tertiary,CollationNodeGraph.previousIndexFromNode(graph.nodes.elementAti(second)))
        graph.makeTailoredCEs()
        val a=graph.nodes.elementAti(first);val b=graph.nodes.elementAti(second)
        assertTrue((a ushr 32)>(rootCE ushr 32));assertTrue((b ushr 32)>(a ushr 32))
        assertEquals(a ushr 32,graph.nodes.elementAti(secondary) ushr 32)
        assertTrue((graph.nodes.elementAti(secondary).toInt() ushr 16)>Collation.COMMON_WEIGHT16)
        assertTrue((graph.nodes.elementAti(tertiary).toInt() and 0x3fff)>Collation.COMMON_WEIGHT16)
    }
    @Test fun belowCommonNodesAndQuaternaryOverflow() {
        val g=CollationNodeGraph(CollationRoot.getData());val r=g.findOrInsertNodeForRootCE(CollationRoot.getData().getSingleCE('a'.code),0)
        val low=g.findOrInsertWeakNode(r,Collation.BEFORE_WEIGHT16,1);val common=g.findCommonNode(r,1)
        assertNotEquals(r,common);assertTrue(CollationNodeGraph.nodeHasBefore2(g.nodes.elementAti(r)))
        assertEquals(common,CollationNodeGraph.nextIndexFromNode(g.nodes.elementAti(low)))
        val q=CollationNodeGraph(CollationRoot.getData());var index=q.findOrInsertNodeForRootCE(CollationRoot.getData().getSingleCE('a'.code),2)
        repeat(4) { index=q.insertTailoredNodeAfter(index,3) }
        assertFailsWith<UnsupportedOperationException> { q.makeTailoredCEs() }
    }
}
