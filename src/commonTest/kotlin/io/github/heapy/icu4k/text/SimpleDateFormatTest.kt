@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class SimpleDateFormatTest {
    @Test fun publicFormattingParsingAndCalendarFactory() {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX", ULocale.US)
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
        format.set2DigitYearStart(Date(0))
        assertEquals("1970-01-01 00:00:00 Z", format.format(Date(0)))
        assertEquals(0L, format.parse("1970-01-01 00:00:00 Z")!!.getTime())
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/UTC"), ULocale.US)
        val factory = calendar.getDateTimeFormat(DateFormat.SHORT, DateFormat.SHORT, ULocale.US)
        assertSame(calendar, factory.getCalendar())
        assertIs<SimpleDateFormat>(factory)
        assertNotNull(Calendar.getDateAtTimePattern(calendar, ULocale.US, DateFormat.SHORT))
    }
    @Test fun symbolsCloneCenturyIdentityAndLocalizedCacheBehavior() {
        val symbols = DateFormatSymbols(ULocale.US)
        val format = SimpleDateFormat("yyyy", symbols, ULocale.US)
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
        symbols.setEras(arrayOf("changed"))
        assertEquals(2, format.getDateFormatSymbols().getEras()!!.size)
        val start = Date(0)
        format.set2DigitYearStart(start)
        assertSame(start, format.get2DigitYearStart())
        assertEquals("1970", format.format(Date(0)))
        format.applyLocalizedPattern("MM")
        assertEquals("MM", format.toPattern())
        assertEquals("1970", format.format(Date(0)))
        val copy = format.clone()
        assertEquals(format, copy)
        assertSame(start, copy.get2DigitYearStart())
        assertNotSame(format.getCalendar(), copy.getCalendar())
        copy.applyPattern("dd")
        assertEquals("01", copy.format(Date(0)))
        assertEquals("MM", format.toPattern())
    }
    @Test fun leapPatternUsesProductionMessageFormatAndOtherArguments() {
        val format = SimpleDateFormat("M", ULocale("zh@calendar=chinese"))
        val symbols = format.getDateFormatSymbols()
        symbols.setLeapMonthPattern("L{0,number,000}", DateFormatSymbols.NUMERIC, DateFormatSymbols.WIDE)
        format.setDateFormatSymbols(symbols)
        val calendar = format.getCalendar()!!
        calendar.clear()
        calendar.set(Calendar.EXTENDED_YEAR, 2023) // 2023 has a leap second month.
        val pos = ParsePosition(0)
        format.parse("L02", calendar, pos)
        assertEquals(3, pos.index)
        assertEquals(1, calendar.get(Calendar.IS_LEAP_MONTH))
        assertEquals(1, calendar.get(Calendar.MONTH))
    }
    @Test fun formatAttributesAndIntervalArePublic() {
        val format = SimpleDateFormat("yyyy-MM-dd", ULocale.US)
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
        val iterator = format.formatToCharacterIterator(Date(0))
        assertEquals(10, iterator.getEndIndex())
        iterator.first()
        assertEquals(DateFormat.Field.YEAR, iterator.getAttribute(DateFormat.Field.YEAR))
        val a = format.getCalendar()!!.clone().also { it.setTimeInMillis(0) }
        val b = a.clone().also { it.add(Calendar.DATE, 1) }
        assertTrue(format.intervalFormatByAlgorithm(a, b, StringBuilder(), FormatFieldPosition(0)).contains("02"))
        assertFailsWith<IllegalArgumentException> { format.formatToCharacterIterator(null) }
    }
}
