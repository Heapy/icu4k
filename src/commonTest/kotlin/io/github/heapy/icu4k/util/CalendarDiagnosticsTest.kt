package io.github.heapy.icu4k.util
import kotlin.test.*

class CalendarDiagnosticsTest {
    @Test fun debugDumpPreservesUnresolvedAndVirtuallySetFields() {
        val c=GregorianCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT)
        c.clear();c.setLenient(false);c.set(2024,1,30)
        val dump=c.toString()
        assertTrue("[time=?" in dump);assertTrue(",YEAR=2024" in dump);assertTrue(",HOUR_OF_DAY=?" in dump)
        assertFalse(c.isSet(Calendar.HOUR_OF_DAY))
        assertEquals(c.getDisplayName(null),dump.substringBefore('['))
        c.setTimeInMillis(0)
        assertTrue(",YEAR=0" in c.toString());assertEquals(1970,c.get(Calendar.YEAR))
        assertTrue(",YEAR=1970" in c.toString())
        c.setTimeZone(null);assertTrue(",zone=null," in c.toString())
    }
    @Test fun anonymousCalendarsCanReportTheirRuntimeClass() {
        val calendar=object:GregorianCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT) {}
        assertTrue(calendar.getDisplayName(null).isNotEmpty())
        assertTrue(calendar.toString().startsWith(calendar.getDisplayName(null)+"[time="))
    }
    private class Ranges:GregorianCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT) {
        fun min()=MIN_DATE
        fun max()=MAX_DATE
        fun bounds()=listOf(MIN_JULIAN.toLong(),MAX_JULIAN.toLong(),MIN_MILLIS,MAX_MILLIS)
    }
    @Test fun protectedRangesRetainSharedDateReferences() {
        val c=Ranges();assertSame(c.min(),Ranges().min());assertSame(c.max(),Ranges().max())
        assertEquals(listOf(-2130706432L,2130706432L,-184303902528000000L,183882168921600000L),c.bounds())
        assertEquals(c.bounds()[2],c.min().getTime());assertEquals(c.bounds()[3],c.max().getTime())
    }
}
