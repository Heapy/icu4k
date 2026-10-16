package io.github.heapy.icu4k.util

import kotlin.test.*

class CECalendarTest {
    @Test fun thirteenthMonthAndClones() {
        for (calendar in listOf(CopticCalendar(SimpleTimeZone(0, "custom"), ULocale.US), EthiopicCalendar(SimpleTimeZone(0, "custom"), ULocale.US))) {
            calendar.clear(); calendar.set(Calendar.EXTENDED_YEAR, 2015); calendar.setTemporalMonthCode("M13"); calendar.set(Calendar.DATE, 6)
            assertEquals("M13", calendar.getTemporalMonthCode())
            assertEquals(6, calendar.getActualMaximum(Calendar.DATE))
            val copy = calendar.clone()
            assertEquals(calendar::class, copy::class)
            copy.add(Calendar.DATE, 1)
            assertEquals(0, copy.get(Calendar.MONTH)); assertEquals(2016, copy.get(Calendar.EXTENDED_YEAR))
            assertEquals(12, calendar.get(Calendar.MONTH)); assertEquals(6, calendar.get(Calendar.DATE))
        }
    }
    @Test fun ethiopicModesAreIndependentAfterClone() {
        val original = EthiopicCalendar(SimpleTimeZone(0, "custom"), ULocale("am_ET@calendar=ethiopic-amete-alem"))
        assertTrue(original.isAmeteAlemEra()); assertEquals(0, original.getMaximum(Calendar.ERA))
        val copy = original.clone() as EthiopicCalendar
        copy.setAmeteAlemEra(false)
        assertTrue(original.isAmeteAlemEra()); assertFalse(copy.isAmeteAlemEra())
        for (calendar in listOf(original, copy)) { calendar.clear(); calendar.set(7500, 0, 1) }
        assertNotEquals(original.getTimeInMillis(), copy.getTimeInMillis())
        assertEquals("ethiopic-amete-alem", original.getType()); assertEquals("ethiopic", copy.getType())
    }
}
