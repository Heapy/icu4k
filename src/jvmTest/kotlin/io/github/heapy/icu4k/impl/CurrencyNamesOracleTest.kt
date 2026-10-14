package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CurrencyMetaInfo
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencyNamesOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) { e.javaClass.simpleName to if (e is NullPointerException) null else e.message }
    @Test fun everyCurrencyStyleAndPluralMatchSource() {
        val codes = CurrencyMetaInfo.getInstance().currencies(CurrencyMetaInfo.CurrencyFilter.all()) + "ZZZ"
        for (id in listOf("root", "en", "fr", "ar", "ja", "he", "es", "de", "zh", "ru", "hi", "it", "pt", "ko", "nl")) {
            val jloc = com.ibm.icu.util.ULocale(id); val kloc = ULocale(id)
            for (code in codes) {
                val j = com.ibm.icu.util.Currency.getInstance(code)
                for (style in -1..6) {
                    val a = booleanArrayOf(true, true); val b = a.copyOf()
                    assertEquals(outcome { j.getName(jloc, style, a) }, outcome { CurrencyNames.name(code, kloc, style, b) }, "$id/$code/$style")
                    assertContentEquals(a, b)
                    for (plural in listOf("one", "other", "few", "bogus", null)) {
                        a[0] = true; b[0] = true
                        assertEquals(outcome { j.getName(jloc, style, plural, a) }, outcome { CurrencyNames.name(code, kloc, style, plural, b) }, "$id/$code/$style/$plural")
                        assertContentEquals(a, b)
                    }
                }
                assertEquals(j.getSymbol(jloc), CurrencyNames.symbol(code, kloc))
            }
        }
    }
    @Test fun emptyChoiceArrayPrecedesStyleValidation() {
        val j = com.ibm.icu.util.Currency.getInstance("USD")
        for (style in listOf(-1, 0, 1, 2, 5, 6)) {
            assertEquals(outcome { j.getName(com.ibm.icu.util.ULocale("en"), style, booleanArrayOf()) }, outcome { CurrencyNames.name("USD", ULocale("en"), style, booleanArrayOf()) })
            assertEquals(outcome { j.getName(com.ibm.icu.util.ULocale("en"), style, "other", booleanArrayOf()) }, outcome { CurrencyNames.name("USD", ULocale("en"), style, "other", booleanArrayOf()) })
        }
    }
}
