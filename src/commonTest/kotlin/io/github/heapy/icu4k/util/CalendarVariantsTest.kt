@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import kotlin.test.*

class CalendarVariantsTest {
    private fun calendars(): List<Calendar> = listOf(
        BuddhistCalendar(SimpleTimeZone(0, "custom"), ULocale("th_TH")),
        TaiwanCalendar(SimpleTimeZone(0, "custom"), ULocale("zh_TW")),
        PersianCalendar(SimpleTimeZone(0, "custom"), ULocale("fa_IR")),
        IndianCalendar(SimpleTimeZone(0, "custom"), ULocale("hi_IN")),
    )
    private fun millis(year: Int, month: Int, day: Int): Long = GregorianCalendar(SimpleTimeZone(0, "custom"), ULocale.US).run {
        clear(); set(year, month, day); getTimeInMillis()
    }
    @Test fun erasYearOffsetsAndNewYearBoundaries() {
        val (buddhist, taiwan, persian, indian) = calendars()
        for (calendar in listOf(buddhist, taiwan, persian, indian)) calendar.setTimeInMillis(0)
        assertEquals(listOf("buddhist", "roc", "persian", "indian"), calendars().map { it.getType() })
        assertEquals(2513, buddhist.get(Calendar.YEAR)); assertEquals(BuddhistCalendar.BE, buddhist.get(Calendar.ERA))
        assertEquals(59, taiwan.get(Calendar.YEAR)); assertEquals(TaiwanCalendar.MINGUO, taiwan.get(Calendar.ERA))
        taiwan.setTimeInMillis(millis(1911, 11, 31)); assertEquals(TaiwanCalendar.BEFORE_MINGUO, taiwan.get(Calendar.ERA)); assertEquals(1, taiwan.get(Calendar.YEAR))
        taiwan.add(Calendar.DATE, 1); assertEquals(TaiwanCalendar.MINGUO, taiwan.get(Calendar.ERA)); assertEquals(1, taiwan.get(Calendar.YEAR))
        persian.setTimeInMillis(millis(2024, 2, 20)); assertEquals(1403, persian.get(Calendar.YEAR)); assertEquals(0, persian.get(Calendar.MONTH)); assertEquals(1, persian.get(Calendar.DATE))
        indian.setTimeInMillis(millis(2024, 2, 21)); assertEquals(1946, indian.get(Calendar.YEAR)); assertEquals(IndianCalendar.CHAITRA, indian.get(Calendar.MONTH)); assertEquals(1, indian.get(Calendar.DATE))
        assertEquals(1970, calendars()[2].apply { setTimeInMillis(0) }.getRelatedYear())
        assertEquals(1970, calendars()[3].apply { setTimeInMillis(0) }.getRelatedYear())
    }
    @Test fun mutableClonesKeepTheCalendarTypeAndIndependentState() {
        for (calendar in calendars()) {
            calendar.setTimeInMillis(millis(2024, 0, 31)); val original = calendar.getTimeInMillis()
            calendar.get(Calendar.DAY_OF_WEEK)
            val copy = calendar.clone()
            assertEquals(calendar::class, copy::class); assertEquals(calendar, copy)
            assertNotSame(calendar.getTimeZone(), copy.getTimeZone())
            copy.add(Calendar.MONTH, 1); assertEquals(original, calendar.getTimeInMillis())
            assertNotEquals(original, copy.getTimeInMillis())
            copy.getTimeZone()!!.setRawOffset(3600000); assertEquals(0, calendar.getTimeZone()!!.getRawOffset())
        }
    }
    @Test fun extendedYearPrecedenceAndStrictMonthValidation() {
        for (calendar in calendars()) {
            calendar.clear(); calendar.set(Calendar.YEAR, 100); calendar.set(Calendar.EXTENDED_YEAR, 2024)
            assertEquals(2024, calendar.get(Calendar.EXTENDED_YEAR))
            calendar.setLenient(false); calendar.clear(); calendar.set(1400, 12, 1)
            assertFailsWith<IllegalArgumentException> { calendar.getTimeInMillis() }
            calendar.setLenient(true); assertTrue(calendar.get(Calendar.MONTH) in 0..11)
        }
    }
    @Test fun statelessOverridesAreAvailableDuringBaseConstruction() {
        val observed = arrayListOf<String>()
        val buddhist = object : BuddhistCalendar(SimpleTimeZone(0, "custom"), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar {
                observed.add("${getType()}:${getMinimum(Calendar.ERA)}:${getMaximum(Calendar.ERA)}")
                return super.setWeekData(data)
            }
        }
        val indian = object : IndianCalendar(SimpleTimeZone(0, "custom"), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar {
                observed.add("${getType()}:${getMinimum(Calendar.ERA)}:${getMaximum(Calendar.ERA)}")
                return super.setWeekData(data)
            }
        }
        assertEquals(listOf("buddhist:0:0", "indian:0:0"), observed)
        assertEquals("buddhist", buddhist.getType()); assertEquals("indian", indian.getType())
    }
    @Test fun constructorScalarHelpersDoNotRequireArraysAndKeepTheirCache() {
        val observed = arrayListOf<String>()
        val calendar = object : IndianCalendar(SimpleTimeZone(0, "custom"), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar {
                computeGregorianFields(2440588)
                observed.add("${fieldName(Calendar.YEAR)}:${getGregorianYear()}:${getDefaultMonthInYear(1970)}:${getDefaultDayInMonth(1970, 0)}:${weekNumber(1, 1, Calendar.THURSDAY)}")
                val failure = assertFailsWith<IllegalStateException> { internalSet(Calendar.YEAR, 1) }
                observed.add(failure.message!!)
                return super.setWeekData(data)
            }
            fun cachedYear() = getGregorianYear()
        }
        assertEquals(listOf("YEAR:1970:0:1:1", "Subclass cannot set YEAR"), observed)
        assertEquals(1970, calendar.cachedYear())
        calendar.setTimeInMillis(1719792000000L); assertEquals(1946, calendar.get(Calendar.YEAR))
        assertEquals(2024, calendar.cachedYear())
    }
}
