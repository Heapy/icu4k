@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util
import kotlin.test.*

class CalendarConstructorsTest {
    @Test fun numericConstructorsSetOnlySuppliedFieldsAndClone() {
        val calendars=listOf<Calendar>(
            BuddhistCalendar(2000,1,3),
            TaiwanCalendar(2000,1,3),
            PersianCalendar(2000,1,3),
            IndianCalendar(2000,1,3),
            HebrewCalendar(2000,1,3),
            CopticCalendar(2000,1,3),
            EthiopicCalendar(2000,1,3))
        for(calendar in calendars) {
            assertFalse(calendar.isSet(Calendar.HOUR_OF_DAY));assertFalse(calendar.isSet(Calendar.MILLISECOND))
            val copy=calendar.clone();assertEquals(calendar,copy)
            copy.add(Calendar.DATE,1);assertEquals(3,calendar.get(Calendar.DATE));assertEquals(4,copy.get(Calendar.DATE))
        }
    }
    @Test fun ethiopicLocaleSelectionDoesNotApplyToNumericOrDateConstructors() {
        val previous=ULocale.getDefault(ULocale.Category.FORMAT)
        try {
            ULocale.setDefault(ULocale.Category.FORMAT,ULocale("am_ET@calendar=ethiopic-amete-alem"))
            assertTrue(EthiopicCalendar().isAmeteAlemEra())
            assertFalse(EthiopicCalendar(2000,1,3).isAmeteAlemEra())
            assertFalse(EthiopicCalendar(Date(0)).isAmeteAlemEra())
            assertEquals(0,EthiopicCalendar(Date(0)).getTimeInMillis())
        } finally { ULocale.setDefault(ULocale.Category.FORMAT,previous) }
    }
}
