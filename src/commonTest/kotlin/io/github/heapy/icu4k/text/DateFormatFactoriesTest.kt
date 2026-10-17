package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.RelativeDateFormat
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class DateFormatFactoriesTest {
    @Test fun stylesSkeletonsAndExplicitCalendars() {
        val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, ULocale.US)
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
        assertEquals("1/1/70, 12:00\u202fAM", format.format(Date(0)))
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/UTC"), ULocale("en@calendar=buddhist"))
        val skeleton = DateFormat.getInstanceForSkeleton(calendar, "yMd", ULocale.US)
        assertSame(calendar, skeleton.getCalendar())
        assertEquals("1/1/2513 BE", skeleton.format(Date(0)))
        assertFailsWith<IllegalArgumentException> { DateFormat.getDateTimeInstance(null, -2, -2, ULocale.US) }
        assertNull(DateFormat.getInstanceForSkeleton(null, "y", ULocale.US).getCalendar())
    }
    @Test fun calendarTypeHookPrecedesNullLocaleFailure() {
        val calls = mutableListOf<String>()
        val calendar = object : GregorianCalendar(ULocale.US) {
            override fun getType(): String { calls.add("type"); return "gregorian" }
        }
        calls.clear()
        assertFailsWith<NullPointerException> { DateFormat.getInstanceForSkeleton(calendar, "y", null) }
        assertEquals(listOf("type"), calls)
    }
    @Test fun relativeDaysContextParsingAndCloneSharing() {
        val format = DateFormat.getDateInstance(DateFormat.RELATIVE_SHORT, ULocale.US)
        assertIs<RelativeDateFormat>(format)
        val cal = Calendar.getInstance(ULocale.US)
        assertEquals("today", format.format(cal, StringBuilder(), FormatFieldPosition(0)).toString())
        cal.add(Calendar.DATE, 1)
        format.setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
        assertEquals("Tomorrow", format.format(cal, StringBuilder(), FormatFieldPosition(0)).toString())
        assertFailsWith<UnsupportedOperationException> { format.parse("today", null, null) }
        val clone = format.clone()
        assertNotSame(format.getCalendar(), clone.getCalendar())
        assertNull(clone.getNumberFormat())
        assertEquals(format, clone)
        assertFailsWith<NullPointerException> { format.hashCode() }
    }
}
