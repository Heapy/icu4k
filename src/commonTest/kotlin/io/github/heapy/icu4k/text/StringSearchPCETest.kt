// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class StringSearchPCETest {
    @Test fun processedOffsetsAndLongBackwardBuffers() {
        val c=RuleBasedCollator("")
        val text="a"+"\u0301".repeat(70)+"😀 b"
        val it=c.getCollationElementIterator(text); val p=StringSearchPCE(it)
        val range=StringSearchPCE.Range()
        val forward=buildList { while(true) { val ce=p.nextProcessed(range); if(ce==-1L) break; add(listOf(ce,range.ixLow_.toLong(),range.ixHigh_.toLong())) } }
        it.reset(); p.init(it)
        val reverse=buildList { while(true) { val ce=p.previousProcessed(range); if(ce==-1L) break; add(ce) } }
        assertEquals(forward.map { it[0] }.reversed(),reverse)
        assertEquals(-1,range.ixLow_); assertEquals(-1,range.ixHigh_)
        assertTrue(forward.all { it[1] in 0..text.length.toLong() && it[2] in 0..text.length.toLong() })
    }
    @Test fun circularHistoryAliasesAndWildcardWeights() {
        val p=StringSearchPCE(RuleBasedCollator("").getCollationElementIterator("abcdefghijklmnopqrstuvwxyz".repeat(4)))
        val b=StringSearchCEBuffer(1,"a",SearchIterator.ElementComparisonType.STANDARD_ELEMENT_COMPARISON,{ true },{ p },false)
        val first=b.get(0)
        for(i in 1..33) b.get(i)
        assertEquals(2,b.firstIx_); assertEquals(34,b.limitIx_)
        assertSame(first,b.get(33)) // Physical slot 0 has been reused.
        assertNull(b.get(0)); assertNull(b.get(35))
        assertSame(b.get(32),b.getPrevious(32))
        val pat=(0x1234L shl 48) or (5L shl 32) or (5L shl 16)
        val marked=(0x1234L shl 48) or (9L shl 32) or (5L shl 16)
        assertEquals(0,StringSearchPCE.compareCE64s(marked,pat,SearchIterator.ElementComparisonType.STANDARD_ELEMENT_COMPARISON))
        assertEquals(-1,StringSearchPCE.compareCE64s(marked,pat,SearchIterator.ElementComparisonType.PATTERN_BASE_WEIGHT_IS_WILDCARD))
        assertEquals(0,StringSearchPCE.compareCE64s(pat,marked,SearchIterator.ElementComparisonType.PATTERN_BASE_WEIGHT_IS_WILDCARD))
        assertEquals(-1,StringSearchPCE.compareCE64s(pat,marked,SearchIterator.ElementComparisonType.ANY_BASE_WEIGHT_IS_WILDCARD))
    }
}
