@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.CalType
import kotlin.test.*

class CalendarFactoryTest {
    @Test fun everyCalendarTypeConstructsAndClones() {
        for(type in CalType.entries) {
            val c=Calendar.getInstance(SimpleTimeZone(0,"test"),ULocale("en@calendar=${type.getId()}"))
            c.setTimeInMillis(1700000000000L)
            val copy=c.clone();assertEquals(c.getType(),copy.getType());assertEquals(c,copy)
            copy.add(Calendar.DATE,1);assertNotEquals(c.getTimeInMillis(),copy.getTimeInMillis())
        }
    }
    @Test fun isoWeeksAndExplicitZoneIdentity() {
        val zone=SimpleTimeZone(0,"explicit")
        val iso=Calendar.getInstance(zone,ULocale("en_US@calendar=iso8601;timezone=Asia/Tokyo"))
        assertIs<GregorianCalendar>(iso);assertSame(zone,iso.getTimeZone())
        assertEquals(Calendar.MONDAY,iso.getFirstDayOfWeek());assertEquals(4,iso.getMinimalDaysInFirstWeek())
        assertEquals(Calendar.SUNDAY,Calendar.getInstance(ULocale("en_US@calendar=iso8601;fw=sun")).getFirstDayOfWeek())
        assertEquals(Calendar.MONDAY,Calendar.getInstance(ULocale("en_US@calendar=iso8601;rg=gbzzzz")).getFirstDayOfWeek())
        assertIs<GregorianCalendar>(Calendar.getInstance(ULocale("en@calendar=unknown")))
        assertTrue((Calendar.getInstance(ULocale("en@calendar=ethiopic-amete-alem")) as EthiopicCalendar).isAmeteAlemEra())
        assertEquals(IslamicCalendar.CalculationType.ISLAMIC,(Calendar.getInstance(ULocale("en@calendar=islamic-rgsa")) as IslamicCalendar).getCalculationType())
    }
}
