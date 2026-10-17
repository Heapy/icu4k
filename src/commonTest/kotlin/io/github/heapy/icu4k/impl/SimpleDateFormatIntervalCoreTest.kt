package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class SimpleDateFormatIntervalCoreTest {
    @Test fun identicalCalendarsAndDifferentTimes() {
        val f=SimpleDateFormatStateFixture("en_US","MMM d, y HH:mm")
        f.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
        val a=f.getCalendar()!!.clone(); a.setTimeInMillis(0)
        val b=a.clone()
        assertEquals("Jan 1, 1970 00:00",f.interval.format(a,b,StringBuilder(),FormatFieldPosition(-1)).toString())
        b.add(Calendar.MINUTE,1)
        val text=f.interval.format(a,b,StringBuilder("prefix:"),FormatFieldPosition(DateFormat.MINUTE_FIELD)).toString()
        assertEquals("prefix:Jan 1, 1970 00:00 – Jan 1, 1970 00:01",text)
    }
    @Test fun incompatibleCalendarsAndWrappedPatternErrors() {
        val f=SimpleDateFormatStateFixture("en_US","j")
        val a=f.getCalendar()!!.clone(); a.setTimeInMillis(0)
        val b=a.clone(); b.setTimeZone(TimeZone.getTimeZone("GMT+01:00"))
        assertEquals("can not format on two different calendars",assertFailsWith<IllegalArgumentException> { f.interval.format(a,b,StringBuilder(),FormatFieldPosition(-1)) }.message)
        b.setTimeZone(a.getTimeZone())
        assertEquals("java.lang.IllegalArgumentException: Illegal pattern character 'j' in \"j\"",assertFailsWith<IllegalArgumentException> { f.interval.format(a,b,StringBuilder(),FormatFieldPosition(-1)) }.message)
    }
}
