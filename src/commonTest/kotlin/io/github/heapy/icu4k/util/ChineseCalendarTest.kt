@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util
import kotlin.test.*

class ChineseCalendarTest {
    @Test fun leapMonthAndCloneOwners() {
        for(calendar in listOf(ChineseCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT),DangiCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT))) {
            calendar.setTimeInMillis(1679443200000L)
            assertEquals(1,calendar.get(Calendar.IS_LEAP_MONTH));assertEquals("M02L",calendar.getTemporalMonthCode())
            val copy=calendar.clone() as ChineseCalendar
            assertEquals(calendar.getType(),copy.getType());assertEquals(calendar,copy)
            copy.add(Calendar.MONTH,1)
            assertEquals("M03",copy.getTemporalMonthCode());assertEquals("M02L",calendar.getTemporalMonthCode())
            copy.setTemporalMonthCode("M01");assertEquals(0,copy.get(Calendar.IS_LEAP_MONTH))
        }
    }
    @Test fun constructorsAndAstronomicalZoneAreIndependentOfDisplayZone() {
        val fields=ChineseCalendar(78,40,1,1,1)
        assertTrue(fields.isSet(Calendar.MILLISECOND));assertEquals(0,fields.get(Calendar.MILLISECOND))
        assertEquals(0,fields.get(Calendar.HOUR_OF_DAY))
        val a=ChineseCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT)
        val b=ChineseCalendar(SimpleTimeZone(19800000,"test"),ULocale.ROOT)
        a.setTimeInMillis(1679443200000L);b.setTimeInMillis(1679443200000L)
        assertEquals(a.getTemporalMonthCode(),b.getTemporalMonthCode())
        assertEquals("Asia/Tokyo",ChineseCalendar(ULocale("en@timezone=Asia/Tokyo")).getTimeZone()!!.getID())
        assertEquals(0,DangiCalendar(Date(0)).getTimeInMillis())
    }
}
