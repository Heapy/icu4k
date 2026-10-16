package io.github.heapy.icu4k.impl.calendar

import kotlin.test.*

class IndianCalendarCoreTest {
    private class Fixture : IndianCalendarCore.Owner {
        val fields=IntArray(24)
        val set=BooleanArray(24)
        val trace=ArrayList<String>()
        var cachedYear=2024
        var preferYear=false
        var failField: Int?=null
        var gregorian: ((Int,Int)->Int)?=null
        val core=IndianCalendarCore(this)
        override fun newerField(defaultField: Int,alternateField: Int): Int { trace.add("newer"); return if(preferYear) alternateField else defaultField }
        override fun internalGet(field: Int,defaultValue: Int): Int { trace.add("get:$field:$defaultValue"); return if(set[field]) fields[field] else defaultValue }
        override fun internalSet(field: Int,value: Int) { trace.add("set:$field:$value"); if(failField==field) error("set:$field"); fields[field]=value; set[field]=true }
        override fun getGregorianYear(): Int { trace.add("gregorianYear"); return cachedYear }
        override fun computeGregorianMonthStart(year: Int,month: Int): Int { trace.add("gregorian:$year:$month"); return gregorian?.invoke(year,month) ?: CalendarDateCore.computeGregorianMonthStart(year,month) }
        override fun baseGetYearLength(year: Int): Int { trace.add("baseYear:$year"); return core.handleComputeMonthStart(year+1,0,false)-core.handleComputeMonthStart(year,0,false) }
    }

    @Test fun extendedYearDefaultsIgnoreEraAndPreserveSignedYears() {
        val f=Fixture()
        assertEquals(1,f.core.handleGetExtendedYear()); assertEquals(listOf("newer","get:19:1"),f.trace)
        f.preferYear=true; f.fields[0]=9; f.set[0]=true; f.fields[1]=Int.MIN_VALUE; f.set[1]=true; f.trace.clear()
        assertEquals(Int.MIN_VALUE,f.core.handleGetExtendedYear()); assertEquals(listOf("newer","get:1:1"),f.trace)
        assertEquals(79,f.core.getRelatedYearDifference()); assertEquals("indian",f.core.getType())
    }

    @Test fun monthLengthsYearLengthAndDistinctNegativeNormalization() {
        val f=Fixture()
        assertEquals(31,f.core.handleGetMonthLength(1946,0))
        assertEquals(30,f.core.handleGetMonthLength(1945,0))
        for(month in 1..5) assertEquals(31,f.core.handleGetMonthLength(1945,month))
        for(month in 6..11) assertEquals(30,f.core.handleGetMonthLength(1946,month))
        assertEquals(31,f.core.handleGetMonthLength(1945,12))
        assertEquals(30,f.core.handleGetMonthLength(1946,-1))
        assertEquals(366,f.core.handleGetYearLength(1946)); assertEquals(365,f.core.handleGetYearLength(1945))
        f.trace.clear(); val negative=f.core.handleComputeMonthStart(1946,-1,true)
        assertEquals(listOf("gregorian:2024:2"),f.trace)
        val normalized=f.core.handleComputeMonthStart(1945,11,true)
        assertNotEquals(normalized,negative)
        assertEquals(negative,f.core.handleComputeMonthStart(1946,-1,false))
        assertEquals(0,f.core.handleGetLimit(0,3)); assertEquals(-5000000,f.core.handleGetLimit(1,0))
        assertFailsWith<IndexOutOfBoundsException> { f.core.handleGetLimit(4,0) }
        assertFailsWith<IndexOutOfBoundsException> { f.core.handleGetLimit(0,4) }
    }

    @Test fun sakaNewYearAndMonthBoundaries() {
        for(year in listOf(2023,2024)) {
            val f=Fixture(); f.cachedYear=year
            val jan1=CalendarDateCore.computeGregorianMonthStart(year,0)+1
            f.core.handleComputeFields(jan1+79)
            assertEquals(year-79,f.fields[19]); assertEquals(11,f.fields[2]); assertEquals(30,f.fields[5]); assertEquals(365,f.fields[6])
            f.core.handleComputeFields(jan1+80)
            assertEquals(year-78,f.fields[19]); assertEquals(0,f.fields[2]); assertEquals(1,f.fields[5]); assertEquals(1,f.fields[6])
            val firstLength=if(year==2024) 31 else 30
            f.core.handleComputeFields(jan1+80+firstLength)
            assertEquals(1,f.fields[2]); assertEquals(1,f.fields[5])
            f.core.handleComputeFields(jan1+80+firstLength+155)
            assertEquals(6,f.fields[2]); assertEquals(1,f.fields[5])
            assertEquals(0,f.fields[0]); assertEquals(f.fields[2],f.fields[23])
        }
    }

    @Test fun gregorianIntegerAdditionPrecedesDoubleConversionAndSaturation() {
        val f=Fixture(); f.gregorian={ _,_ -> Int.MAX_VALUE }
        f.core.handleComputeFields(Int.MIN_VALUE)
        assertEquals(1945,f.fields[1]); assertEquals(286,f.fields[6])
        f.core.handleComputeFields(Int.MAX_VALUE)
        assertEquals(1946,f.fields[1]); assertEquals(Int.MAX_VALUE-79,f.fields[6])
        f.trace.clear(); assertEquals(Int.MIN_VALUE+21,f.core.handleComputeMonthStart(1945,0,true))
        assertEquals(listOf("gregorian:2023:2"),f.trace)
    }

    @Test fun callbackOrderAndPartialWritesOnFailure() {
        val f=Fixture(); val jan1=CalendarDateCore.computeGregorianMonthStart(2024,0)+1
        f.core.handleComputeFields(jan1+80)
        assertEquals(listOf("gregorianYear","gregorian:2024:0","set:0:0","set:19:1946","set:1:1946","set:2:0","set:23:0","set:5:1","set:6:1"),f.trace)
        val broken=Fixture(); broken.failField=19
        assertFailsWith<IllegalStateException> { broken.core.handleComputeFields(jan1+80) }
        assertTrue(broken.set[0]); assertFalse(broken.set[19]); assertFalse(broken.set[1])
        assertEquals(listOf("gregorianYear","gregorian:2024:0","set:0:0","set:19:1946"),broken.trace)
    }
}
