package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.util.Date
import kotlin.test.*

class CalendarWeekendCoreTest {
    private class Fixture : CalendarWeekendCore.State, CalendarWeekendCore.Owner<Fixture> {
        override val self get() = this
        override val firstDayOfWeek get() = first
        override val minimalDaysInFirstWeek get() = minimal
        var first=2
        var minimal=4
        override var weekendOnset=7
        override var weekendOnsetMillis=43200000
        override var weekendCease=1
        override var weekendCeaseMillis=43200000
        val fields=IntArray(24)
        val trace=ArrayList<String>()
        var failMinimal=false
        var failTime=false
        var weekendOverride: Boolean?=null
        var typeOverride: Int?=null
        val core=CalendarWeekendCore(this,this)
        override fun setFirstDayOfWeek(value: Int) { trace.add("first:$value"); first=value }
        override fun setMinimalDaysInFirstWeek(value: Int) { trace.add("minimal:$value"); if(failMinimal) error("minimal"); minimal=value }
        override fun setWeekData(value: CalendarWeekData): Fixture { trace.add("weekData"); return core.setWeekData(value) }
        override fun get(field: Int): Int { trace.add("get:$field"); return fields[field] }
        override fun internalGet(field: Int): Int { trace.add("internal:$field"); return fields[field] }
        override fun getDayOfWeekType(day: Int): Int { trace.add("type:$day"); return typeOverride ?: core.getDayOfWeekType(day) }
        override fun getWeekendTransition(day: Int): Int { trace.add("transition:$day"); return core.getWeekendTransition(day) }
        override fun setTime(date: Date?) { trace.add("time"); if(failTime) error("time"); date!!.getTime() }
        override fun isWeekend(): Boolean { trace.add("weekend"); return weekendOverride ?: core.isWeekend() }
    }

    @Test fun realRegionDataFallbackFreshValuesAndInitializationCache() {
        val world=CalendarWeekDataCore.getWeekDataForRegion("001")
        assertEquals(world,CalendarWeekDataCore.getWeekDataForRegion(null))
        assertEquals(world,CalendarWeekDataCore.getWeekDataForRegion("not-a-region"))
        val us=CalendarWeekDataCore.getWeekDataForRegion("US")
        assertEquals("{1, 1, 7, 0, 1, 86400000}",us.toString())
        assertEquals(us,CalendarWeekDataCore.getWeekDataForRegion("US"))
        assertNotSame(us,CalendarWeekDataCore.getWeekDataForRegion("US"))
        assertSame(CalendarWeekDataCore.cachedForRegion(null),CalendarWeekDataCore.cachedForRegion("001"))
        val f=Fixture(); f.core.setWeekDataForRegion("US")
        assertEquals(listOf("weekData","first:1","minimal:1"),f.trace)
        assertEquals(us,f.core.getWeekData())
    }

    @Test fun partialWeekendOnsetAndExclusiveCeaseAcrossWeekBoundary() {
        val f=Fixture()
        for(day in 1..7) {
            assertEquals(when(day) { 7 -> 2; 1 -> 3; else -> 0 },f.core.getDayOfWeekType(day))
            f.fields[7]=day
            for(hour in listOf(11,12,13)) {
                f.fields[11]=hour
                assertEquals(day==7 && hour>=12 || day==1 && hour<12,f.core.isWeekend())
            }
        }
        f.trace.clear(); f.fields[7]=7; f.fields[11]=12
        assertTrue(f.core.isWeekend())
        assertEquals(listOf("get:7","type:7","internal:14","internal:13","internal:12","internal:11","transition:7"),f.trace)
        f.weekendCease=7; f.weekendCeaseMillis=0
        assertEquals(2,f.core.getDayOfWeekType(7))
        assertEquals(43200000,f.core.getWeekendTransition(7))
        f.weekendOnsetMillis=0
        assertEquals(1,f.core.getDayOfWeekType(7))
        assertFailsWith<IllegalArgumentException> { f.core.getDayOfWeekType(0) }
        assertFailsWith<IllegalArgumentException> { f.core.getWeekendTransition(0) }
    }

    @Test fun setterFailureRetainsEarlierWritesAndSnapshotsAreFresh() {
        val f=Fixture(); val old=f.core.getWeekData(); f.failMinimal=true
        val value=CalendarWeekData(5,1,3,Int.MIN_VALUE,9,Int.MAX_VALUE)
        assertFailsWith<IllegalStateException> { f.core.setWeekData(value) }
        assertEquals(5,f.first); assertEquals(4,f.minimal)
        assertEquals(old.weekendOnset,f.weekendOnset)
        assertEquals(old.weekendOnsetMillis,f.weekendOnsetMillis)
        f.failMinimal=false
        assertSame(f,f.core.setWeekData(value))
        assertEquals(value,f.core.getWeekData())
        assertNotSame(f.core.getWeekData(),f.core.getWeekData())
        f.trace.clear(); assertFailsWith<NullPointerException> { f.core.setWeekData(null) }
        assertTrue(f.trace.isEmpty())
    }

    @Test fun datedPredicateDispatchesAndTimestampFailureStopsEvaluation() {
        val f=Fixture(); f.weekendOverride=true
        assertTrue(f.core.isWeekend(Date(0)))
        assertEquals(listOf("time","weekend"),f.trace)
        f.trace.clear(); f.failTime=true
        assertFailsWith<IllegalStateException> { f.core.isWeekend(Date(0)) }
        assertEquals(listOf("time"),f.trace)
        f.trace.clear(); f.failTime=false; f.fields[7]=1; f.typeOverride=99; f.fields[11]=11
        assertTrue(f.core.isWeekend())
        assertEquals("transition:1",f.trace.last())
    }
}
