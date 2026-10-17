@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import com.ibm.icu.text.SimpleDateFormat as Source
import com.ibm.icu.text.NumberFormat as SourceNumber
import com.ibm.icu.text.DecimalFormat as SourceDecimal
import com.ibm.icu.text.DateFormat as SourceDate
import com.ibm.icu.text.MessageFormat as SourceMessage
import com.ibm.icu.text.TimeZoneFormat as SourceZoneFormat
import com.ibm.icu.util.Calendar as SourceCalendar
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.TimeZone as SourceZone
import com.ibm.icu.util.Output as SourceOutput
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class SimpleDateFormatParseCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        val t = if (e is java.lang.reflect.InvocationTargetException) e.targetException else e
        if (t is VirtualMachineError) throw t
        when (t) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> t.javaClass.simpleName + ":" + t.message }
    }
    private fun pair(id: String, pattern: String): Pair<Source, SimpleDateFormatParseFixture> {
        val a = Source(pattern, SourceLocale(id))
        a.numberFormat = SourceNumber.getInstance(SourceLocale(id))
        a.timeZone = SourceZone.getTimeZone("Etc/UTC")
        a.set2DigitYearStart(java.util.Date(0))
        val b = SimpleDateFormatParseFixture(id, pattern)
        b.assertionsEnabled = Source::class.java.desiredAssertionStatus()
        b.calendar!!.setTimeInMillis(0)
        b.centuryYear = b.calendar!!.get(Calendar.YEAR)
        return a to b
    }
    private fun compare(a: Source, b: SimpleDateFormatParseFixture, text: String, start: Int = 0,
                        ac: SourceCalendar = a.calendar, bc: Calendar = b.calendar!!, clear: Boolean = true) {
        if (clear) { ac.clear(); bc.clear() }
        val ap = java.text.ParsePosition(start).also { it.errorIndex = 91 }
        val bp = ParsePosition(start).also { it.errorIndex = 91 }
        val label = "${b.locale}/${b.pattern}/$text/$start"
        assertEquals(outcome { a.parse(text, ac, ap); "ok" }, outcome { b.core.parse(text, bc, bp); "ok" }, label)
        assertEquals(listOf(ap.index, ap.errorIndex), listOf(bp.index, bp.errorIndex), label)
        assertEquals(ac.timeZone.id, bc.getTimeZone()!!.getID(), label)
        assertEquals((0 until 23).map { ac.isSet(it) }, (0 until 23).map { bc.isSet(it) }, label)
        assertEquals(outcome { ac.timeInMillis }, outcome { bc.getTimeInMillis() }, label)
        assertEquals(a.timeZone.id, b.calendar!!.getTimeZone()!!.getID(), label)
    }
    @Test fun allPatternFieldsAndWidthsAcrossLocalesCalendars() {
        for (id in listOf("en_US", "ru", "fr", "ar_EG", "fa", "zh_CN", "en@numbers=mathbold", "en@numbers=hanidec", "en@calendar=hebrew", "zh@calendar=chinese", "ko@calendar=dangi", "ja@calendar=japanese", "th@calendar=buddhist", "fa@calendar=persian", "ar@calendar=islamic", "en@calendar=coptic")) {
            val (a,b) = pair(id, "")
            for (ch in ('A'..'Z') + ('a'..'z')) for (width in 1..6) {
                val pattern = ch.toString().repeat(width)
                a.applyPattern(pattern); b.pattern = pattern
                for (millis in listOf(0L, 1710054000123L, 1730615400999L)) {
                    val formatted = outcome { a.format(java.util.Date(millis)) }
                    if (formatted is String && !formatted.startsWith("IllegalArgumentException:")) compare(a,b,formatted)
                }
            }
        }
    }
    @Test fun abuttingNumbersWhitespaceLiteralFlagsAndFailures() {
        val (a,b) = pair("en_US", "")
        for (lenient in listOf(true, false)) {
            a.isLenient = lenient; b.calendar!!.setLenient(lenient)
            for (key in DateFormat.BooleanAttribute.entries) {
                a.setBooleanAttribute(SourceDate.BooleanAttribute.valueOf(key.name), lenient)
                if (lenient) b.attributes.add(key) else b.attributes.remove(key)
            }
            for ((pattern, texts) in listOf(
                "HHmmss" to listOf("123456", "12345", "1234", "1", "-12345", "246061", "x", ""),
                "yyyyMMdd" to listOf("20240102", "240102", "10102", "20241399", "123"),
                "yyyy-MM-dd HH:mm:ss.SSSS" to listOf("2024-01-02 12:34:56.12345", "24-1-2 3:4:5.6", "2024/01/02 12:30:00.1", "\u00a02024-1-2\u202f12:34:56.1234"),
                "MMMM, H" to listOf("January 3", "January, 3", "January. 3", "Januaryxxx3"),
                "G yyyy" to listOf("2024", "AD 2024", "garbage", " BC 1"),
                "'abc'" to listOf("abc", "axc", "ab", "x"),
                "MMMM" to listOf("January.", "Jan", "x", ""),
                "c e QQQQQ bbbbb BBBBB" to listOf("1 2 1 midnight at night", "x"))) {
                a.applyPattern(pattern); b.pattern = pattern
                for (text in texts) for (start in listOf(-1, 0, 1, text.length, text.length + 1)) compare(a,b,text,start)
            }
        }
    }
    @Test fun twoDigitYearDayPeriodsTimeZonesAndCalendarConversion() {
        for (id in listOf("en_US", "ru", "zh", "ar")) {
            val (a,b) = pair(id, "")
            for ((pattern, texts) in listOf(
                "yy-MM-dd" to listOf("69-12-31", "70-01-01", "00-01-01", "70-01-02"),
                "h B" to listOf("10 at night", "9 in the afternoon", "12 noon", "6 утра", "11 ночи"),
                "B" to listOf("at night", "noon", "midnight", "in the morning", "ночью", "утра"),
                "yyyy-MM-dd HH:mm z" to listOf("2024-07-01 12:00 EST", "2024-01-01 12:00 EDT", "2024-11-03 01:30 EST", "2024-11-03 01:30 EDT", "2024-03-10 02:30 EST"),
                "yyyy-MM-dd HH:mm VV" to listOf("2024-01-02 12:34 America/New_York", "2024-01-02 12:34 GMT+05:45"),
                "HH:mm XXX" to listOf("12:34 +05:45", "12:34 Z", "12:34 -04:00"))) {
                a.applyPattern(pattern); b.pattern = pattern
                for (text in texts) compare(a,b,text)
            }
            a.applyPattern("yyyy-MM-dd z"); b.pattern = "yyyy-MM-dd z"
            val ac = SourceCalendar.getInstance(SourceZone.getTimeZone("Europe/Minsk"), SourceLocale("th@calendar=buddhist"))
            val bc = Calendar.getInstance(TimeZone.getTimeZone("Europe/Minsk"), ULocale("th@calendar=buddhist"))
            ac.timeInMillis = 0; bc.setTimeInMillis(0)
            compare(a,b,"2024-01-02 EST",ac=ac,bc=bc,clear=false)
            compare(a,b,"broken",ac=ac,bc=bc,clear=false)
        }
    }
    @Test fun outerLeapPatternsAndNegativePositionCalendarState() {
        val (a,b) = pair("zh@calendar=chinese", "M")
        a.dateFormatSymbols = a.dateFormatSymbols.also { it.setLeapMonthPattern("leap{0}", 2, 1) }
        b.symbols!!.setLeapMonthPattern("leap{0}", 2, 1)
        for (pattern in listOf("M", "Md", "MMd", "M d")) {
            a.applyPattern(pattern); b.pattern = pattern
            for (text in listOf("leap2", "leap23", "leap2 3", "23", "2 3", "leapx")) compare(a,b,text)
        }
        val ac = SourceCalendar.getInstance(SourceZone.getTimeZone("Europe/Minsk"), SourceLocale.US)
        val bc = Calendar.getInstance(TimeZone.getTimeZone("Europe/Minsk"), ULocale.US)
        ac.timeInMillis = 0; bc.setTimeInMillis(0)
        compare(a,b,"2",start=-1,ac=ac,bc=bc,clear=false)
        assertEquals("Europe/Minsk",b.calendar!!.getTimeZone()!!.getID())
    }
    @Test fun nullableNumberFormatsAndZoneTimeTypes() {
        val a = object : Source("", SourceLocale.US) {
            override fun getNumberFormat(ch: Char): SourceNumber? = null
        }
        a.timeZone = SourceZone.getTimeZone("Etc/UTC"); a.set2DigitYearStart(java.util.Date(0))
        val b = SimpleDateFormatParseFixture("en_US", "").also { it.nf = null }
        for ((pattern,text) in listOf("MMMM" to "January", "EEEE" to "Monday", "a" to "AM", "b" to "midnight", "y" to "2024", "Q" to "1", "z" to "GMT")) {
            a.applyPattern(pattern); b.pattern = pattern
            compare(a,b,text)
        }
        val az = object : SourceZoneFormat(SourceLocale.US) {
            override fun parse(style: Style?, text: String?, pos: java.text.ParsePosition?, timeType: SourceOutput<TimeType>?): SourceZone? {
                pos!!.index++
                timeType!!.value = null
                return com.ibm.icu.util.SimpleTimeZone(-18_000_000,"custom")
            }
        }
        val bz = object : TimeZoneFormat(ULocale.US) {
            override fun parse(style: Style?, text: String?, pos: ParsePosition?, timeType: Output<TimeType>?): TimeZone? {
                pos!!.index++
                timeType!!.value = null
                return SimpleTimeZone(-18_000_000,"custom")
            }
        }
        a.setTimeZoneFormat(az); b.zoneFormatter = bz
        a.applyPattern("z"); b.pattern = "z"
        compare(a,b,"x")
    }
    @Test fun stockDateNumberFormatsAndNumericRunSuppression() {
        for (id in listOf("en_US", "ar_EG", "fa", "en@numbers=hanidec")) {
            val (a,b) = pair(id, "")
            val stock = Source("", SourceLocale(id)).numberFormat
            assertTrue(stock is com.ibm.icu.impl.DateNumberFormat)
            val digits = (stock as com.ibm.icu.impl.DateNumberFormat).digits.concatToString()
            a.numberFormat = stock
            b.nf = DateNumberFormat(b.locale, digits, com.ibm.icu.text.NumberingSystem.getInstance(SourceLocale(id)).name)
            for (pattern in listOf("yyyyMMdd", "HHmmss", "y-MM-dd", "SSSS")) {
                a.applyPattern(pattern); b.pattern = pattern
                for (text in listOf("123456", "12345", "-12345", "20240102", "12345678901234567890", digits.take(6))) compare(a,b,text)
            }
        }
    }
    @Test fun optionalDotCaseMatchingAndIntegerMutationOnFailure() {
        val (a,b) = pair("en_US", "")
        val region = Source::class.java.getDeclaredMethod("regionMatchesWithOptionalDot", String::class.java, Int::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType).also { it.isAccessible = true }
        for (text in listOf("Jan", "JAN.", "january", "i", "İ", "ı", "ſ", "s", "\uD801\uDC28", "\uD801\uDC00", "x\uD801\uDC28", "\uD801", "\uDC28")) {
            for (data in listOf("Jan.", "January", "I", "s", "\uD801\uDC00", "\uD801\uDC28.", "")) {
                for (start in -1..text.length + 1) for (length in 0..data.length) {
                    assertEquals(outcome { region.invoke(a,text,start,data,length) },
                        outcome { SimpleDateFormatFieldParseCore.regionMatchesWithOptionalDot(text,start,data,length) }, "$text/$start/$data/$length")
                }
            }
        }
        val parseInt = Source::class.java.getDeclaredMethod("parseInt", String::class.java, Int::class.javaPrimitiveType, java.text.ParsePosition::class.java, Boolean::class.javaPrimitiveType, SourceNumber::class.java).also { it.isAccessible = true }
        val an = object : SourceDecimal("0") { override fun parse(text: String?, pos: java.text.ParsePosition?): Number? { throw IllegalStateException("failure") } }
        val bn = object : DecimalFormat("0") { override fun parse(text: String?, pos: ParsePosition?): Number? { throw IllegalStateException("failure") } }
        assertEquals(outcome { parseInt.invoke(a,"12",2,java.text.ParsePosition(0),false,an) }, outcome { SimpleDateFormatFieldParseCore.parseInt("12",2,ParsePosition(0),false,bn) })
        assertEquals(an.negativePrefix,bn.getNegativePrefix())
        assertEquals("\uAB00",bn.getNegativePrefix())
        val periods = Source::class.java.getDeclaredMethod("matchDayPeriodString", String::class.java, Int::class.javaPrimitiveType, arrayOf<String>().javaClass, Int::class.javaPrimitiveType, SourceOutput::class.java).also { it.isAccessible = true }
        val malformed = arrayOfNulls<String>(DayPeriodRules.DayPeriod.VALUES.size + 1).also { it[it.lastIndex] = "extra" }
        assertEquals("bounds",outcome { periods.invoke(a,"extra",0,malformed,malformed.size,null) })
        assertEquals(outcome { periods.invoke(a,"extra",0,malformed,malformed.size,null) }, outcome { b.fields.matchDayPeriodString("extra",0,malformed,malformed.size,null) })
    }
    @Test fun midyearCenturyBoundaryAndCrossInstanceHebrewCorrection() {
        val (a,b) = pair("en_US", "yy-MM-dd")
        val century = 12_960_000_000L // 1970-05-31
        a.set2DigitYearStart(java.util.Date(century)); b.centuryStart = century
        for (text in listOf("70-01-01", "70-05-30", "70-05-31", "70-06-01", "69-12-31")) compare(a,b,text)
        val delayed = Source::class.java.getDeclaredField("DelayedHebrewMonthCheck").also { it.isAccessible = true }
        try {
            delayed.setBoolean(null,false); SimpleDateFormatFieldParseCore.delayedHebrewMonthCheck = false
            val (ah,bh) = pair("en@calendar=hebrew", "M")
            compare(ah,bh,"6")
            assertTrue(delayed.getBoolean(null)); assertTrue(SimpleDateFormatFieldParseCore.delayedHebrewMonthCheck)
            a.applyPattern("y"); b.pattern = "y"
            compare(a,b,"1971")
            assertFalse(delayed.getBoolean(null)); assertFalse(SimpleDateFormatFieldParseCore.delayedHebrewMonthCheck)
        } finally { delayed.setBoolean(null,false); SimpleDateFormatFieldParseCore.delayedHebrewMonthCheck = false }
    }
    @Test fun directFieldParserLeapMonthsAndSharedHebrewState() {
        val method = Source::class.java.declaredMethods.single { it.name == "subParse" && it.parameterCount == 11 }.also { it.isAccessible = true }
        val delayed = Source::class.java.getDeclaredField("DelayedHebrewMonthCheck").also { it.isAccessible = true }
        for (id in listOf("en_US", "en@calendar=hebrew", "zh@calendar=chinese", "ko@calendar=dangi")) {
            val (a,b) = pair(id, "M")
            val am = SourceMessage("leap{0}", SourceLocale(id))
            val bm = b.numericLeapMonthParser("leap{0}")
            for ((ch, count, texts) in listOf(Triple('M',1,listOf("leap2", "2", "6", "7")), Triple('y',2,listOf("69", "70", "5784")), Triple('L',4,listOf("2", "leap3", "January", "x")), Triple('S',5,listOf("12345", "1", "-12")), Triple('B',4,listOf("at night", "midnight", "x")))) {
                for (text in texts) for (bounded in listOf(false,true)) {
                    a.calendar.clear(); b.calendar!!.clear()
                    delayed.setBoolean(null,false); SimpleDateFormatFieldParseCore.delayedHebrewMonthCheck = false
                    val ay = booleanArrayOf(false); val by = booleanArrayOf(false)
                    val az = SourceOutput(SourceZoneFormat.TimeType.UNKNOWN); val bz = Output(TimeZoneFormat.TimeType.UNKNOWN)
                    val ad = SourceOutput<com.ibm.icu.impl.DayPeriodRules.DayPeriod>(); val bd = Output<DayPeriodRules.DayPeriod>()
                    val label = "$id/$ch/$count/$text/$bounded"
                    assertEquals(outcome { method.invoke(a,text,0,ch,count,bounded,true,ay,a.calendar,am,az,ad) },
                        outcome { b.fields.subParse(text,0,ch,count,bounded,true,by,b.calendar!!,bm,bz,bd) }, label)
                    assertEquals(ay.toList(),by.toList(),label)
                    assertEquals(az.value.name,bz.value!!.name,label)
                    assertEquals(ad.value?.name,bd.value?.name,label)
                    assertEquals(delayed.getBoolean(null),SimpleDateFormatFieldParseCore.delayedHebrewMonthCheck,label)
                    assertEquals(outcome { a.calendar.timeInMillis },outcome { b.calendar!!.getTimeInMillis() },label)
                }
            }
        }
        delayed.setBoolean(null,false); SimpleDateFormatFieldParseCore.delayedHebrewMonthCheck = false
    }
}
