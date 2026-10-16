package io.github.heapy.icu4k.impl.calendar

import kotlin.test.*

class GregorianVariantCoreTest {
    private class Fixture : BuddhistCalendarCore.Owner,TaiwanCalendarCore.Owner {
        val fields=IntArray(24)
        val set=BooleanArray(24)
        val trace=ArrayList<String>()
        var newer: (Int,Int)->Int = { default,_ -> default }
        var failBase=false
        var failSet: Int?=null
        val buddhist=BuddhistCalendarCore(this)
        val taiwan=TaiwanCalendarCore(this)
        override fun newerField(defaultField: Int,alternateField: Int): Int { trace.add("newer:$defaultField:$alternateField"); return newer(defaultField,alternateField) }
        override fun internalGet(field: Int): Int { trace.add("get:$field"); return fields[field] }
        override fun internalGet(field: Int,defaultValue: Int): Int { trace.add("get:$field:$defaultValue"); return if(set[field]) fields[field] else defaultValue }
        override fun internalSet(field: Int,value: Int) { trace.add("set:$field:$value"); if(failSet==field) error("set:$field"); fields[field]=value; set[field]=true }
        override fun gregorianComputeFields(julianDay: Int) { trace.add("base:$julianDay"); fields[19]=julianDay; if(failBase) error("base") }
        override fun gregorianComputeMonthStart(year: Int,month: Int,useMonth: Boolean): Int { trace.add("month:$year:$month:$useMonth"); return 777 }
        override fun gregorianGetLimit(field: Int,limitType: Int): Int { trace.add("limit:$field:$limitType"); return 123 }
    }

    @Test fun buddhistEpochDefaultsPrecedenceAndIntegerOverflow() {
        val f=Fixture()
        assertEquals(1970,f.buddhist.handleGetExtendedYear())
        assertEquals(listOf("newer:19:1","get:19:1970"),f.trace)
        f.trace.clear(); f.newer={ _,alternate -> alternate }
        assertEquals(1970,f.buddhist.handleGetExtendedYear())
        assertEquals(listOf("newer:19:1","get:1:2513"),f.trace)
        f.set[1]=true; f.fields[1]=Int.MIN_VALUE
        assertEquals(Int.MAX_VALUE-542,f.buddhist.handleGetExtendedYear())
        f.trace.clear(); f.buddhist.handleComputeFields(Int.MAX_VALUE)
        assertEquals(Int.MIN_VALUE+542,f.fields[1]); assertEquals(0,f.fields[0])
        assertEquals(listOf("base:${Int.MAX_VALUE}","get:19","set:0:0","set:1:${Int.MIN_VALUE+542}"),f.trace)
        assertFalse(f.buddhist.isEra0CountingBackward())
        assertEquals("buddhist",f.buddhist.getType())
    }

    @Test fun taiwanPrecedenceShortCircuitAndEraDefaults() {
        val f=Fixture()
        assertEquals(1970,f.taiwan.handleGetExtendedYear())
        assertEquals(listOf("newer:19:1","newer:19:0","get:19:1970"),f.trace)
        f.trace.clear(); f.newer={ _,alternate -> alternate }
        assertEquals(1912,f.taiwan.handleGetExtendedYear())
        assertEquals(listOf("newer:19:1","get:0:1","get:1:1"),f.trace)
        f.trace.clear(); f.newer={ default,alternate -> if(alternate==0) 0 else default }
        f.set[0]=true; f.fields[0]=0; f.set[1]=true; f.fields[1]=2
        assertEquals(1910,f.taiwan.handleGetExtendedYear())
        assertEquals(listOf("newer:19:1","newer:19:0","get:0:1","get:1:1"),f.trace)
        f.fields[0]=9
        assertEquals(1910,f.taiwan.handleGetExtendedYear())
        f.fields[1]=Int.MIN_VALUE
        assertEquals(Int.MIN_VALUE+1912,f.taiwan.handleGetExtendedYear())
    }

    @Test fun taiwanEraBoundaryAndOverflowFieldConversion() {
        val f=Fixture()
        for(year in listOf(1910,1911,1912,1970)) {
            f.trace.clear(); f.taiwan.handleComputeFields(year)
            assertEquals(if(year>1911) 1 else 0,f.fields[0])
            assertEquals(if(year>1911) year-1911 else 1912-year,f.fields[1])
            assertEquals(listOf("base:$year","get:19","set:0:${f.fields[0]}","set:1:${f.fields[1]}"),f.trace)
        }
        f.taiwan.handleComputeFields(Int.MIN_VALUE)
        assertEquals(1,f.fields[0]); assertEquals(Int.MAX_VALUE-1910,f.fields[1])
        assertEquals("roc",f.taiwan.getType())
    }

    @Test fun eraLimitsBypassBaseAndBuddhistMonthStartDelegatesExactly() {
        val f=Fixture()
        for(type in listOf(Int.MIN_VALUE,-1,0,1,2,3,4,Int.MAX_VALUE)) {
            assertEquals(0,f.buddhist.handleGetLimit(0,type))
            assertEquals(if(type==0 || type==1) 0 else 1,f.taiwan.handleGetLimit(0,type))
        }
        assertTrue(f.trace.isEmpty())
        assertEquals(123,f.buddhist.handleGetLimit(-1,5))
        assertEquals(123,f.taiwan.handleGetLimit(24,-1))
        assertEquals(777,f.buddhist.handleComputeMonthStart(Int.MIN_VALUE,Int.MAX_VALUE,false))
        assertEquals(listOf("limit:-1:5","limit:24:-1","month:${Int.MIN_VALUE}:${Int.MAX_VALUE}:false"),f.trace)
    }

    @Test fun baseAndInternalSetFailuresPreservePartialFields() {
        val f=Fixture(); f.failBase=true
        assertFailsWith<IllegalStateException> { f.buddhist.handleComputeFields(1970) }
        assertEquals(listOf("base:1970"),f.trace)
        assertEquals(1970,f.fields[19]); assertFalse(f.set[0]); assertFalse(f.set[1])
        f.trace.clear(); f.failBase=false; f.failSet=1
        assertFailsWith<IllegalStateException> { f.taiwan.handleComputeFields(1970) }
        assertEquals(1,f.fields[0]); assertTrue(f.set[0]); assertFalse(f.set[1])
        assertEquals(listOf("base:1970","get:19","set:0:1","set:1:59"),f.trace)
    }
}
