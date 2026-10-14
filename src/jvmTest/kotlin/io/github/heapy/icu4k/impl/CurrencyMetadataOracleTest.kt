package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CurrencyMetaInfo
import io.github.heapy.icu4k.util.CurrencyUsage
import io.github.heapy.icu4k.util.ULocale
import java.util.Date
import kotlin.test.*
import com.ibm.icu.util.Currency as Original

class CurrencyMetadataOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) { e.javaClass.simpleName to e.message }
    @Test fun numericDigitsAndAvailabilityMatch() {
        val codes = CurrencyMetaInfo.getInstance().currencies(CurrencyMetaInfo.CurrencyFilter.all()) + listOf("ZZZ", "XTS", "XXX", "usd", "?", "", "EURO", "€UR")
        for (code in codes) {
            assertEquals(outcome { Original.getInstance(code).currencyCode }, outcome { CurrencyMetadata.normalizeCode(code) })
            if (CurrencyMetadata.isAlpha3Code(code)) {
                val j = Original.getInstance(code)
                assertEquals(j.numericCode, CurrencyMetadata.numericCode(code.uppercase()))
                for (usage in CurrencyUsage.entries) {
                    val ju = com.ibm.icu.util.Currency.CurrencyUsage.valueOf(usage.name)
                    assertEquals(j.getDefaultFractionDigits(ju), CurrencyMetadata.fractionDigits(code.uppercase(), usage))
                    assertEquals(j.getRoundingIncrement(ju), CurrencyMetadata.roundingIncrement(code.uppercase(), usage))
                }
            }
            for ((from, to) in listOf(null to null, null to 0L, 0L to null, 0L to 0L, Long.MIN_VALUE to Long.MAX_VALUE, 1L to 0L, 946684800000L to 978307200000L)) {
                assertEquals(outcome { Original.isAvailable(code, from?.let(::Date), to?.let(::Date)) }, outcome { CurrencyMetadata.isAvailable(code, from, to) }, "$code/$from/$to")
            }
        }
    }
    @Test fun regionsDatesKeywordsAndNumericResourceMatch() {
        val regions = CurrencyMetaInfo.getInstance().regions(CurrencyMetaInfo.CurrencyFilter.all()) + listOf("ZZ", "XX", "AQ")
        for (region in regions) {
            val j = com.ibm.icu.util.ULocale("und_$region")
            val k = ULocale("und_$region")
            assertEquals(Original.getInstance(j)?.currencyCode, CurrencyMetadata.regionCurrencyCode(region))
            for (date in listOf(Long.MIN_VALUE, -2208988800000L, 0L, 946684800000L, 1704067200000L, Long.MAX_VALUE)) {
                assertEquals(Original.getAvailableCurrencyCodes(j, Date(date))?.toList(), CurrencyMetadata.availableCurrencyCodes(k, date)?.toList(), "$region/$date")
            }
        }
        for (id in listOf("und", "en", "en_US", "de", "de_DE", "fr_FR", "en_US@rg=gbzzzz", "zz_ZZ")) for (key in listOf("currency", "Currency", "?")) for (common in listOf(false, true)) {
            assertContentEquals(Original.getKeywordValuesForLocale(key, com.ibm.icu.util.ULocale(id), common), CurrencyMetadata.keywordValues(key, ULocale(id), common), "$id/$key/$common")
        }
        val source = com.ibm.icu.util.UResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_BASE_NAME, "currencyNumericCodes").get("codeMap")
        for (i in 0 until source.size) {
            val record = source.get(i)
            assertEquals(record.int, CurrencyMetadata.numericCode(record.key), record.key)
        }
    }
}
