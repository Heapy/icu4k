@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import com.ibm.icu.util.GregorianCalendar as Source
import com.ibm.icu.util.SimpleTimeZone as SourceZone
import com.ibm.icu.util.ULocale as SourceLocale
import kotlin.test.*

class CalendarDiagnosticsOracleTest {
    private fun normalize(value: String) = value.replace("com.ibm.icu.","io.github.heapy.icu4k.")
    @Test fun exactStateDumpDoesNotCompleteFields() {
        val a=Source(SourceZone(19800000,"test"),SourceLocale.US)
        val b=GregorianCalendar(SimpleTimeZone(19800000,"test"),ULocale.US)
        fun check() {
            val beforeA=(0 until a.fieldCount).map { a.isSet(it) };val beforeB=(0 until b.getFieldCount()).map { b.isSet(it) }
            assertEquals(normalize(a.toString()),b.toString())
            assertEquals(beforeA,(0 until a.fieldCount).map { a.isSet(it) });assertEquals(beforeB,(0 until b.getFieldCount()).map { b.isSet(it) })
        }
        a.clear();b.clear();check()
        a.set(2024,1,30);b.set(2024,1,30);a.isLenient=false;b.setLenient(false);check()
        a.clear();b.clear();a.timeInMillis=0;b.setTimeInMillis(0);check()
        a.get(Calendar.YEAR);b.get(Calendar.YEAR);check()
        a.set(Calendar.MINUTE,17);b.set(Calendar.MINUTE,17);check()
        a.repeatedWallTimeOption=Source.WALLTIME_FIRST;b.setRepeatedWallTimeOption(Calendar.WALLTIME_FIRST)
        a.skippedWallTimeOption=Source.WALLTIME_NEXT_VALID;b.setSkippedWallTimeOption(Calendar.WALLTIME_NEXT_VALID);check()
        a.timeZone=null;b.setTimeZone(null);check()
    }
    @Test fun displayNamesIgnoreLocales() {
        val a=Source(SourceZone(0,"test"),SourceLocale.ROOT);val b=GregorianCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT)
        for(id in listOf("en","ja","ar")) assertEquals(normalize(a.getDisplayName(SourceLocale(id))),b.getDisplayName(ULocale(id)))
        assertEquals(normalize(a.getDisplayName(null as SourceLocale?)),b.getDisplayName(null as ULocale?))
        assertEquals(normalize(a.getDisplayName(null as java.util.Locale?)),b.getDisplayName(null as java.util.Locale?))
    }
    private class SourceRanges:Source(SourceZone(0,"test"),SourceLocale.ROOT) {
        fun bounds()=listOf(MIN_JULIAN.toLong(),MAX_JULIAN.toLong(),MIN_MILLIS,MAX_MILLIS)
        fun minDate()=MIN_DATE
        fun maxDate()=MAX_DATE
    }
    private class PortRanges:GregorianCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT) {
        fun bounds()=listOf(MIN_JULIAN.toLong(),MAX_JULIAN.toLong(),MIN_MILLIS,MAX_MILLIS)
        fun minDate()=MIN_DATE
        fun maxDate()=MAX_DATE
    }
    @Test fun sharedProtectedDateBoundsAndGregorianMinimums() {
        val a=SourceRanges();val b=PortRanges()
        assertEquals(a.bounds(),b.bounds());assertEquals(a.minDate().time,b.minDate().getTime());assertEquals(a.maxDate().time,b.maxDate().getTime())
        assertSame(b.minDate(),PortRanges().minDate());assertSame(b.maxDate(),PortRanges().maxDate())
        val oldA=a.minDate().time;val oldB=b.minDate().getTime()
        try { a.minDate().time=123;b.minDate().setTime(123);assertEquals(a.minDate().time,PortRanges().minDate().getTime()) }
        finally { a.minDate().time=oldA;b.minDate().setTime(oldB) }
        a.clear();b.clear();a.set(2024,1,30);b.set(2024,1,30);a.isLenient=false;b.setLenient(false)
        for(f in 0 until a.fieldCount) assertEquals(a.getActualMinimum(f),b.getActualMinimum(f))
    }
}
