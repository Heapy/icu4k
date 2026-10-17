@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import kotlin.test.*

class JapaneseCalendarTest {
    private fun calendar() = JapaneseCalendar(SimpleTimeZone(0, "custom"), ULocale.JAPAN)
    @Test fun modernEraBoundariesAndDefaults() {
        val calendar = calendar()
        calendar.clear(); calendar.set(Calendar.ERA, JapaneseCalendar.REIWA); calendar.set(Calendar.YEAR, 1)
        assertEquals(2019, calendar.get(Calendar.EXTENDED_YEAR)); assertEquals(Calendar.MAY, calendar.get(Calendar.MONTH)); assertEquals(1, calendar.get(Calendar.DATE))
        calendar.add(Calendar.DATE, -1)
        assertEquals(JapaneseCalendar.HEISEI, calendar.get(Calendar.ERA)); assertEquals(31, calendar.get(Calendar.YEAR))
        calendar.clear(); calendar.set(Calendar.ERA, JapaneseCalendar.HEISEI); calendar.set(Calendar.YEAR, 1)
        assertEquals(Calendar.JANUARY, calendar.get(Calendar.MONTH)); assertEquals(8, calendar.get(Calendar.DATE))
        assertEquals(31, calendar.getActualMaximum(Calendar.YEAR)); assertFalse(calendar.haveDefaultCentury())
    }
    @Test fun constructorsUseDefaultZoneAndSetOnlySourceFields() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setICUDefault(SimpleTimeZone(19800000, "private-default"))
            val fields = JapaneseCalendar(JapaneseCalendar.REIWA, 1, Calendar.MAY, 1)
            assertEquals("private-default", fields.getTimeZone()!!.getID())
            assertTrue(fields.isSet(Calendar.ERA)); assertFalse(fields.isSet(Calendar.HOUR_OF_DAY)); assertFalse(fields.isSet(Calendar.MILLISECOND))
            assertEquals(1556649000000L, fields.getTimeInMillis())
            val locale = JapaneseCalendar(ULocale("ja@timezone=Asia/Tokyo"))
            assertEquals("Asia/Tokyo", locale.getTimeZone()!!.getID())
            val date = JapaneseCalendar(Date(0))
            assertEquals(0, date.getTimeInMillis()); assertEquals(JapaneseCalendar.SHOWA, date.get(Calendar.ERA))
            val gregorian = GregorianCalendar(2024, Calendar.FEBRUARY, 29, 12, 34)
            assertFalse(gregorian.isSet(Calendar.SECOND)); assertEquals(0, gregorian.get(Calendar.SECOND))
        } finally { TimeZone.setICUDefault(previous) }
    }
    @Test fun clonesRetainRuntimeTypeGregorianCutoverAndIndependentOwners() {
        val original = calendar().apply { clear(); set(Calendar.ERA, JapaneseCalendar.REIWA); set(1, Calendar.MAY, 31) }
        val copy = original.clone()
        assertIs<JapaneseCalendar>(copy); assertEquals(original, copy)
        assertNotSame(original.getTimeZone(), copy.getTimeZone())
        copy.add(Calendar.MONTH, 1)
        assertEquals(Calendar.MAY, original.get(Calendar.MONTH)); assertEquals(Calendar.JUNE, copy.get(Calendar.MONTH))
        assertEquals(31, original.get(Calendar.DATE)); assertEquals(30, copy.get(Calendar.DATE))
        assertEquals(original.getGregorianChange(), copy.getGregorianChange())
    }
    @Test fun overridesAreAvailableInBaseConstructorCallbacks() {
        val observed = arrayListOf<String>()
        val calendar = object : JapaneseCalendar(SimpleTimeZone(0, "custom"), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar {
                observed.add("${getType()}:${getMinimum(Calendar.ERA)}:${getMaximum(Calendar.ERA)}:${getMinimum(Calendar.YEAR)}")
                return super.setWeekData(data)
            }
        }
        assertEquals(listOf("japanese:0:${calendar.getMaximum(Calendar.ERA)}:1"), observed)
    }
}
