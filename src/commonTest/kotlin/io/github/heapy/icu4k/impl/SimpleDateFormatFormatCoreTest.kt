package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class SimpleDateFormatFormatCoreTest {
    @Test fun numericFieldsQuotedLiteralsAndPositions() {
        val owner = SimpleDateFormatFormatFixture("en_US", "yyyy-MM-dd'T'HH:mm:ss.SSS XXX")
        assertEquals("1970-01-01T00:00:00.000 Z", owner.render(0))
        val buf = StringBuilder("prefix:")
        val pos = FormatFieldPosition(DateFormat.Field.YEAR)
        val attrs = ArrayList<FormatFieldPosition>()
        owner.core.format(owner.calendar, buf, pos, attrs)
        assertEquals(7, pos.getBeginIndex()); assertEquals(11, pos.getEndIndex())
        assertEquals(8, attrs.size)
        assertSame(DateFormat.Field.YEAR, attrs.first().getFieldAttribute())
        owner.useFastFormat = false
        assertEquals("1970-01-01T00:00:00.000 Z", owner.render(0))
    }
    @Test fun dayPeriodFallbackUsesOwnerAndOutputField() {
        val owner = SimpleDateFormatFormatFixture("en_US", "b B")
        assertEquals("AM in the morning", owner.render(0))
        assertTrue("field:a:b:0" in owner.trace)
        owner.pattern = "B"
        owner.locale = ULocale("zz")
        val pos = FormatFieldPosition(DateFormat.FLEXIBLE_DAY_PERIOD_FIELD)
        val output = StringBuilder()
        owner.core.format(owner.calendar, output, pos)
        assertEquals("AM", output.toString()); assertEquals(2, pos.getEndIndex())
    }
    @Test fun capitalizationAndLiteralPrefix() {
        val owner = SimpleDateFormatFormatFixture("ru", "MMMM")
        owner.context = DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE
        assertEquals("Января", owner.render(0))
        owner.pattern = "'x 'MMMM"
        assertEquals("x января", owner.render(0))
    }
    @Test fun hebrewAdarInLeapAndCommonYears() {
        val owner = SimpleDateFormatFormatFixture("en_US@calendar=hebrew", "M MMMM")
        owner.calendar!!.clear(); owner.calendar!!.set(5784, 6, 1)
        assertEquals("7 Adar II", owner.core.format(owner.calendar, StringBuilder(), FormatFieldPosition(-1)).toString())
        owner.calendar!!.clear(); owner.calendar!!.set(5783, 6, 1)
        assertEquals("6 Adar", owner.core.format(owner.calendar, StringBuilder(), FormatFieldPosition(-1)).toString())
    }
    @Test fun chineseLeapMonthPatternsAndCyclicYear() {
        val owner = SimpleDateFormatFormatFixture("zh@calendar=chinese", "G y U MMMM M LLLL L")
        assertEquals("78 40 癸卯 闰二月 闰2 闰二月 闰2", owner.render(1679443200000L))
    }
    @Test fun capturedFastDigitsAndPrimaryFractionDigitsHaveSeparateState() {
        val owner = SimpleDateFormatFormatFixture("en_US", "yyyy SSS")
        owner.setNumber(DateNumberFormat(ULocale("en_US_SDFFASTDIGITS"), '0', "latn"))
        (owner.numberFormat as DateNumberFormat).setZeroDigit('٠')
        assertEquals("1970 ٠٠٠", owner.render(0))
        val copied = SimpleDateFormatNumberCore().also { it.copyStateFrom(owner.numbers) }
        val output = StringBuilder()
        copied.format(null, output, 123, 5, 10)
        assertEquals("00123", output.toString())
    }
    @Test fun unknownFieldsAndInvalidOutputPositions() {
        val owner = SimpleDateFormatFormatFixture("en_US", "jj")
        assertFailsWith<IllegalArgumentException> { owner.render(0) }
        owner.pattern = "l"
        assertEquals("", owner.render(0))
        assertFailsWith<IndexOutOfBoundsException> { owner.core.formatField(StringBuilder(), 'y', 4, 0, 0, null, FormatFieldPosition(-1), '?', owner.calendar) }
    }
}
