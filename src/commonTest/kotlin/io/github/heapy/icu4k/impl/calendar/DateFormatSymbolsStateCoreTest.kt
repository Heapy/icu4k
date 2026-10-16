package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DateFormatSymbolsStateCoreTest {
    private fun owner()=object: DateFormatSymbolsDataCore.Owner {
        override fun className()="test.Symbols"
        override fun currentTimeMillis()=0L
        override fun defaultZoneOffset(time: Long)=0
    }
    private fun state(type: String="gregorian")=DateFormatSymbolsStateCore(DateFormatSymbolsDataCore(owner(),ICUResourceBundleReader.embeddedLoader).also { it.initialize(ULocale.US,null,type) })
    @Test fun defensiveArraysContextAsymmetryAndLegacyWeekdays() {
        val state=state(); val input=arrayOf<String?>("month",null)
        state.setMonths(input); input[0]="input"
        val result=state.getMonths(); result[0]="output"
        assertContentEquals(arrayOf<String?>("month",null),state.getMonths())
        val abbreviated=state.getMonths(0,0)
        state.setMonths(null,0,3); assertContentEquals(abbreviated,state.getMonths(0,3))
        state.setMonths(null,99,1)
        assertFailsWith<NullPointerException> { state.setMonths(null,0,1) }
        assertFailsWith<IllegalArgumentException> { state.getMonths(2,1) }
        state.data.fields["shorterWeekdays"]=null
        assertContentEquals(state.getShortWeekdays(),state.getWeekdays(0,3))
        state.setWeekdays(arrayOf("short"),0,3); assertContentEquals(arrayOf<String?>("short"),state.getWeekdays(0,3))
        state.setAmPmStrings(arrayOf("am","pm"),99,-1)
        assertContentEquals(arrayOf<String?>("am","pm"),state.getAmPmStrings(Int.MIN_VALUE,Int.MAX_VALUE))
    }
    @Test fun cyclicAndLeapPoliciesPreserveStandaloneAbbreviatedSourceSlot() {
        val state=state("chinese")
        assertContentEquals(state.getYearNames(0,0),state.getYearNames(Int.MIN_VALUE,Int.MAX_VALUE))
        state.setYearNames(null,1,0)
        assertNotNull(state.getYearNames(99,99))
        state.setYearNames(arrayOf("year"),0,0); assertContentEquals(arrayOf<String?>("year"),state.getYearNames(-1,-1))
        val standalone=state.data.fields["leapMonthPatterns"]!![4]
        state.setLeapMonthPattern("format",0,0); state.setLeapMonthPattern("standalone",1,0)
        assertEquals("standalone",state.getLeapMonthPattern(0,0)); assertEquals("standalone",state.getLeapMonthPattern(1,3))
        assertEquals(standalone,state.data.fields["leapMonthPatterns"]!![4])
        state.setLeapMonthPattern("ignored",1,3); assertEquals("standalone",state.getLeapMonthPattern(1,0))
        state.setLeapMonthPattern("numeric",2,Int.MAX_VALUE); assertEquals("numeric",state.getLeapMonthPattern(2,Int.MIN_VALUE))
        val ordinary=state(); ordinary.setLeapMonthPattern("ignored",2,0); assertNull(ordinary.getLeapMonthPattern(-1,-1))
    }
    @Test fun stateCopySharesArraysButNotFieldSlotsAndDoesNotInitializeResources() {
        val original=state("chinese")
        original.setZoneStrings(arrayOf(arrayOf("id","name")))
        val blank=DateFormatSymbolsDataCore(object: DateFormatSymbolsDataCore.Owner {
            override fun className()="test.Symbols"
            override fun currentTimeMillis(): Long=error("unexpected initialization")
            override fun defaultZoneOffset(time: Long): Int=error("unexpected initialization")
        },ICUResourceBundleReader.embeddedLoader)
        val copy=DateFormatSymbolsStateCore(blank); copy.initializeFrom(original)
        assertSame(original.data.fields["months"],copy.data.fields["months"])
        assertSame(original.data.capitalization,copy.data.capitalization); assertSame(original.storedZoneStrings,copy.storedZoneStrings)
        copy.setMonths(arrayOf("copy")); assertNotEquals("copy",original.getMonths()[0])
        copy.setLeapMonthPattern("shared",0,0); assertEquals("shared",original.getLeapMonthPattern(0,0))
        copy.setTimeSeparatorString("copy"); assertEquals(":",original.getTimeSeparatorString())
    }
    @Test fun customZonesCopyRowsAndRetainOldValueOnFailure() {
        val state=state(); assertFailsWith<NullPointerException> { state.copyZoneStrings() }
        val input=arrayOf<Array<String?>?>(arrayOf("id",null),emptyArray())
        state.setZoneStrings(input); input[0]!![0]="input"
        val result=state.copyZoneStrings(); result[0]!![0]="output"
        assertEquals("id",state.copyZoneStrings()[0]!![0])
        val before=state.storedZoneStrings
        assertFailsWith<NullPointerException> { state.setZoneStrings(arrayOf(arrayOf("first"),null)) }
        assertSame(before,state.storedZoneStrings)
    }
    @Test fun equalitySkipsOmittedFieldsAndPreservesDispatchAndShortCircuitOrder() {
        val a=state(); val b=state(); val self=Any(); val other=Any(); val trace=ArrayList<String>()
        fun same()=a.equalsObject(self,other,{ trace.add("class"); true },{ trace.add("state"); b },{ trace.add("display:$it"); it.toString() })
        assertTrue(a.equalsObject(self,self,{ error("class") },{ error("state") },{ error("display") }))
        assertFalse(a.equalsObject(self,null,{ error("class") },{ error("state") },{ error("display") }))
        assertTrue(same()); assertEquals(listOf("class","state","display:en_US","display:en_US"),trace)
        b.setQuarters(arrayOf("changed"),0,1); b.data.fields["leapMonthPatterns"]=arrayOf("changed")
        assertTrue(same()); assertEquals(a.hashCodeState(),b.hashCodeState())
        b.setMonths(arrayOf("changed")); trace.clear(); assertFalse(same()); assertEquals(listOf("class","state"),trace)
        assertFalse(a.equalsObject(self,other,{ false },{ error("state") },{ error("display") }))
    }
    @Test fun defaultAmPmAccessDispatchesToOverloadedMethods() {
        val state=object: DateFormatSymbolsStateCore(DateFormatSymbolsDataCore(owner(),ICUResourceBundleReader.embeddedLoader)) {
            val trace=ArrayList<Pair<Int,Int>>()
            override fun getAmPmStrings(context: Int,width: Int): Array<String?> { trace.add(context to width); return arrayOf("override") }
            override fun setAmPmStrings(value: Array<String?>?,context: Int,width: Int) { trace.add(context to width) }
        }
        assertContentEquals(arrayOf<String?>("override"),state.getAmPmStrings()); state.setAmPmStrings(null)
        assertEquals(listOf(0 to 0,0 to 0),state.trace)
    }
}
