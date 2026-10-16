package io.github.heapy.icu4k.util

import kotlin.test.*

class SimpleTimeZoneTest {
    private fun zone() = SimpleTimeZone(-28800000, "test", 2, 2, 1, 7200000, 10, 1, 1, 7200000)
    @Test fun daylightQueriesUseCalendarStateAndTransitions() {
        val zone = zone()
        assertFalse(zone.inDaylightTime(Date(1704067200000L)))
        assertTrue(zone.inDaylightTime(Date(1719792000000L)))
        val transition = zone.getNextTransition(1704067200000L, false)!!
        assertEquals(1710064800000L, transition.getTime())
        val wall = transition.getTime() + transition.getFrom()!!.getRawOffset()
        val before = IntArray(2); val after = IntArray(2)
        zone.getOffsetFromLocal(wall, BasicTimeZone.LocalOption.FORMER, BasicTimeZone.LocalOption.FORMER, before)
        zone.getOffsetFromLocal(wall, BasicTimeZone.LocalOption.LATTER, BasicTimeZone.LocalOption.LATTER, after)
        assertEquals(0, before[1]); assertEquals(3600000, after[1])
        val calendar = GregorianCalendar(zone, ULocale.US)
        calendar.clear(); calendar.set(2024, 2, 10, 2, 30)
        assertEquals(3, calendar.get(Calendar.HOUR_OF_DAY))
        calendar.setLenient(false); calendar.clear(); calendar.set(2024, 2, 10, 2, 30)
        assertFailsWith<IllegalArgumentException> { calendar.getTimeInMillis() }
    }
    @Test fun clonesRebindOwnersAndFrozenCopiesAreIndependent() {
        val zone = zone(); zone.getNextTransition(0, false)
        val copy = zone.clone()
        assertNotSame(zone, copy); assertEquals(zone, copy)
        copy.setRawOffset(0); copy.setID("copy")
        assertEquals(-28800000, zone.getRawOffset()); assertEquals("test", zone.getID())
        assertEquals(3600000, copy.getOffset(1719792000000L))
        zone.freeze(); assertSame(zone, zone.clone())
        assertFailsWith<UnsupportedOperationException> { zone.setID(null) }
        val thawed = zone.cloneAsThawed(); assertFalse(thawed.isFrozen()); thawed.setID("thawed")
        assertTrue(zone.hasSameRules(thawed)); assertNotEquals(zone, thawed)
    }
}
