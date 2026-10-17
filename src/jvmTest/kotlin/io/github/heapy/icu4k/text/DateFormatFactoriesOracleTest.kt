@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.DateFormat as Source
import com.ibm.icu.text.SimpleDateFormat as SourceSimple
import com.ibm.icu.util.Calendar as SourceCalendar
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.TimeZone as SourceZone
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class DateFormatFactoriesOracleTest {
    private fun outcome(action: () -> Any?): String = try { "ok:${action()}" } catch (e: Exception) {
        when (e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName + ":" + e.message }
    }
    private fun compare(a: Source, b: DateFormat) {
        assertEquals(a.javaClass.simpleName, b.javaClass.simpleName)
        assertEquals(a.getLocale(SourceLocale.VALID_LOCALE)?.toString(), b.getLocale(ULocale.VALID_LOCALE)?.toString())
        assertEquals(a.getLocale(SourceLocale.ACTUAL_LOCALE)?.toString(), b.getLocale(ULocale.ACTUAL_LOCALE)?.toString())
        if (a is SourceSimple) assertEquals(a.toPattern(), (b as SimpleDateFormat).toPattern())
        a.timeZone = SourceZone.getTimeZone("Asia/Tokyo"); b.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"))
        for (time in listOf(0L, -2208988800000L, 1712345678901L)) assertEquals(a.format(java.util.Date(time)), b.format(Date(time)))
    }
    @Test fun everyOrdinaryStyleAndSkeletonAcrossCalendars() {
        for (id in listOf("en_US", "fr", "ar", "ru", "zh@calendar=chinese", "he@calendar=hebrew", "ja@calendar=japanese")) {
            val al = SourceLocale(id); val bl = ULocale(id)
            for (date in -1..3) for (time in -1..3) {
                if (date == -1 && time == -1) assertEquals(outcome { Source.getDateTimeInstance(date, time, al) }, outcome { DateFormat.getDateTimeInstance(date, time, bl) })
                else compare(Source.getDateTimeInstance(date, time, al), DateFormat.getDateTimeInstance(date, time, bl))
            }
            for (skeleton in listOf("yMd", "yMMMd", "EEEEjm", "GyMMMEd", "Hm")) {
                compare(Source.getInstanceForSkeleton(skeleton, al), DateFormat.getInstanceForSkeleton(skeleton, bl))
                val ac = SourceCalendar.getInstance(SourceLocale("en@calendar=buddhist")); val bc = Calendar.getInstance(ULocale("en@calendar=buddhist"))
                val a = Source.getInstanceForSkeleton(ac, skeleton, al); val b = DateFormat.getInstanceForSkeleton(bc, skeleton, bl)
                assertSame(ac, a.calendar); assertSame(bc, b.getCalendar()); compare(a, b)
            }
        }
    }
    @Test fun relativeStylesContextsAndJulianDaysMatchSource() {
        for (id in listOf("en_US", "fr", "ru", "ar", "zh", "he", "ja@calendar=japanese")) for (date in listOf(-1, 0, 3, 128, 131)) for (time in listOf(-1, 3, 131)) {
            if (date == -1 && time == -1) continue
            val a = Source.getDateTimeInstance(date, time, SourceLocale(id)); val b = DateFormat.getDateTimeInstance(date, time, ULocale(id))
            for (context in listOf(DisplayContext.CAPITALIZATION_NONE, DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE, DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU, DisplayContext.CAPITALIZATION_FOR_STANDALONE)) {
                a.setContext(com.ibm.icu.text.DisplayContext.valueOf(context.name)); b.setContext(context)
                for (day in listOf(-10, -2, -1, 0, 1, 2, 10)) {
                    val now = System.currentTimeMillis()
                    val ac = SourceCalendar.getInstance(SourceZone.getTimeZone("America/New_York"), SourceLocale(id))
                    val bc = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"), ULocale(id))
                    ac.timeInMillis = now; bc.setTimeInMillis(now)
                    ac.add(SourceCalendar.DATE, day); bc.add(Calendar.DATE, day)
                    val ap = java.text.FieldPosition(Source.HOUR_OF_DAY0_FIELD); val bp = FormatFieldPosition(DateFormat.HOUR_OF_DAY0_FIELD)
                    ap.beginIndex = 8; ap.endIndex = 9; bp.beginIndex = 8; bp.endIndex = 9
                    assertEquals(a.format(ac, StringBuffer("prefix:"), ap).toString(), b.format(bc, StringBuilder("prefix:"), bp).toString(), "$id/$date/$time/$context/$day")
                    assertEquals(listOf(ap.beginIndex, ap.endIndex), listOf(bp.beginIndex, bp.endIndex))
                }
            }
        }
    }
    @Test fun suppliedCalendarHooksFallbackAndLocaleAdapters() {
        val al = SourceLocale.FRANCE; val bl = ULocale.FRANCE
        val ac = SourceCalendar.getInstance(SourceLocale("en@calendar=buddhist")); val bc = Calendar.getInstance(ULocale("en@calendar=buddhist"))
        val af = Source.getDateTimeInstance(ac, 3, 3, al); val bf = DateFormat.getDateTimeInstance(bc, 3, 3, bl)
        assertSame(ac, af.calendar); assertSame(bc, bf.getCalendar()); compare(af, bf)
        val ae = mutableListOf<String>(); val be = mutableListOf<String>()
        val asCalendar = object : com.ibm.icu.util.GregorianCalendar(al) {
            override fun getDateTimeFormat(dateStyle: Int, timeStyle: Int, locale: SourceLocale?): Source {
                ae.add("$dateStyle/$timeStyle/$locale")
                throw java.util.MissingResourceException("missing", "calendar", "pattern")
            }
        }
        val bsCalendar = object : GregorianCalendar(bl) {
            override fun getDateTimeFormat(dateStyle: Int, timeStyle: Int, locale: ULocale?): DateFormat {
                be.add("$dateStyle/$timeStyle/$locale")
                throw MissingResourceException("missing", "calendar", "pattern")
            }
        }
        compare(Source.getDateTimeInstance(asCalendar, 3, 2, al), DateFormat.getDateTimeInstance(bsCalendar, 3, 2, bl))
        assertEquals(ae, be)
        compare(Source.getDateInstance(3, java.util.Locale.FRANCE), DateFormat.getDateInstance(3, java.util.Locale.FRANCE))
        compare(Source.getInstanceForSkeleton("yMMMd", java.util.Locale.FRANCE), DateFormat.getInstanceForSkeleton("yMMMd", java.util.Locale.FRANCE))
        assertSame(DateFormat.getAvailableLocales(), DateFormat.getAvailableLocales())
        assertSame(DateFormat.getAvailableULocales(), DateFormat.getAvailableULocales())
    }
    @Test fun invalidStylesNullsAndRelativeParsingPreserveFailureOrder() {
        for (date in listOf(-2, -1, 4, 127, 128, 132, 256)) for (time in listOf(-2, -1, 4, 128, 132)) {
            assertEquals(outcome { Source.getDateTimeInstance(date, time, SourceLocale.US).javaClass.simpleName }, outcome { DateFormat.getDateTimeInstance(date, time, ULocale.US).javaClass.simpleName }, "$date/$time")
            assertEquals(outcome { Source.getDateTimeInstance(null, date, time, SourceLocale.US) }, outcome { DateFormat.getDateTimeInstance(null, date, time, ULocale.US) })
        }
        val a = Source.getDateInstance(Source.RELATIVE_SHORT, SourceLocale.US); val b = DateFormat.getDateInstance(DateFormat.RELATIVE_SHORT, ULocale.US)
        assertEquals(outcome { a.parse(null, null, null) }, outcome { b.parse(null, null, null) })
        assertEquals(outcome { a.parse("today") }, outcome { b.parse("today") })
        assertEquals(outcome { a.hashCode() }, outcome { b.hashCode() })
    }
}
