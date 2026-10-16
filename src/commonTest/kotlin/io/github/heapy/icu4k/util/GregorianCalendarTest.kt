package io.github.heapy.icu4k.util

import kotlin.test.*

class GregorianCalendarTest {
    private class Derived : GregorianCalendar {
        var payload = intArrayOf()
        constructor(payload: IntArray) : super(Fixed(), ULocale.US) { this.payload = payload; constructions++ }
        private constructor(other: Derived) : super(other, Unit) { payload = other.payload }
        override fun cloneNative(): Calendar = Derived(this)
        companion object { var constructions = 0 }
    }
    private class Fixed(private var raw: Int = 0) : TimeZone("custom") {
        override fun getRawOffset() = raw
        override fun setRawOffset(offsetMillis: Int) { raw = offsetMillis }
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int) = raw
        override fun useDaylightTime() = false
        override fun inDaylightTime(date: Date?) = false
        override fun cloneAsThawed(): TimeZone = Fixed(raw).also { it.setID(getID()) }
    }
    @Test fun explicitZoneFieldsWeekConfigurationAndTemporalAccessors() {
        val zone = Fixed(19800000)
        val calendar = GregorianCalendar(zone, ULocale("en_US_POSIX@fw=mon;hours=h23"))
        assertSame(zone, calendar.getTimeZone()); assertEquals(24, calendar.getFieldCount())
        assertEquals(Calendar.MONDAY, calendar.getFirstDayOfWeek())
        assertEquals(ULocale.US, calendar.getLocale(ULocale.VALID_LOCALE))
        calendar.setTimeInMillis(0)
        assertEquals(1970, calendar.get(Calendar.YEAR)); assertEquals(5, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calendar.get(Calendar.MINUTE)); assertEquals(19800000, calendar.get(Calendar.ZONE_OFFSET))
        calendar.clear(); calendar.set(2024, Calendar.FEBRUARY, 29, 12, 34, 56)
        assertTrue(calendar.inTemporalLeapYear()); assertEquals("M02", calendar.getTemporalMonthCode())
        assertEquals(29, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        assertEquals(2024, calendar.getRelatedYear())
        calendar.setTemporalMonthCode("M03"); assertEquals(Calendar.MARCH, calendar.get(Calendar.MONTH))
    }
    @Test fun historicalCutoverFieldResolutionAndStrictValidation() {
        val calendar = GregorianCalendar(Fixed(), ULocale.US)
        calendar.clear(); calendar.set(1582, Calendar.OCTOBER, 4)
        calendar.add(Calendar.DATE, 1); assertEquals(15, calendar.get(Calendar.DATE))
        calendar.clear(); calendar.set(Calendar.YEAR, 2024); calendar.set(Calendar.DAY_OF_YEAR, 60)
        assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH)); assertEquals(29, calendar.get(Calendar.DATE))
        calendar.setLenient(false); calendar.clear(); calendar.set(2023, Calendar.FEBRUARY, 29)
        assertFailsWith<IllegalArgumentException> { calendar.getTimeInMillis() }
        calendar.setLenient(true); assertEquals(Calendar.MARCH, calendar.get(Calendar.MONTH))
    }
    @Test fun cloneRebindsAllStateAndCalendarAlgorithms() {
        val original = GregorianCalendar(Fixed(), ULocale.US)
        original.clear(); original.set(2024, Calendar.JANUARY, 31)
        original.getTimeInMillis(); original.get(Calendar.DAY_OF_WEEK)
        val copy = original.clone()
        assertEquals(original, copy); assertNotSame(original.getTimeZone(), copy.getTimeZone())
        copy.add(Calendar.MONTH, 1); assertEquals(29, copy.get(Calendar.DATE))
        assertEquals(Calendar.JANUARY, original.get(Calendar.MONTH)); assertEquals(31, original.get(Calendar.DATE))
        copy.roll(Calendar.MONTH, 1); assertEquals(Calendar.MARCH, copy.get(Calendar.MONTH))
        copy.getTimeZone()!!.setRawOffset(3600000); assertEquals(0, original.getTimeZone()!!.getRawOffset())
        assertTrue(original.before(copy)); assertTrue(copy.after(original))
        val same = original.clone(); assertEquals(3, same.fieldDifference(Date(original.getTimeInMillis() + 3 * 86400000L), Calendar.DATE))
        assertEquals(31, original.get(Calendar.DATE))
    }
    @Test fun nativeSubclassCopiesPreserveTypeWithoutConstructorCallbacks() {
        Derived.constructions = 0
        val original = Derived(intArrayOf(7))
        original.clear(); original.set(2024, Calendar.JANUARY, 31); original.get(Calendar.DAY_OF_WEEK)
        val copy = original.clone()
        assertIs<Derived>(copy); assertEquals(1, Derived.constructions)
        assertSame(original.payload, copy.payload)
        copy.add(Calendar.MONTH, 1)
        assertEquals(31, original.get(Calendar.DATE)); assertEquals(29, copy.get(Calendar.DATE))
    }
    @Test fun nullComparisonStillValidatesTheReceiverFirst() {
        val calendar = GregorianCalendar(Fixed(), ULocale.US)
        calendar.clear(); calendar.setLenient(false); calendar.set(2023, Calendar.FEBRUARY, 29)
        assertFailsWith<IllegalArgumentException> { calendar.compareTo(null) }
        calendar.set(Calendar.YEAR, 2024)
        assertFailsWith<NullPointerException> { calendar.compareTo(null) }
    }
    @Test fun gregorianConstructorHooksSeeJavaZeroStateBeforeNumericInitializers() {
        val observed = arrayListOf<String>()
        val calendar = object : GregorianCalendar(Fixed(), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar {
                observed.add("${getGregorianChange().getTime()}:${isLeapYear(1500)}:$isGregorian:$invertGregorian")
                isGregorian = true; invertGregorian = true
                return super.setWeekData(data)
            }
            fun flags() = listOf(isGregorian, invertGregorian)
        }
        assertEquals(listOf("0:false:false:false"), observed)
        assertEquals(-12219292800000L, calendar.getGregorianChange().getTime())
        assertTrue(calendar.isLeapYear(1500))
        assertEquals(listOf(true, true), calendar.flags())
    }
}
