package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.util.Date
import kotlin.test.*

class TimeZoneBaseCoreTest {
    private class Zone : TimeZoneBaseCore.Owner<Zone> {
        val core = TimeZoneBaseCore(this, this, "stored")
        val trace = ArrayList<String>()
        val fields = ArrayList<List<Int>>()
        var raw = 0
        var uses = false
        var daylight = false
        var frozen = false
        var cloneResult: Zone? = null
        var receivedDate: Date? = null
        var nextDst = ArrayDeque<Int>()
        override fun getRawOffset(zone: Zone): Int { zone.trace.add("raw"); return zone.raw }
        override fun getOffset(zone: Zone, era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int): Int {
            zone.trace.add("fields")
            zone.fields.add(listOf(era, year, month, day, dayOfWeek, millis))
            return zone.raw + if (zone.nextDst.isEmpty()) 0 else zone.nextDst.removeFirst()
        }
        override fun getOffset(zone: Zone, date: Long, local: Boolean, offsets: IntArray) {
            zone.trace.add("virtual:$date:$local")
            zone.core.getOffset(date, local, offsets)
        }
        override fun useDaylightTime(zone: Zone): Boolean { zone.trace.add("uses"); return zone.uses }
        override fun inDaylightTime(zone: Zone, date: Date): Boolean {
            zone.trace.add("daylight"); zone.receivedDate = date; return zone.daylight
        }
        override fun isFrozen(zone: Zone): Boolean { zone.trace.add("frozen"); return zone.frozen }
        override fun cloneAsThawed(zone: Zone): Zone? { zone.trace.add("clone"); return zone.cloneResult }
        override fun hasSameClass(other: Any) = other is Zone
        override fun storedID(other: Any) = (other as Zone).core.getID()
    }

    @Test fun localRetryAndUTCCalculationUseProlepticFields() {
        val z = Zone(); z.raw = 3600000; z.nextDst.addAll(listOf(3600000, 0))
        val offsets = intArrayOf(777, 888, 999)
        z.core.getOffset(0, true, offsets)
        assertContentEquals(intArrayOf(3600000, 0, 999), offsets)
        assertEquals(listOf("raw", "fields", "fields"), z.trace)
        assertEquals(listOf(listOf(1, 1970, 0, 1, 5, 0), listOf(1, 1969, 11, 31, 4, 82800000)), z.fields)
        z.trace.clear(); z.fields.clear(); z.nextDst.add(3600000)
        assertEquals(7200000, z.core.getOffset(0))
        assertEquals(listOf("virtual:0:false", "raw", "fields"), z.trace)
        assertEquals(listOf(listOf(1, 1970, 0, 1, 5, 3600000)), z.fields)
    }

    @Test fun arrayFailuresRetainGetterCallsAndPartialWritesOnEveryPlatform() {
        val z = Zone(); z.raw = 123
        assertFailsWith<NullPointerException> { z.core.getOffset(0, false, null) }
        assertEquals(listOf("raw"), z.trace)
        z.trace.clear()
        assertFailsWith<IndexOutOfBoundsException> { z.core.getOffset(0, false, IntArray(0)) }
        assertEquals(listOf("raw"), z.trace)
        z.trace.clear()
        val one = intArrayOf(777)
        assertFailsWith<IndexOutOfBoundsException> { z.core.getOffset(0, true, one) }
        assertContentEquals(intArrayOf(123), one)
        assertEquals(listOf("raw", "fields"), z.trace)
    }

    @Test fun daylightAndRuleQueriesShortCircuitAndAllocateCurrentDate() {
        val z = Zone(); z.uses = true
        assertEquals(3600000, z.core.getDSTSavings())
        assertTrue(z.core.observesDaylightTime())
        assertNull(z.receivedDate)
        assertEquals(listOf("uses", "uses"), z.trace)
        z.trace.clear(); z.uses = false; z.daylight = true
        val start = Date().getTime()
        assertTrue(z.core.observesDaylightTime())
        assertTrue(z.receivedDate!!.getTime() in start..Date().getTime())
        assertEquals(listOf("uses", "daylight"), z.trace)
        z.trace.clear()
        assertFalse(z.core.hasSameRules(null)); assertTrue(z.trace.isEmpty())
        val other = Zone(); other.raw = 1
        assertFalse(z.core.hasSameRules(other))
        assertEquals(listOf("raw"), z.trace)
        assertEquals(listOf("raw"), other.trace)
    }

    @Test fun IDStateCloneDispatchAndBaseFreezeContract() {
        val z = Zone(); val other = Zone()
        assertTrue(z.core.equalsZone(other))
        assertFalse(z.core.equalsZone("stored"))
        assertFalse(z.core.equalsZone(null))
        z.frozen = true
        assertFailsWith<NullPointerException> { z.core.setID(null) }
        assertTrue(z.trace.isEmpty())
        assertEquals("Attempt to modify a frozen TimeZone instance.", assertFailsWith<UnsupportedOperationException> { z.core.setID("changed") }.message)
        assertSame(z, z.core.clone())
        assertFalse(z.core.isFrozen())
        assertEquals("Needs to be implemented by the subclass.", assertFailsWith<UnsupportedOperationException> { z.core.freeze() }.message)
        z.frozen = false; z.cloneResult = other
        assertSame(other, z.core.clone())
        z.cloneResult = null; assertNull(z.core.clone())
        val uninitialized = TimeZoneBaseCore(z, z)
        assertNull(uninitialized.getID())
        assertTrue(uninitialized.equalsZone(z)) // Identity succeeds before inspecting ID.
        assertFailsWith<NullPointerException> { uninitialized.equalsZone(other) }
        assertFailsWith<NullPointerException> { uninitialized.hashCodeID() }
        assertFailsWith<NullPointerException> { TimeZoneBaseCore(z, z, null) }
        z.core.setID("new")
        assertFalse(z.core.equalsZone(other))
        assertEquals("new".hashCode(), z.core.hashCodeID())
    }
}
