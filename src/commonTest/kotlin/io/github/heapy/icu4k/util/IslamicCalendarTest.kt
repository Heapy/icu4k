@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import kotlin.test.*

class IslamicCalendarTest {
    @Test fun modesAndIndependentClones() {
        for (mode in IslamicCalendar.CalculationType.entries) {
            val calendar = IslamicCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT)
            calendar.setCalculationType(mode); calendar.clear(); calendar.set(1445,IslamicCalendar.RAMADAN,1)
            val copy = calendar.clone() as IslamicCalendar
            assertEquals(mode,copy.getCalculationType()); assertEquals(calendar,copy)
            copy.add(Calendar.DATE,1)
            assertEquals(1,calendar.get(Calendar.DATE)); assertEquals(2,copy.get(Calendar.DATE))
            val time=calendar.getTimeInMillis()
            calendar.setCivil(false); assertEquals(time,calendar.getTimeInMillis())
            assertEquals(IslamicCalendar.CalculationType.ISLAMIC,calendar.getCalculationType())
            calendar.setCivil(true); assertEquals(time,calendar.getTimeInMillis()); assertTrue(calendar.isCivil())
        }
    }
    @Test fun constructorsRetainCivilForFieldsAndDate() {
        val locale=ULocale.getDefault(ULocale.Category.FORMAT)
        val zone=TimeZone.getDefault()
        try {
            ULocale.setDefault(ULocale.Category.FORMAT,ULocale("en@calendar=islamic-umalqura"))
            TimeZone.setICUDefault(SimpleTimeZone(19800000,"private-default"))
            assertEquals(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA,IslamicCalendar().getCalculationType())
            val calendar=IslamicCalendar(1445,8,1)
            assertEquals(IslamicCalendar.CalculationType.ISLAMIC_CIVIL,calendar.getCalculationType())
            assertEquals("private-default",calendar.getTimeZone()!!.getID())
            assertFalse(calendar.isSet(Calendar.ERA)); assertFalse(calendar.isSet(Calendar.HOUR_OF_DAY))
            assertEquals(0,calendar.get(Calendar.HOUR_OF_DAY))
            val date=IslamicCalendar(Date(0))
            assertEquals(IslamicCalendar.CalculationType.ISLAMIC_CIVIL,date.getCalculationType())
            assertEquals(0,date.getTimeInMillis())
            assertEquals("Asia/Tokyo",IslamicCalendar(ULocale("en@timezone=Asia/Tokyo")).getTimeZone()!!.getID())
        } finally { ULocale.setDefault(ULocale.Category.FORMAT,locale); TimeZone.setICUDefault(zone) }
    }
    @Test fun earlyAlgorithmStateAndInitializerReset() {
        var observed=false
        val calendar=object:IslamicCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar {
                assertFailsWith<NullPointerException> { getCalculationType() }
                assertEquals(0,getMinimum(ERA)); observed=true
                setCalculationType(CalculationType.ISLAMIC_TBLA)
                return super.setWeekData(data)
            }
        }
        assertTrue(observed); assertEquals(IslamicCalendar.CalculationType.ISLAMIC_CIVIL,calendar.getCalculationType())
    }
}
