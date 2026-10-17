package io.github.heapy.icu4k.util
import kotlin.test.*

class RuleBasedTimeZoneTest {
    @Test fun publicRecurringRulesTransitionsFreezeAndClone() {
        val zone = RuleBasedTimeZone("rules", InitialTimeZoneRule("initial", 0, 0))
        zone.addTransitionRule(AnnualTimeZoneRule("summer", 0, 3600000, DateTimeRule(2, 2, 1, 7200000, DateTimeRule.WALL_TIME), 2000, AnnualTimeZoneRule.MAX_YEAR))
        zone.addTransitionRule(AnnualTimeZoneRule("winter", 0, 0, DateTimeRule(10, 1, 1, 7200000, DateTimeRule.WALL_TIME), 2000, AnnualTimeZoneRule.MAX_YEAR))
        val offsets = IntArray(2)
        zone.getOffset(1719792000000L, false, offsets)
        assertContentEquals(intArrayOf(0, 3600000), offsets)
        assertNotNull(zone.getNextTransition(0, false))
        assertEquals(3, zone.getTimeZoneRules().size)
        zone.freeze()
        assertSame(zone, zone.clone())
        assertFailsWith<UnsupportedOperationException> { zone.addTransitionRule(null) }
        val clone = zone.cloneAsThawed()
        assertFalse(clone.isFrozen())
        assertTrue(zone.hasSameRules(clone))
        clone.setID("clone")
        assertEquals("rules", zone.getID())
    }
    @Test fun queriesRespectVirtualOffsetHooks() {
        var calls = 0
        val zone = object : RuleBasedTimeZone("hooks", InitialTimeZoneRule("initial", 1234, 0)) {
            override fun getOffset(date: Long, local: Boolean, offsets: IntArray?) {
                calls++
                super.getOffset(date, local, offsets)
            }
        }
        assertEquals(1234, zone.getRawOffset())
        assertTrue(calls > 0)
        assertFalse(zone.inDaylightTime(Date(0)))
        assertFailsWith<UnsupportedOperationException> { zone.setRawOffset(0) }
    }
}
