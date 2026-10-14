@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.text.CurrencyMetaInfo.CurrencyFilter
import io.github.heapy.icu4k.util.CurrencyUsage
import io.github.heapy.icu4k.impl.Grego
import kotlin.test.*

class CurrencyMetaInfoTest {
    @Test fun currencyDataFiltersAndCashDigits() {
        assertTrue(CurrencyMetaInfo.hasData())
        val meta = CurrencyMetaInfo.getInstance()
        assertSame(meta, CurrencyMetaInfo.getInstance(false)); assertSame(meta, CurrencyMetaInfo.getInstance(true))
        assertEquals(listOf("USD"), meta.currencies(CurrencyFilter.onRegion("US").withDate(0).withTender()))
        assertTrue("AQ" in meta.regions(null))
        assertEquals(5, meta.currencyDigits("CHF", CurrencyUsage.CASH).roundingIncrement)
        assertEquals(0, meta.currencyDigits("CHF", null).roundingIncrement)
        assertEquals(2, meta.currencyDigits("bogus").fractionDigits)
        val rows = meta.currencyInfo(CurrencyFilter.onRegion("DE"))
        assertTrue(rows.any { it.code == "EUR" }); assertTrue(rows.any { it.code == "DEM" })
        assertFailsWith<UnsupportedOperationException> { (rows as MutableList).clear() }
    }
    @Test fun calendarAndFilterBoundaries() {
        assertEquals(0L, Grego.fieldsToDay(1970, 0, 1))
        assertEquals("1969-12-31T23:59:59.999Z", Grego.timeToString(-1))
        assertEquals("2000-02-29T00:00:00.000Z", Grego.timeToString(Grego.fieldsToDay(2000, 1, 29) * Grego.MILLIS_PER_DAY))
        assertFalse(Grego.isLeapYear(1900)); assertTrue(Grego.isLeapYear(2000))
        val filter = CurrencyFilter.all().withRegion("DE").withDateRange(2, 1).withTender()
        assertEquals(2, filter.from); assertEquals(1, filter.to)
        assertEquals(filter, CurrencyFilter.onRegion("DE").withDateRange(2, 1).withTender())
        assertTrue(CurrencyFilter.all().toString().contains("tenderOnly='false'"))
        assertFalse(CurrencyFilter.all().toString().contains("from="))
        assertFailsWith<IndexOutOfBoundsException> { Grego.monthLength(2001, -1) }
    }
}
