package io.github.heapy.icu4k.impl.calendar

import kotlin.test.*

class HebrewCalendarCoreTest {
    private class Fixture : HebrewCalendarCore.Owner {
        val fields=IntArray(24)
        val set=BooleanArray(24)
        val trace=ArrayList<String>()
        var preferred=19
        var resolution=2
        var failField: Int?=null
        var yearLengthOverride: Int?=null
        val core=HebrewCalendarCore(this)
        override fun get(field: Int): Int { trace.add("get:$field"); return fields[field] }
        override fun set(field: Int,value: Int) { trace.add("set:$field:$value"); if(failField==field) error("set"); fields[field]=value; set[field]=true }
        override fun pinField(field: Int) { trace.add("pin:$field") }
        override fun baseAdd(field: Int,amount: Int) { trace.add("add:$field:$amount") }
        override fun baseRoll(field: Int,amount: Int) { trace.add("roll:$field:$amount") }
        override fun handleGetYearLength(year: Int): Int { trace.add("length:$year"); return yearLengthOverride ?: core.handleGetYearLength(year) }
        override fun handleGetExtendedYear(): Int { trace.add("extended"); return core.handleGetExtendedYear() }
        override fun internalGetMonth(): Int { trace.add("month"); return core.internalGetMonth() }
        override fun baseInternalGetMonth(): Int { trace.add("baseMonth"); return fields[2] }
        override fun baseValidateField(field: Int) { trace.add("validate:$field") }
        override fun resolveMonthField(): Int { trace.add("resolve"); return resolution }
        override fun internalGet(field: Int): Int { trace.add("internal:$field"); return fields[field] }
        override fun internalGet(field: Int,defaultValue: Int): Int { trace.add("internal:$field:$defaultValue"); return if(set[field]) fields[field] else defaultValue }
        override fun newerField(defaultField: Int,alternateField: Int): Int { trace.add("newer"); return preferred }
        override fun internalSet(field: Int,value: Int) { trace.add("internalSet:$field:$value"); if(failField==field) error("set"); fields[field]=value; set[field]=true }
    }
    @Test fun yearTypesMonthStartsAndRoundTrips() {
        val f=Fixture()
        for(year in 5770..5790) {
            val leap=HebrewCalendarCore.isLeapYear(year)
            val length=f.core.handleGetYearLength(year)
            assertTrue(length in if(leap) 383..385 else 353..355)
            for(month in 0..12) {
                if(month==5 && !leap) continue
                val start=f.core.handleComputeMonthStart(year,month,true)
                f.core.handleComputeFields(start+1)
                assertEquals(year,f.fields[1]); assertEquals(month,f.fields[2]); assertEquals(1,f.fields[5])
                assertEquals(if(!leap && month>5) month-1 else month,f.fields[23])
            }
        }
        assertEquals(12,f.core.handleGetLimit(23,3)); assertEquals(11,f.core.handleGetLimit(23,2))
        assertFailsWith<IndexOutOfBoundsException> { f.core.handleGetLimit(4,0) }
        f.yearLengthOverride=356
        assertEquals("Illegal year length 356 in year 5783",assertFailsWith<IllegalArgumentException> { f.core.handleGetMonthLength(5783,1) }.message)
    }
    @Test fun adarAddRollZeroAndFailuresRetainSourceOrder() {
        val f=Fixture(); f.fields[1]=5783; f.fields[2]=4
        f.core.add(2,1)
        assertEquals(6,f.fields[2]); assertEquals(listOf("get:2","get:1","set:2:6","set:1:5783","pin:5"),f.trace)
        f.trace.clear(); f.core.add(23,0)
        assertEquals(listOf("get:2","get:1","set:2:6","set:1:5783","pin:5"),f.trace)
        f.trace.clear(); f.core.roll(2,-1); assertEquals(4,f.fields[2])
        f.fields[1]=5784; f.fields[2]=4; f.core.add(2,1); assertEquals(5,f.fields[2])
        f.trace.clear(); f.failField=1
        assertFailsWith<IllegalStateException> { f.core.add(2,1) }
        assertEquals(6,f.fields[2]); assertFalse("pin:5" in f.trace)
        f.trace.clear(); f.core.add(1,0); f.core.roll(5,0)
        assertEquals(listOf("add:1:0","roll:5:0"),f.trace)
    }
    @Test fun temporalCodesAndOrdinalMonthGapPolicy() {
        val f=Fixture(); f.core.setTemporalMonthCode("M05L")
        assertEquals(listOf("set:2:5"),f.trace); assertEquals("M05L",f.core.getTemporalMonthCode())
        assertFailsWith<NullPointerException> { f.core.setTemporalMonthCode(null) }
        assertEquals("Incorrect temporal Month code: M13",assertFailsWith<IllegalArgumentException> { f.core.setTemporalMonthCode("M13") }.message)
        f.fields[2]=13; assertFailsWith<IndexOutOfBoundsException> { f.core.getTemporalMonthCode() }
        f.fields[19]=5783; f.set[19]=true; f.resolution=23
        f.fields[23]=5; assertEquals(5,f.core.internalGetMonth())
        f.fields[23]=6; assertEquals(7,f.core.internalGetMonth())
        f.fields[19]=5784; assertEquals(6,f.core.internalGetMonth()); assertTrue(f.core.inTemporalLeapYear())
    }
    @Test fun validationDefaultsCycleAndPartialFieldWrites() {
        val f=Fixture(); assertEquals(1,f.core.handleGetExtendedYear())
        f.fields[19]=5783; f.set[19]=true; f.fields[2]=5
        assertEquals("MONTH cannot be ADAR_1(5) except leap years",assertFailsWith<IllegalArgumentException> { f.core.validateField(2) }.message)
        f.fields[19]=5784; f.core.validateField(2); assertEquals("validate:2",f.trace.last())
        for(year in -100..100) assertEquals(HebrewCalendarCore.isLeapYear(year),HebrewCalendarCore.isLeapYear(year+19))
        assertEquals(-3760,f.core.getRelatedYearDifference()); assertEquals("hebrew",f.core.getType())
        val start=f.core.handleComputeMonthStart(5784,0,true)
        val broken=Fixture(); broken.failField=19
        assertFailsWith<IllegalStateException> { broken.core.handleComputeFields(start+1) }
        assertTrue(broken.set[0]); assertTrue(broken.set[1]); assertFalse(broken.set[19]); assertFalse(broken.set[2])
        assertEquals(listOf("internalSet:0:0","internalSet:1:5784","internalSet:19:5784"),broken.trace.filter { it.startsWith("internalSet:") })
    }
}
