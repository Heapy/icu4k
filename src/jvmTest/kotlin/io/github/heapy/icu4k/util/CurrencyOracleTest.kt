@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.text.ParsePosition
import java.util.Date
import java.util.Locale
import kotlin.test.*

class CurrencyOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Exception) { e.javaClass.simpleName + if (e is NullPointerException || e is ClassCastException) "" else ":${e.message}" }
    @Test fun publicMetadataNamesAndFactoriesMatchSource() {
        for (code in listOf(null, "", "1", "US1", "éur", "usd", "USD", "EUR", "CHF", "BYR", "DEM", "ZZZ", "JPY", "MGA", "BHD")) {
            assertEquals(outcome { JCurrency.getInstance(code).toString() }, outcome { Currency.getInstance(code).toString() }, code)
            if (code == null || code.length != 3 || code.any { it !in 'a'..'z' && it !in 'A'..'Z' }) continue
            val j = JCurrency.getInstance(code); val k = Currency.getInstance(code)
            assertSame(k, Currency.getInstance(code.uppercase()))
            assertEquals(j.currencyCode, k.getCurrencyCode()); assertEquals(j.numericCode, k.getNumericCode())
            assertEquals(j.hashCode(), k.hashCode())
            for (usage in JCurrency.CurrencyUsage.entries + listOf(null)) {
                val ku = usage?.let { Currency.CurrencyUsage.valueOf(it.name) }
                assertEquals(j.getDefaultFractionDigits(usage), k.getDefaultFractionDigits(ku))
                assertEquals(j.getRoundingIncrement(usage), k.getRoundingIncrement(ku))
            }
            for (locale in listOf("en_US", "en_CA", "fr", "de", "ru", "zh_TW", "ja", "ar", "zz")) {
                val jl = JLocale(locale); val kl = ULocale(locale)
                assertEquals(j.getSymbol(jl), k.getSymbol(kl))
                assertEquals(j.getSymbol(jl.toLocale()), k.getSymbol(Locale.forLanguageTag(jl.toLanguageTag())))
                for (style in -1..6) {
                    val ja = booleanArrayOf(true); val ka = booleanArrayOf(true)
                    assertEquals(outcome { j.getName(jl, style, ja) }, outcome { k.getName(kl, style, ka) })
                    assertContentEquals(ja, ka)
                    assertEquals(outcome { j.getName(jl, style, "few", ja) }, outcome { k.getName(kl, style, "few", ka) })
                    assertContentEquals(ja, ka)
                }
            }
            assertEquals(j.displayName, k.getDisplayName()); assertEquals(j.symbol, k.getSymbol())
        }
        for (locale in listOf("", "und", "en", "en_US", "en_US_PREEURO", "de_DE", "de_DE_PREEURO", "zz_ZZ", "en_US@currency=eur", "en_US@rg=gbzzzz", "en_US@currency=invalid")) {
            val jl = JLocale(locale); val kl = ULocale(locale)
            assertEquals(outcome { JCurrency.getInstance(jl)?.currencyCode }, outcome { Currency.getInstance(kl)?.getCurrencyCode() }, locale)
            for (date in listOf(Long.MIN_VALUE, 0L, 946684800000L, 1704067200000L, Long.MAX_VALUE)) {
                assertEquals(JCurrency.getAvailableCurrencyCodes(jl, Date(date))?.toList(), Currency.getAvailableCurrencyCodes(kl, Date(date))?.toList(), "$locale/$date")
            }
            for (key in listOf(null, "currency", "other")) for (common in listOf(false, true)) {
                assertEquals(JCurrency.getKeywordValuesForLocale(key, jl, common).toList(), Currency.getKeywordValuesForLocale(key, kl, common).toList(), "$locale/$key/$common")
            }
        }
        assertEquals(JCurrency.getAvailableCurrencies().map { it.currencyCode }.toSet(), Currency.getAvailableCurrencies().map { it.getCurrencyCode() }.toSet())
        for (code in listOf(null, "", "USD", "usd", "ZZZ", "DEM")) for ((from, to) in listOf(null to null, 0L to null, null to 0L, 1000L to 0L)) {
            assertEquals(outcome { JCurrency.isAvailable(code, from?.let(::Date), to?.let(::Date)) }, outcome { Currency.isAvailable(code, from, to) }, "$code/$from/$to")
        }
        assertSame(Currency.getInstance("USD"), Currency.fromJavaCurrency(java.util.Currency.getInstance("USD")))
        assertEquals(java.util.Currency.getInstance("USD"), Currency.getInstance("USD").toJavaCurrency())
    }
    @Test fun parsingAndTrieFacadeMatchSource() {
        for (locale in listOf("en", "fr", "ru", "ja")) for (text in listOf("USD", "$", "US dollars", "euro", "€", "Japanese yen", "no currency")) for (type in listOf(0, 1, 2, -1)) {
            val jp = java.text.ParsePosition(0); val kp = ParsePosition(0)
            assertEquals(JCurrency.parse(JLocale(locale), text, type, jp), Currency.parse(ULocale(locale), text, type, kp))
            assertEquals(jp.index, kp.index); assertEquals(jp.errorIndex, kp.errorIndex)
            val jt = JCurrency.getParsingTrie(JLocale(locale), type); val kt = Currency.getParsingTrie(ULocale(locale), type)
            val jl = com.ibm.icu.impl.TextTrieMap.Output(); val kl = io.github.heapy.icu4k.impl.TextTrieMap.Output()
            val ji = jt.get(text, 0, jl); val ki = kt.get(text, 0, kl)
            fun jlist() = buildList { if (ji != null) while (ji.hasNext()) { val value = ji.next(); add(value.getISOCode() to value.getCurrencyString()) } }
            fun klist() = buildList { if (ki != null) while (ki.hasNext()) { val value = ki.next(); add(value.getISOCode() to value.getCurrencyString()) } }
            assertEquals(jlist(), klist()); assertEquals(jl.matchLength, kl.matchLength); assertEquals(jl.partialMatch, kl.partialMatch)
            assertSame(kt, Currency.getParsingTrie(ULocale(locale), type))
        }
    }
    @Test fun serviceRegistrationFallbackAndUnregisterMatchSource() {
        assertEquals(JCurrency.getAvailableULocales().map { it.name }.toSet(), Currency.getAvailableULocales().map { it.getName() }.toSet())
        val jr = JCurrency.registerInstance(JCurrency.getInstance("USD"), JLocale("zz_ZZ"))
        val kr = Currency.registerInstance(Currency.getInstance("USD"), ULocale("zz_ZZ"))
        val jr2 = JCurrency.registerInstance(JCurrency.getInstance("EUR"), JLocale("zz_ZZ"))
        val kr2 = Currency.registerInstance(Currency.getInstance("EUR"), ULocale("zz_ZZ"))
        fun check() {
            for (locale in listOf("zz_ZZ", "zz_ZZ_VARIANT", "zz", "en_US", "zz_ZZ@currency=chf")) {
                assertEquals(JCurrency.getInstance(JLocale(locale))?.currencyCode, Currency.getInstance(ULocale(locale))?.getCurrencyCode(), locale)
            }
            assertEquals(JCurrency.getAvailableULocales().map { it.name }.toSet(), Currency.getAvailableULocales().map { it.getName() }.toSet())
            assertEquals(JCurrency.getAvailableLocales().map { it.toLanguageTag() }.toSet(), Currency.getAvailableLocales().map { it.toLanguageTag() }.toSet())
        }
        try {
            check()
            assertEquals(JCurrency.unregister(jr2), Currency.unregister(kr2)); check()
            assertEquals(outcome { JCurrency.unregister(Any()) }, outcome { Currency.unregister(Any()) })
        } finally {
            JCurrency.unregister(jr2); Currency.unregister(kr2)
            assertEquals(JCurrency.unregister(jr), Currency.unregister(kr))
        }
        assertEquals(JCurrency.unregister(jr), Currency.unregister(kr))
        assertEquals(outcome { JCurrency.unregister(null) }, outcome { Currency.unregister(null) })
    }
}
