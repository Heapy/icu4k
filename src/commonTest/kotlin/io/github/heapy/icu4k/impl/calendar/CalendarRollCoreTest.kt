package io.github.heapy.icu4k.impl.calendar

import kotlin.test.*

class CalendarRollCoreTest {
    private class Fixture : CalendarRollCore.Owner {
        val fields=IntArray(24)
        val trace=ArrayList<String>()
        var time=0L
        var minimum=1
        var maximum=31
        var eraBackward=false
        var setterFailure=false
        var completeFailure=false
        val core=CalendarRollCore(this)
        override fun complete() { trace.add("complete"); if(completeFailure) error("complete") }
        override fun get(field: Int): Int { trace.add("get:$field"); return fields[field] }
        override fun internalGet(field: Int): Int { trace.add("internal:$field"); return fields[field] }
        override fun internalGetMonth(): Int { trace.add("month"); return fields[2] }
        override fun set(field: Int,value: Int) { trace.add("set:$field:$value"); fields[field]=value }
        override fun clear(field: Int) { trace.add("clear:$field"); fields[field]=0 }
        override fun getActualMinimum(field: Int): Int { trace.add("min:$field"); return minimum }
        override fun getActualMaximum(field: Int): Int { trace.add("max:$field"); return maximum }
        override fun getMaximum(field: Int): Int { trace.add("limit:$field"); return maximum }
        override fun pinField(field: Int) { trace.add("pin:$field") }
        override fun isEra0CountingBackward(): Boolean { trace.add("backward"); return eraBackward }
        override fun getFirstDayOfWeek()=1
        override fun getMinimalDaysInFirstWeek()=1
        override fun getTimeInMillis(): Long { trace.add("time"); return time }
        override fun setTimeInMillis(value: Long) { trace.add("time:$value"); if(setterFailure) error("setTime"); time=value }
        override fun storedTimeInMillis()=time
        override fun assignStoredTime(value: Long) { trace.add("stored:$value"); time=value }
        override fun fieldName(field: Int): String { trace.add("name:$field"); return "field$field" }
    }

    @Test fun zeroUnsupportedAndCompletionFailures() {
        val f=Fixture()
        f.core.roll(Int.MIN_VALUE,0)
        assertTrue(f.trace.isEmpty())
        assertEquals("Calendar.roll(field22) not supported",assertFailsWith<IllegalArgumentException> { f.core.roll(22,1) }.message)
        assertEquals(listOf("complete","name:22"),f.trace)
        f.trace.clear(); f.completeFailure=true
        assertFailsWith<IllegalStateException> { f.core.roll(22,1) }
        assertEquals(listOf("complete"),f.trace)
    }

    @Test fun fixedRangeOverflowAndZeroDivisor() {
        val f=Fixture(); f.fields[5]=31
        f.core.roll(5,1)
        assertEquals(1,f.fields[5])
        assertEquals(listOf("complete","min:5","max:5","internal:5","set:5:1"),f.trace)
        f.trace.clear(); f.minimum=0; f.maximum=-1
        assertFailsWith<ArithmeticException> { f.core.roll(5,1) }
        assertEquals(listOf("complete","min:5","max:5","internal:5"),f.trace)
        f.minimum=0; f.maximum=23; f.fields[11]=23; f.time=Long.MAX_VALUE
        f.core.roll(11,1)
        assertEquals(Long.MAX_VALUE-23*3600000L,f.time)
    }

    @Test fun boundedAndBackwardEraYearsPinAfterSetting() {
        val f=Fixture(); f.fields[0]=1; f.fields[1]=1; f.maximum=10
        f.core.roll(1,-1)
        assertEquals(10,f.fields[1])
        assertEquals(listOf("complete","get:0","internal:1","max:1","set:1:10","pin:2","pin:5"),f.trace)
        f.trace.clear(); f.fields[0]=0; f.fields[1]=1; f.eraBackward=true
        f.core.roll(1,2)
        assertEquals(1,f.fields[1])
        assertEquals(listOf("complete","get:0","backward","internal:1","backward","set:1:1","pin:2","pin:5"),f.trace)
    }

    @Test fun directTimeMutationSurvivesTimestampSetterFailure() {
        val f=Fixture(); f.fields[6]=1; f.maximum=365; f.time=1000000000L; f.setterFailure=true
        assertFailsWith<IllegalStateException> { f.core.roll(6,-1) }
        assertEquals(364*86400000L,f.time)
        assertEquals(listOf("complete","internal:6","max:6","stored:${-86400000L}","stored:${364*86400000L}","time:${364*86400000L+1000000000L}"),f.trace)
    }
}
