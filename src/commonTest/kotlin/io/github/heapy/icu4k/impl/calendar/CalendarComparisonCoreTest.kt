package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.util.Date
import kotlin.test.*

class CalendarComparisonCoreTest {
    private class Model(val name: String,var time: Long=0) {
        var lenient=true
        var zone: Any?="zone"
    }
    private class Fixture : CalendarComparisonCore.Owner<Model> {
        override val self=Model("self")
        val other=Model("other")
        val trace=ArrayList<String>()
        var equivalent=true
        val core=CalendarComparisonCore(this)
        override fun sameRuntimeClass(value: Any): Boolean { trace.add("class"); return value is Model }
        override fun asCalendar(value: Any?)=value as? Model
        override fun isEquivalentTo(calendar: Model): Boolean { trace.add("equivalent"); return equivalent }
        override fun getTimeInMillis(calendar: Model): Long { trace.add("time:${calendar.name}"); return calendar.time }
        override fun getTime(calendar: Model): Date { trace.add("date:${calendar.name}"); return Date(calendar.time) }
        override fun isLenient(calendar: Model): Boolean { trace.add("lenient:${calendar.name}"); return calendar.lenient }
        override fun getFirstDayOfWeek(calendar: Model): Int { trace.add("first:${calendar.name}"); return 1 }
        override fun getMinimalDaysInFirstWeek(calendar: Model): Int { trace.add("minimal:${calendar.name}"); return 4 }
        override fun getTimeZone(calendar: Model): Any? { trace.add("zone:${calendar.name}"); return calendar.zone }
        override fun getRepeatedWallTimeOption(calendar: Model): Int { trace.add("repeat:${calendar.name}"); return 0 }
        override fun getSkippedWallTimeOption(calendar: Model): Int { trace.add("skip:${calendar.name}"); return 0 }
        override fun storedLenient()=true
        override fun storedFirstDayOfWeek()=1
        override fun storedMinimalDaysInFirstWeek()=4
        override fun storedRepeatedWallTime()=1
        override fun storedSkippedWallTime()=2
        override fun storedZoneHashCode()=123
    }

    @Test fun equalsShortCircuitsAndUsesOtherDateAccessor() {
        val f=Fixture()
        assertFalse(f.core.calendarEquals(null)); assertTrue(f.core.calendarEquals(f.self)); assertTrue(f.trace.isEmpty())
        assertFalse(f.core.calendarEquals("bad")); assertEquals(listOf("class"),f.trace)
        f.trace.clear(); f.equivalent=false
        assertFalse(f.core.calendarEquals(f.other)); assertEquals(listOf("class","equivalent"),f.trace)
        f.trace.clear(); f.equivalent=true
        assertTrue(f.core.calendarEquals(f.other))
        assertEquals(listOf("class","equivalent","time:self","date:other"),f.trace)
    }

    @Test fun equivalenceShortCircuitsAndEvaluatesBothZonesBeforeNullFailure() {
        val f=Fixture(); f.other.lenient=false
        assertFalse(f.core.isEquivalentTo(f.other))
        assertEquals(listOf("class","lenient:self","lenient:other"),f.trace)
        f.trace.clear(); f.other.lenient=true; f.self.zone=null
        assertFailsWith<NullPointerException> { f.core.isEquivalentTo(f.other) }
        assertEquals(listOf("zone:self","zone:other"),f.trace.takeLast(2))
        f.trace.clear(); f.self.zone="zone"
        assertTrue(f.core.isEquivalentTo(f.other))
        assertEquals(listOf("repeat:self","repeat:other","skip:self","skip:other"),f.trace.takeLast(4))
    }

    @Test fun comparisonOrderingAndLongSubtractionOverflow() {
        val f=Fixture(); f.self.time=Long.MAX_VALUE; f.other.time=-1
        assertTrue(f.core.before(f.other))
        assertEquals(listOf("time:other","time:self"),f.trace)
        f.trace.clear(); assertEquals(-1,f.core.compareTo(f.other))
        assertEquals(listOf("time:self","time:other"),f.trace)
        f.trace.clear(); assertFailsWith<NullPointerException> { f.core.compareTo(null) }
        assertEquals(listOf("time:self"),f.trace)
        f.trace.clear(); assertFalse(f.core.after(Date(-1)))
        assertEquals(listOf("time:self"),f.trace)
        f.trace.clear(); assertEquals("nullis not a Calendar or Date",assertFailsWith<IllegalArgumentException> { f.core.before(null) }.message)
        assertTrue(f.trace.isEmpty())
    }

    @Test fun hashReadsStoredConfigurationWithoutResolvingTime() {
        val f=Fixture()
        assertEquals(1 or (1 shl 1) or (4 shl 4) or (1 shl 7) or (2 shl 9) or (123 shl 11),f.core.calendarHashCode())
        assertTrue(f.trace.isEmpty())
    }
}
