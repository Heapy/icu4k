package io.github.heapy.icu4k.util

import com.ibm.icu.util.SimpleTimeZone as Source
import com.ibm.icu.util.BasicTimeZone.LocalOption as SourceOption
import kotlin.test.*

class SimpleTimeZoneOracleTest {
    private class TracedSource : Source(-28800000, "test", 2, 2, 1, 7200000, 10, 1, 1, 7200000) {
        var calls = arrayListOf<String>()
        override fun isFrozen(): Boolean { calls.add("frozen"); return super.isFrozen() }
        override fun useDaylightTime(): Boolean { calls.add("uses"); return super.useDaylightTime() }
        override fun getRawOffset(): Int { calls.add("raw"); return super.getRawOffset() }
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int): Int {
            calls.add("six"); return super.getOffset(era, year, month, day, dayOfWeek, millis)
        }
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int, length: Int): Int {
            calls.add("seven"); return super.getOffset(era, year, month, day, dayOfWeek, millis, length)
        }
    }
    private class TracedPort : SimpleTimeZone(-28800000, "test", 2, 2, 1, 7200000, 10, 1, 1, 7200000) {
        var calls = arrayListOf<String>()
        override fun isFrozen(): Boolean { calls.add("frozen"); return super.isFrozen() }
        override fun useDaylightTime(): Boolean { calls.add("uses"); return super.useDaylightTime() }
        override fun getRawOffset(): Int { calls.add("raw"); return super.getRawOffset() }
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int): Int {
            calls.add("six"); return super.getOffset(era, year, month, day, dayOfWeek, millis)
        }
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int, length: Int): Int {
            calls.add("seven"); return super.getOffset(era, year, month, day, dayOfWeek, millis, length)
        }
    }
    private fun source() = Source(-28800000, "test", 2, 2, 1, 7200000, 10, 1, 1, 7200000)
    private fun port() = SimpleTimeZone(-28800000, "test", 2, 2, 1, 7200000, 10, 1, 1, 7200000)
    @Test fun virtualDaylightAndIDQueriesAndCloneRebindingMatchSource() {
        val a = TracedSource(); val b = TracedPort()
        a.id = "renamed"; b.setID("renamed"); assertEquals(a.calls, b.calls)
        a.calls.clear(); b.calls.clear()
        assertEquals(a.inDaylightTime(Date(1719792000000L)), b.inDaylightTime(Date(1719792000000L)))
        assertEquals(a.calls, b.calls)
        val x = a.cloneAsThawed() as TracedSource; val y = b.cloneAsThawed() as TracedPort
        x.calls = arrayListOf(); y.calls = arrayListOf(); a.calls.clear(); b.calls.clear()
        assertEquals(x.getOffset(1719792000000L), y.getOffset(1719792000000L))
        assertEquals(x.calls, y.calls); assertTrue(a.calls.isEmpty()); assertTrue(b.calls.isEmpty())
    }
    @Test fun daylightQueriesAndCalendarWallTimePoliciesMatchSource() {
        val a = source(); val b = port()
        for (time in listOf(Long.MIN_VALUE, -12219292800001L, -1L, 0L, 1710064799999L, 1710064800000L, 1719792000000L, 1730624399999L, 1730624400000L, Long.MAX_VALUE)) {
            assertEquals(a.inDaylightTime(Date(time)), b.inDaylightTime(Date(time)), "daylight/$time")
            for (former in BasicTimeZone.LocalOption.entries) for (latter in BasicTimeZone.LocalOption.entries) {
                val x = IntArray(2); val y = IntArray(2)
                a.getOffsetFromLocal(time, SourceOption.valueOf(former.name), SourceOption.valueOf(latter.name), x)
                b.getOffsetFromLocal(time, former, latter, y)
                assertContentEquals(x, y, "$time/$former/$latter")
            }
        }
        val left = com.ibm.icu.util.GregorianCalendar(a, com.ibm.icu.util.ULocale.US)
        val right = GregorianCalendar(b, ULocale.US)
        for (skipped in 0..2) for (repeated in 0..1) for (month in listOf(2, 10)) {
            left.skippedWallTimeOption = skipped; right.setSkippedWallTimeOption(skipped)
            left.repeatedWallTimeOption = repeated; right.setRepeatedWallTimeOption(repeated)
            left.clear(); right.clear(); val day = if (month == 2) 10 else 3
            left.set(2024, month, day, if (month == 2) 2 else 1, 30)
            right.set(2024, month, day, if (month == 2) 2 else 1, 30)
            assertEquals(left.timeInMillis, right.getTimeInMillis(), "$month/$skipped/$repeated")
        }
    }
    @Test fun publicMutatorsClonesHashAndFreezeMatchSource() {
        val a = source(); val b = port()
        a.setStartRule(2, 8, 1, 3600000, true); b.setStartRule(2, 8, 1, 3600000, true)
        a.setEndRule(10, 1, 3600000); b.setEndRule(10, 1, 3600000)
        a.setStartYear(1990); b.setStartYear(1990); a.setDSTSavings(1800000); b.setDSTSavings(1800000)
        a.id = "renamed"; b.setID("renamed")
        assertEquals(a.hashCode(), b.hashCode()); assertEquals(a.toString(), b.toString())
        assertEquals(a.getNextTransition(0, false)?.toString(), b.getNextTransition(0, false)?.toString())
        val x = a.clone(); val y = b.clone(); x.rawOffset = 0; y.setRawOffset(0)
        assertEquals(a == x, b == y); assertEquals(a.hasSameRules(x), b.hasSameRules(y))
        a.freeze(); b.freeze(); assertSame(a, a.clone()); assertSame(b, b.clone())
        assertFalse(a.cloneAsThawed().isFrozen); assertFalse(b.cloneAsThawed().isFrozen())
    }
}
