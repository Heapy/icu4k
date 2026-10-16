package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.Grego
import kotlin.test.*

class SimpleTimeZoneCoreTest {
    private fun us() = SimpleTimeZoneCore(-18_000_000, "Test", 2, 2, 1, 7_200_000, 0, 10, 1, 1, 7_200_000, 0).also { it.setStartYear(2007) }
    private fun date(y: Int, m: Int, d: Int, h: Int = 0, minute: Int = 0) = Grego.fieldsToDay(y,m,d) * Grego.MILLIS_PER_DAY + h * 3_600_000 + minute * 60_000
    @Test fun utcOffsetsAndTransitionsMatchUsRules() {
        val zone = us()
        val out = IntArray(2)
        zone.getOffset(date(2025,2,9,6,59),false,out)
        assertContentEquals(intArrayOf(-18_000_000,0),out)
        zone.getOffset(date(2025,2,9,7),false,out)
        assertContentEquals(intArrayOf(-18_000_000,3_600_000),out)
        val next = assertNotNull(zone.getNextTransition(date(2025,0,1),false))
        assertEquals(date(2025,2,9,7),next.getTime())
        assertSame(zone.getTimeZoneRules()[2],next.getTo())
        assertEquals(next.getTime(), zone.getPreviousTransition(next.getTime(),true)?.getTime())
        assertNotEquals(next.getTime(), zone.getNextTransition(next.getTime(),false)?.getTime())
        val first = assertNotNull(zone.getNextTransition(Long.MIN_VALUE,false))
        assertEquals(date(2007,2,11,7), first.getTime())
        assertNull(zone.getPreviousTransition(first.getTime(),false))
        assertSame(first,zone.getNextTransition(first.getTime(),true))
    }
    @Test fun gapOverlapAndNegativeSavingsFollowSourceOptions() {
        val zone = us()
        val out = IntArray(2)
        zone.getOffsetFromLocal(date(2025,2,9,2,30),1,1,out)
        assertEquals(0,out[1])
        zone.getOffsetFromLocal(date(2025,2,9,2,30),3,3,out)
        assertEquals(3_600_000,out[1])
        zone.getOffsetFromLocal(date(2025,10,2,1,30),1,1,out)
        assertEquals(0,out[1])
        zone.getOffsetFromLocal(date(2025,10,2,1,30),3,3,out)
        assertEquals(3_600_000,out[1])
        zone.setDSTSavings(-3_600_000)
        zone.getOffset(date(2025,6,1),false,out)
        assertEquals(-3_600_000,out[1])
    }
    @Test fun cloningSharesCachedRulesUntilMutationAndFreezeProtectsState() {
        val zone = us()
        val rules = zone.getTimeZoneRules()
        val copy = zone.cloneAsThawed()
        assertEquals(zone,copy)
        assertEquals(zone.hashCode(),copy.hashCode())
        assertNotSame(zone.getTimeZoneRules(),copy.getTimeZoneRules())
        assertSame(rules[1],copy.getTimeZoneRules()[1])
        copy.setID("Copy")
        assertNotSame(rules[1],copy.getTimeZoneRules()[1])
        assertEquals("Test",zone.getID())
        assertTrue(zone.hasSameRules(copy))
        zone.freeze()
        assertSame(zone,zone.clone())
        assertFalse(zone.cloneAsThawed().isFrozen())
        assertFailsWith<UnsupportedOperationException> { zone.setID(null) }
        assertFailsWith<UnsupportedOperationException> { zone.setDSTSavings(0) }
        assertFailsWith<UnsupportedOperationException> { zone.setStartYear(0) }
    }
    @Test fun fixedRulesValidateFieldsAndIgnoreSuppliedMonthLength() {
        val zone = SimpleTimeZoneCore(123,"Fixed")
        assertEquals(1,zone.getTimeZoneRules().size)
        assertNull(zone.getNextTransition(0,true))
        assertNull(zone.getPreviousTransition(0,true))
        assertFalse(zone.useDaylightTime())
        assertEquals(3_600_000,zone.getDSTSavings())
        assertEquals(123,zone.getOffset(1,2025,0,31,1,0,-100))
        assertFailsWith<IllegalArgumentException> { zone.getOffset(1,2025,1,29,1,0) }
        assertFailsWith<IllegalArgumentException> { zone.getOffset(1,2025,0,1,0,0) }
        val short = intArrayOf(999)
        assertFailsWith<IndexOutOfBoundsException> { zone.getOffset(0L,false,short) }
        assertEquals(123,short[0])
        assertFailsWith<NullPointerException> { SimpleTimeZoneCore(0,null) }
    }
    @Test fun failedRuleMutationKeepsOldCachedRulesAndPartiallyChangesFields() {
        val zone = us()
        val old = zone.getTimeZoneRules()
        assertFailsWith<IllegalArgumentException> { zone.setStartRule(20,1,1,0) }
        assertSame(old[1],zone.getTimeZoneRules()[1])
        assertTrue(zone.useDaylightTime())
        zone.setStartRule(2,0,0,0)
        assertFalse(zone.useDaylightTime())
        assertEquals(1,zone.getTimeZoneRules().size)
        zone.setStartRule(2,15,0)
        zone.setEndRule(10,5,0)
        assertTrue(zone.useDaylightTime())
        assertEquals(3,zone.getTimeZoneRules().size)
    }

    @Test fun southernUtcRulesAndLeapDayClampingRunInCommon() {
        val south = SimpleTimeZoneCore(36_000_000,"South",9,1,0,0,2,2,1,0,0,2)
        val out = IntArray(2)
        south.getOffset(date(2025,0,1),false,out)
        assertContentEquals(intArrayOf(36_000_000,3_600_000),out)
        south.getOffset(date(2025,6,1),false,out)
        assertContentEquals(intArrayOf(36_000_000,0),out)
        south.getOffset(date(2025,2,1),false,out)
        assertEquals(0,out[1])
        val leap = SimpleTimeZoneCore(0,"Leap",1,29,0,0,1,8,1,0,0,1)
        leap.getOffset(date(2025,1,27),false,out)
        assertEquals(0,out[1])
        leap.getOffset(date(2025,1,28),false,out)
        assertEquals(3_600_000,out[1])
    }
}
