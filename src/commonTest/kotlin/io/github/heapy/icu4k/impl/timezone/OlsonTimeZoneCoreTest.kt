package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.Date
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.test.*

class OlsonTimeZoneCoreTest {
    private fun time(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Grego.fieldsToDay(year,month,day) * 86_400_000L + hour * 3_600_000L + minute * 60_000L
    @Test fun newYorkHistoryAndFinalRulesUseOneEngine() {
        val zone = OlsonTimeZoneCore("America/New_York", clock = { time(2025,6,1) })
        val offsets = IntArray(2)
        zone.getOffset(time(1880,0,1),false,offsets)
        assertContentEquals(intArrayOf(-17_762_000,0),offsets)
        zone.getOffset(time(2025,2,9,6,59),false,offsets)
        assertContentEquals(intArrayOf(-18_000_000,0),offsets)
        zone.getOffset(time(2025,2,9,7),false,offsets)
        assertContentEquals(intArrayOf(-18_000_000,3_600_000),offsets)
        assertTrue(zone.useDaylightTime()); assertTrue(zone.observesDaylightTime())
        assertEquals(-18_000_000,zone.getRawOffset())
        assertTrue(zone.inDaylightTime(Date(time(2025,6,1))))
        assertEquals(time(2025,2,9,7),zone.getNextTransition(time(2025,0,1),false)?.getTime())
        assertEquals(time(2025,2,9,7),zone.getPreviousTransition(time(2025,2,9,7),true)?.getTime())
        assertEquals(3,zone.basicCore().getSimpleTimeZoneRulesNear(time(2025,0,1)).size)
        assertTrue(zone.basicCore().getTimeZoneRules(time(2025,0,1))!!.size >= 3)
    }
    @Test fun apiaDateLineGapHandlesRawAndDstOffsets() {
        val zone = OlsonTimeZoneCore("Pacific/Apia")
        val wall = time(2011,11,30,12)
        val offsets = IntArray(2)
        zone.getOffsetFromLocal(wall,4,12,offsets)
        assertEquals(-36_000_000,offsets[0]+offsets[1])
        zone.getOffsetFromLocal(wall,12,12,offsets)
        assertEquals(50_400_000,offsets[0]+offsets[1])
        zone.getOffset(time(2011,11,30,10),false,offsets)
        assertEquals(50_400_000,offsets[0]+offsets[1])
    }
    @Test fun clonesShareHistoricRuleCachesAndPreserveOriginalCanonicalId() {
        val zone = OlsonTimeZoneCore("US/Eastern")
        val rules = zone.getTimeZoneRules()
        val copy = zone.cloneAsThawed()
        assertEquals(zone,copy); assertEquals(zone.hashCode(),copy.hashCode())
        assertSame(rules[0],copy.getTimeZoneRules()[0])
        copy.setID("Renamed")
        assertEquals("America/New_York",copy.getCanonicalID())
        assertEquals("US/Eastern",zone.getID())
        assertNotSame(rules[0],copy.getTimeZoneRules()[0])
        assertTrue(zone.hasSameRules(copy))
        zone.freeze(); assertSame(zone,zone.clone())
        assertFalse(zone.cloneAsThawed().isFrozen())
        assertFailsWith<UnsupportedOperationException> { zone.setID(null) }
        assertFailsWith<UnsupportedOperationException> { zone.setRawOffset(0) }
    }
    @Test fun rawOffsetMutationRetainsSourceDayValuedBoundary() {
        val zone = OlsonTimeZoneCore("Europe/Minsk",clock = { time(2025,6,1) })
        val offsets = IntArray(2)
        zone.getOffset(0L,false,offsets)
        val old = offsets.copyOf()
        zone.setRawOffset(1337)
        zone.getOffset(0L,false,offsets)
        assertContentEquals(old,offsets)
        zone.getOffset(30_000L,false,offsets)
        assertContentEquals(intArrayOf(1337,0),offsets)
        assertEquals(1337,zone.getRawOffset())
    }
    @Test fun shortArraysAndInvalidFieldsFailPortably() {
        val zone = OlsonTimeZoneCore("Etc/GMT")
        val short = intArrayOf(7)
        assertFailsWith<IndexOutOfBoundsException> { zone.getOffset(0L,false,short) }
        assertEquals(0,short[0])
        assertFailsWith<NullPointerException> { zone.getOffset(0L,false,null) }
        assertFailsWith<IllegalArgumentException> { zone.getOffset(1,2025,12,1,1,0) }
        assertEquals(0,zone.getOffset(1,2025,1,31,1,0,31)) // Provided monthLength is honored.
        assertFailsWith<IllegalArgumentException> { zone.getOffset(1,2025,1,31,1,0) }
        assertFailsWith<IllegalArgumentException> { OlsonTimeZoneCore("Unknown/Zone") }
        assertFailsWith<NullPointerException> { OlsonTimeZoneCore(null as String?) }
        assertFailsWith<IllegalArgumentException> { OlsonTimeZoneCore(null as UResourceBundle?,null,"Test") }
    }

    @Test fun metadataFactoriesCacheFrozenPrototypesAndFilterThroughSystemEngine() {
        val zone = assertNotNull(ZoneMetaCore.getSystemTimeZoneCore("Europe/Minsk"))
        assertTrue(zone.isFrozen())
        assertSame(zone,ZoneMetaCore.getSystemTimeZoneCore("Europe/Minsk"))
        val raw = zone.getRawOffset()
        assertEquals(setOf("Europe/Minsk"),ZoneMetaCore.getAvailableIDs(ZoneMetaCore.SystemTimeZoneType.ANY,"BY",raw))
        assertNull(ZoneMetaCore.getSystemTimeZoneCore("Unknown/Zone"))
        assertNull(ZoneMetaCore.getSystemTimeZoneCore("Unknown/Zone"))
        val custom = assertNotNull(ZoneMetaCore.getCustomTimeZoneCore("GMT+530"))
        assertSame(custom,ZoneMetaCore.getCustomTimeZoneCore("GMT+05:30"))
        assertEquals("GMT+05:30",custom.getID()); assertEquals(19_800_000,custom.getRawOffset())
        assertTrue(custom.isFrozen())
        val seconds = assertNotNull(ZoneMetaCore.getCustomTimeZoneCore("GMT+000059"))
        assertEquals("GMT",seconds.getID()); assertEquals(59_000,seconds.getRawOffset())
        val byOffset = ZoneMetaCore.getCustomTimeZoneCore(59_999)
        assertEquals("GMT",byOffset.getID()); assertEquals(59_999,byOffset.getRawOffset())
        assertFalse(byOffset.isFrozen())
        assertNotSame(byOffset,ZoneMetaCore.getCustomTimeZoneCore(59_999))
    }
}
