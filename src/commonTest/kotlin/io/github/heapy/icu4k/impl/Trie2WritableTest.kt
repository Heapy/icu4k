// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import kotlin.test.*

class Trie2WritableTest {
    @Test fun rangesAlternateLeadUnitsAndBothFrozenWidths() {
        val t=Trie2Writable(3,-7)
        assertSame(t,t.setRange(0x100,0x2ff,10,true));t.setRange(0x200,0x3ff,20,false)
        assertEquals(10,t[0x200]);assertEquals(20,t[0x300]);assertEquals(3,t[0x400])
        t.set(0xd800,123);t.setForLeadSurrogateCodeUnit(0xd800.toChar(),456);t.set(0x1f600,0x12345678)
        for(frozen in listOf(t.toTrie2_16(),t.toTrie2_32())) {
            assertEquals(123,frozen[0xd800]);assertEquals(456,frozen.getFromU16SingleLead(0xd800.toChar()))
            assertEquals(if(frozen is Trie2_16) 0x5678 else 0x12345678,frozen[0x1f600])
            assertEquals(-7,frozen[-1]);assertEquals(3,frozen[0x10ffff])
            val read=Trie2.createFromSerialized(frozen.serialize());assertEquals(frozen[0x1f600],read[0x1f600])
        }
    }
    @Test fun copyingAndMutationAfterFreezeLeaveSnapshotsIndependent() {
        val t=Trie2Writable(0,-1).set(65,7);val frozen=t.toTrie2_32()
        val copy=Trie2Writable(frozen);copy.set(65,8);copy.setForLeadSurrogateCodeUnit(0xd812.toChar(),9)
        assertEquals(7,t[65]);assertEquals(8,copy[65]);assertEquals(0,frozen.getFromU16SingleLead(0xd812.toChar()))
        t.set(66,12);assertEquals(12,t[66]);assertEquals(0,frozen[66]);assertEquals(7,t[65])
        assertFailsWith<IllegalArgumentException> { t.set(-1,5) }
        assertFailsWith<IllegalArgumentException> { t.setRange(10,9,0,true) }
    }
    @Test fun rangeObjectsAndHashInvalidation() {
        val t=Trie2Writable(0,1);val old=t.hashCode();t.set(70,2);assertNotEquals(old,t.hashCode())
        val range=Trie2.Range().apply { startCodePoint=0xd800;endCodePoint=0xd802;value=5;leadSurrogate=true }
        t.setRange(range,true)
        assertEquals(0,t[0xd800]);assertEquals(5,t.getFromU16SingleLead(0xd802.toChar()))
        val snapshot=t.toTrie2_32();assertEquals(t.hashCode(),snapshot.hashCode());assertEquals<Trie2>(t,snapshot)
    }
}
