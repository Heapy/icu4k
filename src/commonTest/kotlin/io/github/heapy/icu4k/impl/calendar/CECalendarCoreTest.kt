package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CECalendarCoreTest {
    private class Fixture : CECalendarCore.Owner,CopticCalendarCore.Owner,EthiopicCalendarCore.Owner {
        val fields=IntArray(24)
        val set=BooleanArray(24)
        val trace=ArrayList<String>()
        var useEthiopic=false
        var preferYear=false
        var modeOverride: Boolean?=null
        var failSet: Int?=null
        var eraHook: (() -> Unit)?=null
        var c=CopticCalendarCore(this)
        var e=EthiopicCalendarCore(this)
        val ce=CECalendarCore(this)
        override fun newerField(defaultField: Int,alternateField: Int): Int { trace.add("newer"); return if(preferYear) alternateField else defaultField }
        override fun internalGet(field: Int,defaultValue: Int): Int { trace.add("get:$field:$defaultValue"); return if(set[field]) fields[field] else defaultValue }
        override fun getJDEpochOffset(): Int { trace.add("epoch"); return if(useEthiopic) e.getJDEpochOffset() else c.getJDEpochOffset() }
        override fun extendedYearToEra(year: Int): Int { trace.add("era:$year:${fields[19]}"); eraHook?.invoke(); return if(useEthiopic) e.extendedYearToEra(year) else c.extendedYearToEra(year) }
        override fun extendedYearToYear(year: Int): Int { trace.add("year:$year"); return if(useEthiopic) e.extendedYearToYear(year) else c.extendedYearToYear(year) }
        override fun internalSet(field: Int,value: Int) { trace.add("set:$field:$value"); if(failSet==field) error("set:$field"); fields[field]=value; set[field]=true }
        override fun get(field: Int): Int { trace.add("get:$field"); return fields[field] }
        override fun set(field: Int,value: Int) { trace.add("public:$field:$value"); fields[field]=value }
        override fun baseGetTemporalMonthCode(): String { trace.add("baseCode"); return "base" }
        override fun baseSetTemporalMonthCode(code: String?) { trace.add("baseCode:$code"); error("baseCode") }
        override fun isAmeteAlemEra(): Boolean { trace.add("mode"); return modeOverride ?: e.isAmeteAlemEra() }
        override fun setAmeteAlemEra(value: Boolean) { trace.add("mode-set:$value"); e.setAmeteAlemEra(value) }
        override fun baseGetLimit(field: Int,type: Int): Int { trace.add("baseLimit:$field:$type"); return ce.handleGetLimit(field,type) }
    }

    @Test fun conversionCyclesNormalizationAndLongOverflow() {
        for(epoch in listOf(-285019,1723856,1824665)) {
            for(year in -8L..8L) for(month in 0..12) {
                val day=CECalendarCore.ceToJD(year,month,1,epoch)
                val fields=IntArray(3)
                CECalendarCore.jdToCE(day,epoch,fields)
                assertContentEquals(intArrayOf(year.toInt(),month,1),fields)
                assertEquals(day,CECalendarCore.ceToJD(year-1,month+13,1,epoch))
                assertEquals(day,CECalendarCore.ceToJD(year+1,month-13,1,epoch))
            }
            assertEquals(epoch,CECalendarCore.ceToJD(Long.MIN_VALUE,0,1,epoch))
            assertEquals(epoch-366,CECalendarCore.ceToJD(Long.MAX_VALUE,0,1,epoch))
            val fields=IntArray(3)
            CECalendarCore.jdToCE(CECalendarCore.ceToJD(3,12,6,epoch),epoch,fields)
            assertContentEquals(intArrayOf(3,12,6),fields)
        }
        val partial=intArrayOf(123)
        assertFailsWith<IndexOutOfBoundsException> { CECalendarCore.jdToCE(1824665,1824665,partial) }
        assertContentEquals(intArrayOf(0),partial)
        assertFailsWith<NullPointerException> { CECalendarCore.jdToCE(0,0,null) }
    }

    @Test fun monthLengthSignedRemaindersLimitsAndMonthStart() {
        val f=Fixture()
        assertEquals(30,f.ce.handleGetMonthLength(3,11))
        assertEquals(6,f.ce.handleGetMonthLength(3,12))
        assertEquals(5,f.ce.handleGetMonthLength(-1,12))
        assertEquals(4,f.ce.handleGetMonthLength(-3,12))
        assertEquals(4,f.ce.handleGetMonthLength(-3,-1))
        assertEquals(30,f.ce.handleGetMonthLength(-3,Int.MAX_VALUE))
        assertEquals(12,f.ce.handleGetLimit(23,3))
        assertEquals(-1,f.ce.handleGetLimit(8,0))
        assertFailsWith<IndexOutOfBoundsException> { f.ce.handleGetLimit(4,0) }
        assertFailsWith<IndexOutOfBoundsException> { f.ce.handleGetLimit(24,0) }
        val expected=CopticCalendarCore.copticToJD(3,12,0)
        assertEquals(expected,f.ce.handleComputeMonthStart(3,12,false))
        assertEquals(expected,f.ce.handleComputeMonthStart(3,12,true))
    }

    @Test fun fieldCallbacksUseOriginalDateAndPreservePartialWrites() {
        val f=Fixture(); f.eraHook={ f.fields[19]=999 }
        f.ce.handleComputeFields(CopticCalendarCore.copticToJD(-1,12,5))
        assertEquals(listOf("epoch","set:19:-1","era:-1:-1","set:0:0","year:-1","set:1:2","set:2:12","set:23:12","set:5:5","set:6:365"),f.trace)
        assertEquals(999,f.fields[19])
        val broken=Fixture(); broken.failSet=1
        assertFailsWith<IllegalStateException> { broken.ce.handleComputeFields(CopticCalendarCore.copticToJD(3,0,1)) }
        assertEquals(3,broken.fields[19]); assertEquals(1,broken.fields[0]); assertFalse(broken.set[1]); assertFalse(broken.set[2])
    }

    @Test fun temporalThirteenthMonthAndFallbackDispatch() {
        val f=Fixture(); f.fields[2]=12
        assertEquals("M13",f.ce.getTemporalMonthCode()); assertEquals(listOf("get:2"),f.trace)
        f.trace.clear(); f.fields[2]=11
        assertEquals("base",f.ce.getTemporalMonthCode()); assertEquals(listOf("get:2","baseCode"),f.trace)
        f.trace.clear(); f.ce.setTemporalMonthCode("M13")
        assertEquals(listOf("public:2:12","public:22:0"),f.trace)
        f.trace.clear(); assertFailsWith<NullPointerException> { f.ce.setTemporalMonthCode(null) }; assertTrue(f.trace.isEmpty())
        assertFailsWith<IllegalStateException> { f.ce.setTemporalMonthCode("M13L") }; assertEquals(listOf("baseCode:M13L"),f.trace)
    }

    @Test fun copticEraAndEthiopicModePolicies() {
        val f=Fixture()
        assertEquals(1,f.c.handleGetExtendedYear())
        f.preferYear=true; f.set[0]=true; f.fields[0]=0; f.set[1]=true; f.fields[1]=Int.MIN_VALUE
        assertEquals(Int.MIN_VALUE+1,f.c.handleGetExtendedYear())
        f.fields[0]=9; assertEquals(Int.MIN_VALUE,f.c.handleGetExtendedYear())
        assertEquals(0,f.c.extendedYearToEra(0)); assertEquals(1,f.c.extendedYearToYear(0))
        assertTrue(f.c.isEra0CountingBackward()); assertEquals(284,f.c.getRelatedYearDifference())
        f.useEthiopic=true; f.fields[0]=0; f.fields[1]=5501
        assertEquals(1,f.e.handleGetExtendedYear())
        f.e.setAmeteAlemEra(true)
        assertEquals(5501,f.e.handleGetExtendedYear()); assertEquals(-285019,f.e.getJDEpochOffset())
        assertEquals(0,f.e.handleGetLimit(0,Int.MIN_VALUE)); assertEquals("ethiopic-amete-alem",f.e.getType())
        assertEquals(-1,f.e.extendedYearToYear(-1)); assertEquals(0,f.e.extendedYearToEra(1))
        val copy=Fixture(); copy.useEthiopic=true; copy.e=f.e.copyFor(copy)
        f.e.setAmeteAlemEra(false)
        assertTrue(copy.e.isAmeteAlemEra()); assertFalse(f.e.isAmeteAlemEra())
        assertEquals(5499,f.e.extendedYearToYear(-1)); assertEquals(8,f.e.getRelatedYearDifference())
        f.e.setCalcTypeForLocale(ULocale("en@calendar=ethiopic-amete-alem")); assertTrue(f.e.isAmeteAlemEra())
        f.e.setCalcTypeForLocale(ULocale("en@calendar=ethiopic")); assertFalse(f.e.isAmeteAlemEra())
        f.modeOverride=true; assertEquals("ethiopic-amete-alem",f.e.getType())
        assertEquals(CECalendarCore.ceToJD(3,12,6,1723856),EthiopicCalendarCore.EthiopicToJD(3,12,6))
    }
}
