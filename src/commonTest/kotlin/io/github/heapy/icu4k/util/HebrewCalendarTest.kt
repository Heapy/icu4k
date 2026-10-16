package io.github.heapy.icu4k.util

import kotlin.test.*

class HebrewCalendarTest {
    private fun calendar() = HebrewCalendar(SimpleTimeZone(0, "custom"), ULocale("he_IL"))
    @Test fun leapMonthArithmeticAndTemporalCodes() {
        val calendar = calendar()
        calendar.clear(); calendar.set(5784, HebrewCalendar.SHEVAT, 30)
        assertTrue(calendar.inTemporalLeapYear())
        calendar.add(Calendar.MONTH, 1)
        assertEquals("M05L", calendar.getTemporalMonthCode()); assertEquals(30, calendar.get(Calendar.DATE))
        calendar.add(Calendar.ORDINAL_MONTH, 1)
        assertEquals("M06", calendar.getTemporalMonthCode()); assertEquals(29, calendar.get(Calendar.DATE))
        calendar.clear(); calendar.set(5785, HebrewCalendar.SHEVAT, 30)
        assertFalse(calendar.inTemporalLeapYear()); calendar.add(Calendar.MONTH, 1)
        assertEquals(HebrewCalendar.ADAR, calendar.get(Calendar.MONTH)); assertEquals(5, calendar.get(Calendar.ORDINAL_MONTH))
        calendar.roll(Calendar.MONTH, -1); assertEquals(HebrewCalendar.SHEVAT, calendar.get(Calendar.MONTH))
    }
    @Test fun strictValidationRejectsAbsentAdarAndAcceptsLeapMonth() {
        val calendar = calendar(); calendar.setLenient(false)
        calendar.clear(); calendar.set(5785, HebrewCalendar.ADAR_1, 1)
        val error = assertFailsWith<IllegalArgumentException> { calendar.getTimeInMillis() }
        assertEquals("MONTH cannot be ADAR_1(5) except leap years", error.message)
        calendar.clear(); calendar.set(Calendar.YEAR, 5784); calendar.setTemporalMonthCode("M05L"); calendar.set(Calendar.DATE, 1)
        assertEquals(HebrewCalendar.ADAR_1, calendar.get(Calendar.MONTH))
        assertFailsWith<IllegalArgumentException> { calendar.setTemporalMonthCode("M13") }
    }
    @Test fun clonedCalendarsUseIndependentFieldsZonesAndOwners() {
        val original = calendar().apply { clear(); set(5784, HebrewCalendar.ADAR_1, 30) }
        val copy = original.clone()
        assertIs<HebrewCalendar>(copy); assertEquals(original, copy)
        assertNotSame(original.getTimeZone(), copy.getTimeZone())
        copy.add(Calendar.MONTH, 1)
        assertEquals(HebrewCalendar.ADAR_1, original.get(Calendar.MONTH))
        assertEquals(HebrewCalendar.ADAR, copy.get(Calendar.MONTH))
        assertEquals(30, original.get(Calendar.DATE)); assertEquals(29, copy.get(Calendar.DATE))
    }
    @Test fun limitsAreAvailableDuringBaseConstruction() {
        val observed = arrayListOf<String>()
        val calendar = object : HebrewCalendar(SimpleTimeZone(0, "custom"), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar {
                observed.add("${getType()}:${getMaximum(Calendar.MONTH)}:${getMaximum(Calendar.ORDINAL_MONTH)}")
                return super.setWeekData(data)
            }
        }
        assertEquals(listOf("hebrew:12:12"), observed)
        assertEquals("hebrew", calendar.getType())
    }
}
