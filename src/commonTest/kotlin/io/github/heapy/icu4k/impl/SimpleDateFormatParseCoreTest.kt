@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class SimpleDateFormatParseCoreTest {
    @Test fun abuttingNumericFieldsAndTwoDigitCentury() {
        val f = SimpleDateFormatParseFixture("en_US", "HHmmss")
        assertEquals(5, f.parse("12345").index)
        assertEquals(listOf(1, 23, 45), listOf(Calendar.HOUR_OF_DAY, Calendar.MINUTE, Calendar.SECOND).map { f.calendar!!.get(it) })
        f.pattern = "yy-MM-dd"
        assertEquals(8, f.parse("69-12-31").index)
        assertEquals(2069, f.calendar!!.get(Calendar.YEAR))
        f.parse("70-01-01")
        assertEquals(1970, f.calendar!!.get(Calendar.YEAR))
    }
    @Test fun namedFieldsOptionalDotsAndLongestMatch() {
        val f = SimpleDateFormatParseFixture("en_US", "MMM d yyyy")
        assertEquals(11, f.parse("Jan 12 2024").index)
        assertEquals(0, f.calendar!!.get(Calendar.MONTH))
        f.symbols!!.setMonths(arrayOf("a", "ab", "abc"))
        f.pattern = "MMMM"
        assertEquals(3, f.parse("abcd").index)
        assertEquals(2, f.calendar!!.get(Calendar.MONTH))
        val names = arrayOf<String?>("Jan.", "January")
        f.calendar!!.clear()
        assertEquals(3, f.fields.matchString("Jan", 0, Calendar.MONTH, names, null, f.calendar!!))
        assertEquals(-1, f.fields.matchString("x", 0, Calendar.MONTH, names, null, f.calendar!!))
        assertEquals(0, f.fields.matchQuarterString("x", 0, Calendar.MONTH, names, f.calendar!!))
    }
    @Test fun fractionalSecondsAndUnicodeWhitespace() {
        val f = SimpleDateFormatParseFixture("en_US", "HH:mm:ss.SSSSS")
        assertEquals(15, f.parse(" 12:34:56.12345").index)
        assertEquals(123, f.calendar!!.get(Calendar.MILLISECOND))
        f.pattern = "H m"
        assertEquals(5, f.parse("1\u00a0\u202f23").index)
        assertEquals(23, f.calendar!!.get(Calendar.MINUTE))
        assertEquals(2, SimpleDateFormatFieldParseCore.countDigits("x\uD835\uDFCE5", 1, 4))
    }
    @Test fun periodsAndExplicitZoneType() {
        val f = SimpleDateFormatParseFixture("en_US", "B")
        assertEquals(8, f.parse("at nightxx").index)
        assertEquals(22, f.calendar!!.get(Calendar.HOUR_OF_DAY))
        f.pattern = "yyyy-MM-dd HH:mm z"
        assertEquals(20, f.parse("2024-07-01 12:00 EST").index)
        assertEquals(1719853200000L, f.calendar!!.getTimeInMillis())
    }
    @Test fun boundedIntegerAndNegativeSuppression() {
        val nf = DecimalFormat("0")
        val pos = ParsePosition(0)
        assertEquals(123, SimpleDateFormatFieldParseCore.parseInt("123456", 3, pos, false, nf)!!.toInt())
        assertEquals(3, pos.index)
        pos.index = 0
        // Lenient DecimalFormat still recognizes the minus sign independently of its prefix.
        assertEquals(-12, SimpleDateFormatFieldParseCore.parseInt("-12", -1, pos, false, nf)!!.toInt())
        assertEquals("-", nf.getNegativePrefix())
        val dnf = DateNumberFormat(ULocale.US, "0123456789", "latn")
        pos.index = 0
        assertNull(SimpleDateFormatFieldParseCore.parseInt("-12", -1, pos, false, dnf))
        pos.index = 0
        assertEquals(-12, dnf.parse("-12", pos)!!.toInt())
    }
    @Test fun leapPatternParserAndSupplementaryCasePairs() {
        val f = SimpleDateFormatParseFixture("zh@calendar=chinese", "M")
        val cal = object : ChineseCalendar(TimeZone.getTimeZone("Etc/UTC"), ULocale("zh")) {
            fun raw(field: Int): Int = internalGet(field)
        }
        f.calendar = cal
        val leap = f.numericLeapMonthParser("leap{0}")
        f.calendar!!.clear()
        assertEquals(5, f.fields.subParse("leap2", 0, 'M', 1, false, true, booleanArrayOf(false), f.calendar!!, leap, null, null))
        assertEquals(1, cal.raw(Calendar.IS_LEAP_MONTH))
        assertEquals(1, cal.raw(Calendar.MONTH))
        assertEquals(2, SimpleDateFormatFieldParseCore.regionMatchesWithOptionalDot("\uD801\uDC28", 0, "\uD801\uDC00", 2))
    }
    @Test fun malformedPeriodNamesFailBeforeNullOutputAssignment() {
        val f = SimpleDateFormatParseFixture("en_US", "B")
        val names = arrayOfNulls<String>(DayPeriodRules.DayPeriod.VALUES.size + 1)
        names[names.lastIndex] = "extra"
        assertFailsWith<IndexOutOfBoundsException> { f.fields.matchDayPeriodString("extra", 0, names, names.size, null) }
        assertFailsWith<NullPointerException> { f.fields.matchDayPeriodString("midnight", 0, arrayOf("midnight"), 1, null) }
    }
    @Test fun failurePositionsAndLiteralFlags() {
        val f = SimpleDateFormatParseFixture("en_US", "yyyy-MM-dd")
        assertEquals(listOf(0, 4), f.parse("2024!01!02").let { listOf(it.index, it.errorIndex) })
        assertEquals(listOf(-1, 0), f.parse("2024", -1).let { listOf(it.index, it.errorIndex) })
        f.pattern = "MMMM, H"
        assertEquals(9, f.parse("January 3").index)
        f.pattern = "'abc'"
        assertEquals(1, f.parse("axc").index)
        f.attributes.remove(DateFormat.BooleanAttribute.PARSE_PARTIAL_LITERAL_MATCH)
        assertEquals(0, f.parse("axc").index)
    }
}
