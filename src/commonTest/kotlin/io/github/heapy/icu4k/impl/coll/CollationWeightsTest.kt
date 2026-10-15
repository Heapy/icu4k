// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

class CollationWeightsTest {
    @Test fun primaryShortWeightsAndExhaustion() {
        val w=CollationWeights();w.initForPrimary(false)
        assertEquals(0xffffffffL,w.nextWeight())
        assertTrue(w.allocWeights(0x10000000,0x14000000,3))
        assertEquals(0x11000000,w.nextWeight());assertEquals(0x12000000,w.nextWeight());assertEquals(0x13000000,w.nextWeight())
        repeat(3) { assertEquals(0xffffffffL,w.nextWeight()) }
        assertFalse(w.allocWeights(0x10000000,0x10020000,1))
        assertFalse(w.allocWeights(0x14000000,0x10000000,1))
    }
    @Test fun primaryCompressionAndLowerLevelByteBounds() {
        for(mode in 0..3) {
            val w=CollationWeights()
            when(mode) { 0 -> w.initForPrimary(false);1 -> w.initForPrimary(true);2 -> w.initForSecondary();else -> w.initForTertiary() }
            val lower=if(mode==0) 0x10020000L else if(mode==1) 0x10040000L else 0x200L
            val upper=if(mode<2) 0x11000000L else 0x800L
            val count=if(mode==3) 100 else 600
            assertTrue(w.allocWeights(lower,upper,count))
            var previous=lower
            repeat(count) {
                val next=w.nextWeight()
                assertTrue(next>previous && next<upper,"mode=$mode, weight=$next")
                val length=CollationWeights.lengthOfWeight(next)
                for(index in (if(mode<2) 2 else 3)..length) {
                    val b=((next shr (8*(4-index))) and 255).toInt()
                    val min=if(mode==1 && index==2) 4 else 2
                    val max=if(mode==3) 63 else if(mode==1 && index==2) 254 else 255
                    assertTrue(b in min..max,"mode=$mode byte=$index/$b")
                }
                previous=next
            }
        }
    }
    @Test fun lengthsDependOnLowBytesIncludingOutOfDomainLongs() {
        assertEquals(1,CollationWeights.lengthOfWeight(0))
        assertEquals(1,CollationWeights.lengthOfWeight(Long.MIN_VALUE))
        assertEquals(2,CollationWeights.lengthOfWeight(0x12340000))
        assertEquals(3,CollationWeights.lengthOfWeight(0x12345600))
        assertEquals(4,CollationWeights.lengthOfWeight(-1))
    }
}
