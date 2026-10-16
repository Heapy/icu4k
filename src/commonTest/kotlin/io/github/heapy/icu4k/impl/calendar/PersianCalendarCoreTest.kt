package io.github.heapy.icu4k.impl.calendar

import kotlin.test.*

class PersianCalendarCoreTest {
    private class Fixture : PersianCalendarCore.Owner {
        val fields=IntArray(24)
        val set=BooleanArray(24)
        val trace=ArrayList<String>()
        var preferYear=false
        var failField: Int?=null
        val core=PersianCalendarCore(this)
        override fun newerField(defaultField: Int,alternateField: Int): Int { trace.add("newer"); return if(preferYear) alternateField else defaultField }
        override fun internalGet(field: Int,defaultValue: Int): Int { trace.add("get:$field:$defaultValue"); return if(set[field]) fields[field] else defaultValue }
        override fun internalSet(field: Int,value: Int) { trace.add("set:$field:$value"); if(failField==field) error("set:$field"); fields[field]=value; set[field]=true }
    }

    @Test fun defaultsSignedYearsAndLimits() {
        val f=Fixture()
        assertEquals(1,f.core.handleGetExtendedYear()); assertEquals(listOf("newer","get:19:1"),f.trace)
        f.preferYear=true; f.fields[0]=9; f.set[0]=true; f.fields[1]=Int.MIN_VALUE; f.set[1]=true; f.trace.clear()
        assertEquals(Int.MIN_VALUE,f.core.handleGetExtendedYear()); assertEquals(listOf("newer","get:1:1"),f.trace)
        assertEquals(622,f.core.getRelatedYearDifference()); assertEquals("persian",f.core.getType())
        assertEquals(-5000000,f.core.handleGetLimit(1,0)); assertEquals(0,f.core.handleGetLimit(0,3))
        assertEquals(29,f.core.handleGetLimit(5,2))
        assertFailsWith<IndexOutOfBoundsException> { f.core.handleGetLimit(4,0) }
        assertFailsWith<IndexOutOfBoundsException> { f.core.handleGetLimit(0,4) }
    }

    @Test fun correctedLeapDaysMoveToFollowingYearAndRoundTrip() {
        val f=Fixture()
        for(year in listOf(1502,1601,2030,2059,2748,2987)) {
            assertFalse(PersianCalendarCore.isLeapYear(year))
            assertTrue(PersianCalendarCore.isLeapYear(year+1))
            assertEquals(365,f.core.handleGetYearLength(year)); assertEquals(366,f.core.handleGetYearLength(year+1))
            for(y in year-1..year+2) {
                val start=f.core.handleComputeMonthStart(y,0,true)+1
                assertEquals(1948320L+PersianCalendarCore.firstJulianOfYear(y),start.toLong())
                f.core.handleComputeFields(start)
                assertEquals(y,f.fields[1]); assertEquals(0,f.fields[2]); assertEquals(1,f.fields[5]); assertEquals(1,f.fields[6])
                f.core.handleComputeFields(start+f.core.handleGetYearLength(y)-1)
                assertEquals(y,f.fields[1]); assertEquals(11,f.fields[2]); assertEquals(f.core.handleGetMonthLength(y,11),f.fields[5])
            }
        }
    }

    @Test fun monthStartsLengthsAndFloorNormalization() {
        val f=Fixture()
        for(year in listOf(-33,-1,0,1,1403,1502,1503,2988)) {
            for(month in 0..11) {
                val start=f.core.handleComputeMonthStart(year,month,true)
                assertEquals(start,f.core.handleComputeMonthStart(year,month,false))
                assertEquals(start,f.core.handleComputeMonthStart(year+1,month-12,true))
                assertEquals(start,f.core.handleComputeMonthStart(year-1,month+12,true))
                assertEquals(f.core.handleGetMonthLength(year,month),f.core.handleGetMonthLength(year+1,month-12))
                assertEquals(f.core.handleGetMonthLength(year,month),f.core.handleComputeMonthStart(year,month+1,true)-start)
                f.core.handleComputeFields(start+1)
                assertEquals(year,f.fields[1]); assertEquals(month,f.fields[2]); assertEquals(1,f.fields[5])
            }
        }
        assertEquals(29,f.core.handleGetMonthLength(1503,-1)); assertEquals(30,f.core.handleGetMonthLength(1504,-1))
    }

    @Test fun cycleArithmeticAndSourceIntegerOverflow() {
        for(year in -100..1000) {
            assertEquals(PersianCalendarCore.isLeapYear(year),PersianCalendarCore.isLeapYear(year+33))
            assertEquals(12053L,PersianCalendarCore.firstJulianOfYear(year+33)-PersianCalendarCore.firstJulianOfYear(year))
        }
        assertFalse(PersianCalendarCore.isLeapYear(Int.MIN_VALUE))
        assertTrue(PersianCalendarCore.isLeapYear(Int.MIN_VALUE+1))
        assertFalse(PersianCalendarCore.isLeapYear(Int.MAX_VALUE))
        assertEquals(365L,PersianCalendarCore.firstJulianOfYear(Int.MIN_VALUE+2)-PersianCalendarCore.firstJulianOfYear(Int.MIN_VALUE+1))
        val f=Fixture()
        f.core.handleComputeFields(Int.MIN_VALUE)
        val a=f.fields.copyOf()
        val wrappedDays=Int.MIN_VALUE-1948320
        val year=1+CalendarDateCore.floorDivide(33L*wrappedDays+3,12053L).toInt()
        assertEquals(year,a[1])
        assertEquals(a[1],a[19]); assertEquals(a[2],a[23])
    }

    @Test fun fieldOrderAndPartialWritesOnFailure() {
        val f=Fixture(); val start=f.core.handleComputeMonthStart(1503,0,true)+1
        f.core.handleComputeFields(start)
        assertEquals(listOf("set:0:0","set:1:1503","set:19:1503","set:2:0","set:23:0","set:5:1","set:6:1"),f.trace)
        val broken=Fixture(); broken.failField=19
        assertFailsWith<IllegalStateException> { broken.core.handleComputeFields(start) }
        assertTrue(broken.set[0]); assertTrue(broken.set[1]); assertFalse(broken.set[19]); assertFalse(broken.set[2])
        assertEquals(1503,broken.fields[1])
        assertEquals(listOf("set:0:0","set:1:1503","set:19:1503"),broken.trace)
    }
}
