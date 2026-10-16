package io.github.heapy.icu4k.impl.calendar

import kotlin.test.*

class CalendarAddCoreTest {
    private class Fixture : CalendarAddCore.Owner {
        val fields=IntArray(24)
        val trace=ArrayList<String>()
        var time=0L
        var lenientValue=false
        var backward=false
        var skipped=0
        var transition: Long?=null
        var pinFailure=false
        var wall: (() -> Int)?=null
        var offset: (() -> Int)?=null
        val core=CalendarAddCore(this)
        override fun get(field: Int): Int { trace.add("get:$field"); return when(field) { 21 -> wall?.invoke() ?: fields[field]; 16 -> offset?.invoke() ?: fields[field]; else -> fields[field] } }
        override fun set(field: Int,value: Int) { trace.add("set:$field:$value"); fields[field]=value }
        override fun pinField(field: Int) { trace.add("pin:$field"); if(pinFailure) error("pin failed") }
        override fun isEra0CountingBackward(): Boolean { trace.add("backward"); return backward }
        override fun isLenient(): Boolean { trace.add("lenient"); return lenientValue }
        override fun setLenient(value: Boolean) { trace.add("lenient:$value"); lenientValue=value }
        override fun complete() { trace.add("complete") }
        override fun fieldName(field: Int): String { trace.add("name:$field"); return "field$field" }
        override fun getTimeInMillis(): Long { trace.add("time"); return time }
        override fun setTimeInMillis(value: Long) { trace.add("time:$value"); time=value }
        override fun storedTimeInMillis()=time
        override fun storedSkippedWallTime()=skipped
        override fun getImmediatePreviousZoneTransition(time: Long): Long? { trace.add("transition:$time"); return transition }
    }

    @Test fun zeroAndUnsupportedFieldsDoNotResolveTime() {
        val f=Fixture()
        f.core.add(Int.MIN_VALUE,0)
        assertTrue(f.trace.isEmpty())
        assertEquals("Calendar.add(field22) not supported",assertFailsWith<IllegalArgumentException> { f.core.add(22,1) }.message)
        assertEquals(listOf("name:22"),f.trace)
    }

    @Test fun temporaryLeniencyOrderingOverflowAndFailureState() {
        val f=Fixture(); f.backward=true; f.fields[1]=4
        f.core.add(1,Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE+4,f.fields[1])
        assertEquals(listOf("get:0","backward","lenient","lenient:true","get:1","set:1:${Int.MIN_VALUE+4}","pin:5","complete","lenient:false"),f.trace)
        f.trace.clear(); f.pinFailure=true
        assertFailsWith<IllegalStateException> { f.core.add(2,1) }
        assertTrue(f.lenientValue)
        assertFalse("complete" in f.trace)
    }

    @Test fun gapOptionsAndMissingTransitionFailure() {
        for (option in 0..2) {
            val f=Fixture(); f.skipped=option; f.transition=123L
            // Both possible candidates are in the gap; emulate the zone's normalization.
            f.wall={ if(f.time==0L) 7200000 else 10800000 }
            f.offset={ if(f.time==0L) 0 else 3600000 }
            f.core.add(5,1)
            assertEquals(when(option) { 0 -> 86400000L; 1 -> 82800000L; else -> 123L },f.time)
        }
        val f=Fixture(); f.skipped=2
        f.wall={ if(f.time==0L) 0 else 1 }; f.offset={ if(f.time==0L) 0 else 3600000 }
        assertEquals("Could not locate a time zone transition before 86400000",assertFailsWith<RuntimeException> { f.core.add(5,1) }.message)
        assertEquals(82800000L,f.time)
    }

    @Test fun elapsedHoursAndLongOverflowAvoidWallCallbacks() {
        val f=Fixture(); f.time=Long.MAX_VALUE
        f.core.add(11,1)
        assertEquals(Long.MIN_VALUE+3599999,f.time)
        assertEquals(listOf("time","time:${f.time}"),f.trace)
    }
}
