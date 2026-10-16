package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class OlsonTimeZoneTest {
    @Test fun historicalOffsetsTransitionsAndLocalGapsUseIcuData() {
        val zone = OlsonTimeZone("America/New_York")
        val offsets = IntArray(2)
        zone.getOffset(0, false, offsets); assertContentEquals(intArrayOf(-18000000, 0), offsets)
        zone.getOffset(1710054000000L, false, offsets); assertContentEquals(intArrayOf(-18000000, 3600000), offsets)
        val transition = zone.getNextTransition(1704067200000L, false)!!
        assertEquals(1710054000000L, transition.getTime())
        assertEquals(0, transition.getFrom()!!.getDSTSavings()); assertEquals(3600000, transition.getTo()!!.getDSTSavings())
        val gap = 1710037800000L // 2024-03-10 02:30 as a local-wall timestamp.
        zone.getOffsetFromLocal(gap, BasicTimeZone.LocalOption.FORMER, BasicTimeZone.LocalOption.FORMER, offsets)
        assertContentEquals(intArrayOf(-18000000, 0), offsets)
        zone.getOffsetFromLocal(gap, BasicTimeZone.LocalOption.LATTER, BasicTimeZone.LocalOption.LATTER, offsets)
        assertContentEquals(intArrayOf(-18000000, 3600000), offsets)
    }
    @Test fun frozenCloneAndRenamedCanonicalIdKeepIndependentState() {
        val original = OlsonTimeZone("US/Eastern")
        original.getTimeZoneRules(); original.freeze()
        assertSame(original, original.clone())
        val copy = original.cloneAsThawed()
        assertFalse(copy.isFrozen()); assertTrue(original.isFrozen()); assertTrue(original.hasSameRules(copy))
        copy.setID("custom"); assertEquals("custom", copy.getID()); assertEquals("US/Eastern", original.getID())
        assertEquals("America/New_York", copy.getCanonicalID())
        val raw = original.getRawOffset(); copy.setRawOffset(raw + 123000)
        assertEquals(raw, original.getRawOffset()); assertEquals(raw + 123000, copy.getRawOffset())
        assertFailsWith<UnsupportedOperationException> { original.setID("changed") }
        assertFailsWith<UnsupportedOperationException> { original.setRawOffset(0) }
    }
    private class Tagged : OlsonTimeZone {
        var tag: Int
        constructor(id: String, tag: Int) : super(id) { this.tag = tag }
        private constructor(other: Tagged) : super(other, Unit) { tag = other.tag }
        override fun cloneNative(): OlsonTimeZone = Tagged(this)
    }
    @Test fun nativeSubclassCopyHookRetainsAdditionalState() {
        val original = Tagged("Europe/Minsk", 42)
        val copy = original.cloneAsThawed()
        assertIs<Tagged>(copy); assertEquals(42, copy.tag)
        copy.tag = 7; copy.setID("changed")
        assertEquals(42, original.tag); assertEquals("Europe/Minsk", original.getID())
    }
    private class DispatchZone : OlsonTimeZone {
        constructor() : super("Europe/Minsk")
        private constructor(other: DispatchZone) : super(other, Unit)
        override fun getOffset(date: Long, local: Boolean, offsets: IntArray?) {
            offsets!![0] = if (getID() == "copy") 123000 else 456000; offsets[1] = 0
        }
        override fun cloneNative(): OlsonTimeZone = DispatchZone(this)
    }
    @Test fun clonedOwnerDispatchReachesCopy() {
        val zone = DispatchZone()
        val copy = zone.cloneAsThawed(); copy.setID("copy")
        assertEquals(123000, copy.getRawOffset()); assertEquals(456000, zone.getRawOffset())
    }
}
