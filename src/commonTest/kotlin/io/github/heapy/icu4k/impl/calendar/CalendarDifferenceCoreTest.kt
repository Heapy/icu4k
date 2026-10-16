package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.util.Date
import kotlin.test.*

class CalendarDifferenceCoreTest {
    private class Fixture : CalendarDifferenceCore.Owner {
        var time=0L
        var unit=10L
        var failAdd=false
        val trace=ArrayList<String>()
        val core=CalendarDifferenceCore(this)
        override fun getTimeInMillis(): Long { trace.add("get"); return time }
        override fun setTimeInMillis(value: Long) { trace.add("set:$value"); time=value }
        override fun add(field: Int,amount: Int) { trace.add("add:$field:$amount"); if(failAdd) error("add"); time+=amount*unit }
    }

    @Test fun signedSearchAndFinalPartialEndpoint() {
        for(target in listOf(-195L,-190L,-1L,0L,1L,190L,195L)) {
            val f=Fixture()
            assertEquals((target/10).toInt(),f.core.fieldDifference(Date(target),5))
            assertEquals(target/10*10,f.time)
            val additions=f.trace.filter { it.startsWith("add:") }
            assertTrue(additions.isNotEmpty())
            for(index in f.trace.indices) if(f.trace[index].startsWith("add:")) assertEquals("set:0",f.trace[index-1])
        }
    }

    @Test fun intLimitsOverflowAndFailureLeaveLastProbeState() {
        for(target in listOf(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong())) {
            val f=Fixture(); f.unit=1
            assertEquals(target.toInt(),f.core.fieldDifference(Date(target),14))
            assertEquals(target,f.time)
        }
        val positive=Fixture(); positive.unit=1
        assertFailsWith<RuntimeException> { positive.core.fieldDifference(Date(Int.MAX_VALUE.toLong()+1),14) }
        assertEquals(Int.MAX_VALUE.toLong(),positive.time)
        val negative=Fixture(); negative.unit=1
        assertFailsWith<RuntimeException> { negative.core.fieldDifference(Date(Int.MIN_VALUE.toLong()-1),14) }
        assertEquals(Int.MIN_VALUE.toLong(),negative.time)
        val broken=Fixture(); broken.time=77; broken.failAdd=true
        assertFailsWith<IllegalStateException> { broken.core.fieldDifference(Date(100),5) }
        assertEquals(77,broken.time)
        assertEquals(listOf("get","set:77","add:5:1"),broken.trace)
    }

    @Test fun nullAndDateGetterAreEvaluatedAfterCalendarTime() {
        val f=Fixture()
        assertFailsWith<NullPointerException> { f.core.fieldDifference(null,99) }
        assertEquals(listOf("get"),f.trace)
        f.trace.clear()
        val date=object : Date(0) { override fun getTime(): Long { f.trace.add("date"); return 0 } }
        assertEquals(0,f.core.fieldDifference(date,99))
        assertEquals(listOf("get","date","set:0","add:99:0"),f.trace)
    }
}
