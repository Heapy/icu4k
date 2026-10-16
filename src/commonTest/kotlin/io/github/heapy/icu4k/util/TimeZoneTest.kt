package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.timezone.SimpleTimeZoneCore
import kotlin.test.*

class TimeZoneTest {
    private open class Fixed(id: String?, var raw: Int = 12345) : TimeZone(id) {
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int) = raw
        override fun setRawOffset(offsetMillis: Int) { raw = offsetMillis }
        override fun getRawOffset() = raw
        override fun useDaylightTime() = false
        override fun inDaylightTime(date: Date?) = false
        override fun cloneAsThawed(): TimeZone = Fixed(getID(), raw)
    }
    private class Annual : BasicTimeZone("test") {
        private val rules = SimpleTimeZoneCore(-28800000, "test", 2, 2, 1, 7200000, 0, 10, 1, 1, 7200000, 0)
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int) = rules.getOffset(era, year, month, day, dayOfWeek, milliseconds)
        override fun setRawOffset(offsetMillis: Int) = rules.setRawOffset(offsetMillis)
        override fun getRawOffset() = rules.getRawOffset()
        override fun useDaylightTime() = rules.useDaylightTime()
        override fun inDaylightTime(date: Date?) = false
        override fun getNextTransition(base: Long, inclusive: Boolean) = rules.getNextTransition(base, inclusive)
        override fun getPreviousTransition(base: Long, inclusive: Boolean) = rules.getPreviousTransition(base, inclusive)
        @Suppress("UNCHECKED_CAST")
        override fun getTimeZoneRules() = rules.getTimeZoneRules() as Array<TimeZoneRule?>
    }
    @Test fun publicBaseOffsetsIdentityAndSubclassPolicies() {
        val a = Fixed("custom"); val b = a.clone()
        assertNotSame(a, b); assertEquals(a, b); assertEquals("custom".hashCode(), a.hashCode())
        b.setID("copy"); assertEquals("custom", a.getID()); assertTrue(a.hasSameRules(b)); assertNotEquals(a, b)
        val offsets = IntArray(2); a.getOffset(Long.MIN_VALUE, false, offsets)
        assertContentEquals(intArrayOf(12345, 0), offsets); assertEquals(12345, a.getOffset(0L))
        assertEquals(0, a.getDSTSavings()); assertFalse(a.observesDaylightTime())
        assertFailsWith<NullPointerException> { a.setID(null) }
        assertFailsWith<UnsupportedOperationException> { a.freeze() }
        assertFalse(a.isFrozen())
        val virtualID = object : Fixed("stored") { override fun getID() = "virtual" }
        assertEquals("stored".hashCode(), virtualID.hashCode())
    }
    @Test fun publicBasicTransitionAlgorithmsDispatchToTheOwner() {
        val a = Annual(); val b = Annual()
        val start = 1704067200000L; val end = 1735689600000L
        assertEquals(-28800000, a.getOffset(start)); assertEquals(-25200000, a.getOffset(1719792000000L))
        assertEquals(1710064800000L, a.getNextTransition(start, false)!!.getTime())
        assertTrue(a.hasEquivalentTransitions(b, start, end)); assertTrue(a.hasEquivalentTransitions(a, start, end, true))
        assertFalse(a.hasEquivalentTransitions(Fixed("fixed"), start, end)); assertFalse(a.hasEquivalentTransitions(null, start, end))
        assertEquals(3, a.getTimeZoneRules(start)!!.size); assertEquals(3, a.getSimpleTimeZoneRulesNear(start).size)
        b.setRawOffset(0); assertFalse(a.hasEquivalentTransitions(b, start, end, true))
        assertEquals(6, BasicTimeZone.LocalOption.entries.size)
        assertFailsWith<IllegalStateException> { a.getOffsetFromLocal(0, null, null, null) }
    }
    @Test fun publicMetadataIncludesAliasesRegionsAndWindowsMappings() {
        assertEquals("Etc/UTC", TimeZone.getCanonicalID("UTC"))
        assertTrue("UTC" in TimeZone.getAvailableIDs())
        assertTrue("America/New_York" in TimeZone.getAvailableIDs(TimeZone.SystemTimeZoneType.CANONICAL_LOCATION, "US", null))
        assertEquals("US", TimeZone.getRegion("America/New_York"))
        assertEquals("Eastern Standard Time", TimeZone.getWindowsID("US/Eastern"))
        assertEquals("America/New_York", TimeZone.getIDForWindowsID("Eastern Standard Time", "US"))
    }
}
