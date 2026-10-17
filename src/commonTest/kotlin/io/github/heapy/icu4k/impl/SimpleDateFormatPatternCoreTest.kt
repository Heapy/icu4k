package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.DateFormat
import io.github.heapy.icu4k.util.Calendar
import kotlin.test.*

class SimpleDateFormatPatternCoreTest {
    @Test fun quotedTextAndNumericFieldsRemainDistinct() {
        val items = SimpleDateFormatPatternCore.compile("yyyy-MM-dd'T'HH:mm''ss '年'")
        assertEquals(listOf('y', 'M', 'd', 'H', 'm', 's'), items.filterIsInstance<SimpleDateFormatPatternCore.PatternItem>().map { it.type })
        assertEquals(listOf("-", "-", "T", ":", "'", " 年"), items.filterIsInstance<String>())
        assertTrue(items.filterIsInstance<SimpleDateFormatPatternCore.PatternItem>().all { it.isNumeric })
        assertFalse(SimpleDateFormatPatternCore.isNumeric('M', 3))
        assertTrue(SimpleDateFormatPatternCore.isNumeric('M', 2))
        assertFalse(SimpleDateFormatPatternCore.isNumeric('j', 1))
    }
    @Test fun scanHandlesQuotesWithoutRejectingIncompletePatterns() {
        assertEquals(SimpleDateFormatPatternCore.Flags(false, false, true), SimpleDateFormatPatternCore.scan("'ms年"))
        assertEquals(SimpleDateFormatPatternCore.Flags(true, true, false), SimpleDateFormatPatternCore.scan("mm''ss"))
        assertEquals(listOf("unclosed"), SimpleDateFormatPatternCore.compile("'unclosed").toList())
        assertEquals("'", SimpleDateFormatPatternCore.compile("''").single())
    }
    @Test fun fieldTablesAndCache() {
        assertSame(DateFormat.Field.TIME_ZONE, SimpleDateFormatPatternCore.attribute('X'))
        assertSame(DateFormat.Field.QUARTER, SimpleDateFormatPatternCore.attribute('q'))
        assertNull(SimpleDateFormatPatternCore.attribute('j'))
        assertEquals(Calendar.EXTENDED_YEAR, SimpleDateFormatPatternCore.calendarField(SimpleDateFormatPatternCore.index('r')))
        assertEquals(-2, SimpleDateFormatPatternCore.calendarField(SimpleDateFormatPatternCore.index('B')))
        val first = SimpleDateFormatPatternCore.compile("y-MM CORE_CACHE")
        assertSame(first, SimpleDateFormatPatternCore.compile("y-MM CORE_CACHE"))
    }
}
