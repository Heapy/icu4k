// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

class CollationCompareTest {
    private fun compare(a: LongArray,b: LongArray,strength: Int=2,flags: Int=0,top: Long=0): Int {
        val settings=CollationSettings().apply { options=(strength shl 12) or flags; variableTop=top }
        return CollationCompare.compareUpToQuaternary(compareFixtureIterator(a),compareFixtureIterator(b),settings)
    }
    @Test fun eachWeightLevelAndIgnorables() {
        val base=compareCE(0x30000000)
        assertEquals(-1,compare(longArrayOf(base),longArrayOf(compareCE(0x40000000)),0))
        assertEquals(0,compare(longArrayOf(base),longArrayOf(compareCE(0x30000000,0x600)),0))
        assertEquals(-1,compare(longArrayOf(base),longArrayOf(compareCE(0x30000000,0x600)),1))
        assertEquals(-1,compare(longArrayOf(base),longArrayOf(compareCE(0x30000000,0x500,0x600))))
        assertEquals(0,compare(longArrayOf(0,base),longArrayOf(base)))
        val q1=compareCE(0x30000000,0x500,0x540); val q2=compareCE(0x30000000,0x500,0x580)
        assertEquals(0,compare(longArrayOf(q1),longArrayOf(q2),2)); assertEquals(-1,compare(longArrayOf(q1),longArrayOf(q2),3))
        assertEquals(-1,compare(longArrayOf(),longArrayOf(base))); assertEquals(0,compare(longArrayOf(),longArrayOf()))
    }
    @Test fun backwardSecondarySegmentsAndCaseOrdering() {
        val a=longArrayOf(compareCE(0x30000000,0x500),compareCE(0x40000000,0x600))
        val b=longArrayOf(compareCE(0x30000000,0x600),compareCE(0x40000000,0x500))
        assertEquals(-1,compare(a,b,1)); assertEquals(1,compare(a,b,1,CollationSettings.BACKWARD_SECONDARY))
        val merge=Collation.ceFromSimpleCE32(Collation.MERGE_SEPARATOR_CE32)
        assertEquals(-1,compare(longArrayOf(a[0],merge,a[1]),longArrayOf(b[0],merge,b[1]),1,CollationSettings.BACKWARD_SECONDARY))
        val lower=longArrayOf(compareCE(0x30000000)); val upper=longArrayOf(compareCE(0x30000000,0x500,0x8500))
        assertEquals(0,compare(lower,upper)); assertEquals(-1,compare(lower,upper,0,CollationSettings.CASE_LEVEL))
        assertEquals(1,compare(lower,upper,0,CollationSettings.CASE_LEVEL or CollationSettings.UPPER_FIRST))
        assertEquals(-1,compare(lower,upper,2,CollationSettings.CASE_FIRST))
        assertEquals(1,compare(lower,upper,2,CollationSettings.CASE_FIRST_AND_UPPER_MASK))
        assertEquals(0,compare(longArrayOf(compareCE(0,0x600,0x8500),lower[0]),lower,0,CollationSettings.CASE_LEVEL))
    }
    @Test fun shiftedVariablesEraseFollowingPrimaryIgnorablesInTheBuffer() {
        val a=longArrayOf(compareCE(0x10000000),compareCE(0,0x600),compareCE(0x30000000))
        val b=longArrayOf(compareCE(0x30000000))
        val settings=CollationSettings().apply { options=0x2004; variableTop=0x20000000 }
        val left=compareFixtureIterator(a); val right=compareFixtureIterator(b)
        assertEquals(0,CollationCompare.compareUpToQuaternary(left,right,settings))
        assertEquals(0x1000000000000000L,left.getCE(0)); assertEquals(0,left.getCE(1))
        settings.setStrength(3)
        assertEquals(-1,CollationCompare.compareUpToQuaternary(compareFixtureIterator(a),compareFixtureIterator(b),settings))
        assertEquals(0,compare(a,b,2,CollationSettings.SHIFTED,0x20000000))
    }
    @Test fun scriptReorderingChangesPrimaryDifferences() {
        val settings=CollationSettings().apply { setReordering(scriptCollationData(),intArrayOf(14)) }
        assertEquals(1,CollationCompare.compareUpToQuaternary(compareFixtureIterator(longArrayOf(compareCE(0x20000000))),compareFixtureIterator(longArrayOf(compareCE(0x30000000))),settings))
    }
}
internal fun compareCE(primary: Long,secondary: Int=0x500,tertiary: Int=0x500): Long = (primary shl 32) or (secondary.toLong() shl 16) or tertiary.toLong()
internal fun compareFixtureData(weights: LongArray): CollationData = iteratorFixtureData().apply {
    ces=weights
    trie=iteratorFixtureTrie(mapOf('x'.code to Collation.makeCE32FromTagIndexAndLength(6,0,weights.size)))
}
internal fun compareFixtureIterator(weights: LongArray): UTF16CollationIterator = UTF16CollationIterator(compareFixtureData(weights),false,if (weights.isEmpty()) "" else "x",0)
