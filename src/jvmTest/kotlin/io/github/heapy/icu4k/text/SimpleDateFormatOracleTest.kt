@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.SimpleDateFormat as Source
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.Calendar as SourceCalendar
import io.github.heapy.icu4k.util.*
import java.text.AttributedCharacterIterator
import kotlin.test.*

class SimpleDateFormatOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) {
        when (e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName + ":" + e.message }
    }
    private fun prepare(a: Source, b: SimpleDateFormat) {
        a.timeZone = com.ibm.icu.util.TimeZone.getTimeZone("Etc/UTC"); b.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
        a.set2DigitYearStart(java.util.Date(0)); b.set2DigitYearStart(Date(0))
    }
    @Test fun publicConstructorsFormatParseAndMutableOperations() {
        for (id in listOf("en_US", "ru", "ar", "ja@calendar=japanese", "zh@calendar=chinese", "ko@calendar=dangi", "he@calendar=hebrew", "fa@calendar=persian", "hi@calendar=indian")) {
            for (override in listOf<String?>(null, "y=thai")) {
                val a = if (override == null) Source("yyyy-MM-dd HH:mm:ss XXX", SourceLocale(id)) else Source("yyyy-MM-dd HH:mm:ss XXX", override, SourceLocale(id))
                val b = if (override == null) SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX", ULocale(id)) else SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX", override, ULocale(id))
                prepare(a, b)
                for (pattern in listOf("yyyy-MM-dd HH:mm:ss XXX", "G y MMMM d EEEE a h:mm z", "yyMMddHHmmss", "MMMMM LLLL QQQQ cccc U r B b", "yyyy年M月d日", "")) {
                    a.applyPattern(pattern); b.applyPattern(pattern)
                    assertEquals(a.toPattern(), b.toPattern())
                    assertEquals(a.toLocalizedPattern(), b.toLocalizedPattern())
                    for (time in listOf(0L, -2208988800000L, 951827696123L, 1712345678901L)) {
                        val at = outcome { a.format(java.util.Date(time)) }; val bt = outcome { b.format(Date(time)) }
                        assertEquals(at, bt, "$id/$override/$pattern/$time")
                        if (at is String) {
                            val ap = java.text.ParsePosition(0); val bp = ParsePosition(0)
                            assertEquals(outcome { a.parse(at, ap)?.time }, outcome { b.parse(at, bp)?.getTime() }, "$id/$pattern/$at")
                            assertEquals(listOf(ap.index, ap.errorIndex), listOf(bp.index, bp.errorIndex))
                        }
                    }
                }
                val ac = a.clone(); val bc = b.clone()
                assertEquals(a == ac, b == bc)
                assertEquals(a.hashCode(), b.hashCode())
                assertEquals(outcome { a.applyPattern(null) }, outcome { b.applyPattern(null) })
                assertEquals(outcome { a == a }, outcome { b == b })
                assertEquals(outcome { a.hashCode() }, outcome { b.hashCode() })
            }
        }
    }
    private fun runs(iterator: AttributedCharacterIterator): Any {
        val result = ArrayList<Any>()
        var i = iterator.beginIndex
        while (i < iterator.endIndex) {
            iterator.setIndex(i)
            val limit = iterator.runLimit
            val text = buildString { for (j in i until limit) append(iterator.setIndex(j)) }
            iterator.setIndex(i)
            result.add(listOf(i, limit, text, iterator.attributes.entries.associate { it.key.toString().substringAfter('(') to it.value.toString().substringAfter('(') }))
            i = limit
        }
        return result
    }
    @Test fun attributedInputsIncludingJavaCalendarMutationMatchSource() {
        val a = Source("G yyyy-MM-dd HH:mm EEEE", SourceLocale.US)
        val b = SimpleDateFormat("G yyyy-MM-dd HH:mm EEEE", ULocale.US)
        prepare(a, b)
        val ac = SourceCalendar.getInstance(SourceLocale("th@calendar=buddhist")).also { it.timeInMillis = 123456789L }
        val bc = Calendar.getInstance(ULocale("th@calendar=buddhist")).also { it.setTimeInMillis(123456789L) }
        val javaCal = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("Asia/Tokyo")).also { it.timeInMillis = 1723456789000L }
        val pairs = listOf(java.util.Date(0) to Date(0), 12345L to 12345L, ac to bc, javaCal to javaCal,
            java.time.LocalDate.of(2024, 2, 29) to java.time.LocalDate.of(2024, 2, 29), java.time.DayOfWeek.FRIDAY to java.time.DayOfWeek.FRIDAY,
            java.time.Month.FEBRUARY to java.time.Month.FEBRUARY, null to null, "bad" to "bad")
        for ((av, bv) in pairs) {
            assertEquals(outcome { runs(a.formatToCharacterIterator(av)) }, outcome { runs(b.formatToCharacterIterator(bv)) }, "$av")
            assertEquals(a.calendar.type, b.getCalendar()!!.getType())
            assertEquals(a.calendar.timeInMillis, b.getCalendar()!!.getTimeInMillis())
        }
    }
    @Test fun calendarFactoryStylesKeywordsAndConfigurationOwnership() {
        for (id in listOf("en_US", "en_US@hours=h23", "en_US@rg=gbzzzz", "he@calendar=hebrew", "zh@calendar=chinese", "ja@calendar=japanese", "ar@numbers=arab")) {
            for (date in -1..3) for (time in -1..3) {
                val ac = SourceCalendar.getInstance(SourceLocale(id))
                val bc = Calendar.getInstance(ULocale(id))
                var a: Source? = null; var b: SimpleDateFormat? = null
                assertEquals(outcome { a = ac.getDateTimeFormat(date, time, SourceLocale(id)) as Source; "ok" }, outcome { b = bc.getDateTimeFormat(date, time, ULocale(id)) as SimpleDateFormat; "ok" }, "$id/$date/$time")
                if (a != null && b != null) {
                    assertSame(ac, a!!.calendar); assertSame(bc, b!!.getCalendar())
                    prepare(a!!, b!!)
                    assertEquals(a!!.toPattern(), b!!.toPattern())
                    assertEquals(a!!.format(java.util.Date(951827696123L)), b!!.format(Date(951827696123L)), "$id/$date/$time")
                }
            }
        }
    }
}
