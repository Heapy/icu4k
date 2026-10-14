@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.text.CurrencyMetaInfo.CurrencyFilter
import io.github.heapy.icu4k.util.CurrencyUsage
import kotlin.test.*
import com.ibm.icu.text.CurrencyMetaInfo as Original
import com.ibm.icu.util.Currency.CurrencyUsage as OriginalUsage

class CurrencyMetaInfoOracleTest {
    @Test fun completeMetadataQueriesAndRecordBoundariesMatch() {
        val j = Original.getInstance(); val k = CurrencyMetaInfo.getInstance()
        assertEquals(Original.hasData(), CurrencyMetaInfo.hasData())
        fun compare(f: CurrencyFilter?) {
            val jf = f?.let { Original.CurrencyFilter.all().withRegion(it.region).withCurrency(it.currency).withDateRange(it.from, it.to).let { result -> if (it.tenderOnly) result.withTender() else result } }
            assertEquals(j.currencyInfo(jf).map { listOf(it.region, it.code, it.from, it.to, it.priority, it.isTender) }, k.currencyInfo(f).map { listOf(it.region, it.code, it.from, it.to, it.priority, it.isTender()) })
            assertEquals(j.currencies(jf), k.currencies(f)); assertEquals(j.regions(jf), k.regions(f))
        }
        compare(null); compare(CurrencyFilter.onTender())
        for (region in j.regions(null) + listOf("bogus", "de", "ZZ")) {
            compare(CurrencyFilter.onRegion(region)); compare(CurrencyFilter.onRegion(region).withTender())
        }
        for (code in j.currencies(null) + listOf("XXX", "bogus", "usd")) {
            compare(CurrencyFilter.onCurrency(code))
            for (usage in listOf(CurrencyUsage.STANDARD, CurrencyUsage.CASH, null)) {
                val jd = j.currencyDigits(code, usage?.let { OriginalUsage.valueOf(it.name) }); val kd = k.currencyDigits(code, usage)
                assertEquals(jd.toString(), kd.toString())
            }
        }
        for (row in j.currencyInfo(null)) {
            val filter = CurrencyFilter.onRegion(row.region).withCurrency(row.code)
            compare(filter.withDateRange(row.from, row.to)); compare(filter.withDateRange(row.to, row.from))
            compare(filter.withDate(row.from)); compare(filter.withDate(row.to))
            if (row.from != Long.MIN_VALUE) compare(filter.withDate(row.from - 1))
            if (row.to != Long.MAX_VALUE) compare(filter.withDate(row.to + 1))
        }
    }
    @Test fun filtersHashesDebugStringsAndMutableDatesMatch() {
        for (region in listOf(null, "DE", "", "a'b")) for (currency in listOf(null, "EUR", "USD")) for (time in listOf(Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE)) {
            val j = Original.CurrencyFilter.onRegion(region).withCurrency(currency).withDate(time)
            val k = CurrencyFilter.onRegion(region).withCurrency(currency).withDate(time)
            assertEquals(j.hashCode(), k.hashCode()); assertEquals(j.toString(), k.toString())
            assertEquals(j.equals(j.withTender()), k.equals(k.withTender()))
        }
        val j = Original.CurrencyInfo(null, "USD", 0, 1, -1, false)
        val k = CurrencyMetaInfo.CurrencyInfo(null, "USD", 0, 1, -1, false)
        assertEquals(j.toString(), k.toString())
        fun changingDate() = object : java.util.Date() { private var calls = 0L; override fun getTime(): Long = calls++ }
        val jf = Original.CurrencyFilter.onDate(changingDate()); val kf = CurrencyFilter.onDate(changingDate())
        assertEquals(jf.from, kf.from); assertEquals(jf.to, kf.to)
        assertEquals(Original.CurrencyFilter.onDateRange(null, null).toString(), CurrencyFilter.onDateRange(null, null).toString())
    }
}
