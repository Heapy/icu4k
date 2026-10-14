@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*
import com.ibm.icu.impl.CurrencyData as OriginalData
import com.ibm.icu.impl.ICUCurrencyDisplayInfoProvider as OriginalProvider
import com.ibm.icu.text.CurrencyDisplayNames as OriginalNames

class CurrencyDisplayNamesOracleTest {
    private fun format(v: OriginalData.CurrencyFormatInfo?): Any? = v?.let { listOf(it.isoCode, it.currencyPattern, it.monetaryDecimalSeparator, it.monetaryGroupingSeparator) }
    private fun format(v: CurrencyData.CurrencyFormatInfo?): Any? = v?.let { listOf(it.isoCode, it.currencyPattern, it.monetaryDecimalSeparator, it.monetaryGroupingSeparator) }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Exception) { failure.javaClass.simpleName }
    @Test fun allCurrenciesMapsPluralFallbackAndVariantsMatch() {
        val jp = OriginalProvider(); val kp = ICUCurrencyDisplayInfoProvider()
        val codes = com.ibm.icu.text.CurrencyMetaInfo.getInstance().currencies(null) + listOf("bogus", "usd", "XXX", "", "null")
        for (locale in listOf("root", "en", "en_CA", "en_GB", "fr", "ar", "ja", "es", "es_MX", "he", "zh_TW", "zzzz")) for (fallback in listOf(false, true)) {
            val j = jp.getInstance(com.ibm.icu.util.ULocale(locale), fallback)
            val k = kp.getInstance(ULocale(locale), fallback)
            assertEquals(j == null, k == null, "$locale/$fallback"); if (j == null || k == null) continue
            assertEquals(j.uLocale.toString(), k.getULocale().toString())
            assertEquals(j.symbolMap(), k.symbolMap(), "$locale symbols")
            assertEquals(j.nameMap(), k.nameMap(), "$locale names")
            assertEquals(j.unitPatterns, k.getUnitPatterns())
            val js = j.spacingInfo; val ks = k.getSpacingInfo()!!
            assertEquals(js.hasBeforeCurrency, ks.hasBeforeCurrency); assertEquals(js.hasAfterCurrency, ks.hasAfterCurrency)
            assertContentEquals(js.beforeSymbols, ks.getBeforeSymbols()); assertContentEquals(js.afterSymbols, ks.getAfterSymbols())
            for (code in codes) {
                assertEquals(outcome { j.getName(code) }, outcome { k.getName(code) }, "$locale/$fallback/$code name")
                assertEquals(outcome { j.getSymbol(code) }, outcome { k.getSymbol(code) })
                assertEquals(outcome { j.getNarrowSymbol(code) }, outcome { k.getNarrowSymbol(code) })
                assertEquals(outcome { j.getFormalSymbol(code) }, outcome { k.getFormalSymbol(code) })
                assertEquals(outcome { j.getVariantSymbol(code) }, outcome { k.getVariantSymbol(code) })
                assertEquals(outcome { format(j.getFormatInfo(code)) }, outcome { format(k.getFormatInfo(code)) })
                for (plural in listOf("zero", "one", "two", "few", "many", "other", "0", "1", "=0", "=1", "bogus"))
                    assertEquals(outcome { j.getPluralName(code, plural) }, outcome { k.getPluralName(code, plural) }, "$locale/$fallback/$code/$plural")
            }
        }
        assertEquals(OriginalNames.getInstance(java.util.Locale.FRENCH).getName("USD"), CurrencyDisplayNames.getInstance(java.util.Locale.FRENCH)!!.getName("USD"))
    }
    @Test fun nullCacheKeysAndMutableMapsMatch() {
        for (mode in 0..3) {
            val j = OriginalProvider().getInstance(com.ibm.icu.util.ULocale.ENGLISH, true)
            val k = ICUCurrencyDisplayInfoProvider().getInstance(ULocale.ENGLISH, true)!!
            fun jcall(code: String?): Any? = when (mode) { 0 -> j.getName(code); 1 -> j.getNarrowSymbol(code); 2 -> j.getPluralName(code, "one"); else -> j.getFormatInfo(code) }
            fun kcall(code: String?): Any? = when (mode) { 0 -> k.getName(code); 1 -> k.getNarrowSymbol(code); 2 -> k.getPluralName(code, "one"); else -> k.getFormatInfo(code) }
            for (code in listOf(null, null, "USD")) assertEquals(outcome { jcall(code) }, outcome { kcall(code) })
        }
        val j = OriginalProvider().getInstance(com.ibm.icu.util.ULocale.ENGLISH, true)
        val k = ICUCurrencyDisplayInfoProvider().getInstance(ULocale.ENGLISH, true)!!
        j.symbolMap()["custom"] = "ZZZ"; (k.symbolMap() as MutableMap)["custom"] = "ZZZ"
        assertEquals(j.symbolMap(), k.symbolMap())
        j.unitPatterns["invented"] = "custom"; (k.getUnitPatterns() as MutableMap)["invented"] = "custom"
        assertEquals(j.unitPatterns, k.getUnitPatterns())
    }
    @Test fun pluralCharSequenceCallbacksMatch() {
        class Input(private val text: String, val trace: MutableList<String>) : CharSequence {
            override val length: Int get() { trace.add("length"); return text.length }
            override fun get(index: Int): Char { trace.add("get$index"); return text[index] }
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = error("unused")
            override fun toString(): String { trace.add("string"); return text }
        }
        for (text in listOf("0", "1", "q", "=0", "few", "many", "other", "wrong")) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            assertEquals(outcome { com.ibm.icu.impl.StandardPlural.fromString(Input(text, jt)).ordinal }, outcome { StandardPlural.fromString(Input(text, kt)).ordinal })
            assertEquals(jt, kt)
        }
    }
    @Test fun standardPluralKeywordsAndDefaultsMatch() {
        for (keyword in listOf("zero", "one", "two", "few", "many", "other", "0", "1", "=0", "=1", "", "OTHER", "wrong", null)) {
            assertEquals(outcome { com.ibm.icu.impl.StandardPlural.orNullFromString(keyword)?.ordinal }, outcome { StandardPlural.orNullFromString(keyword)?.ordinal })
            assertEquals(outcome { com.ibm.icu.impl.StandardPlural.indexFromString(keyword) }, outcome { StandardPlural.indexFromString(keyword) })
        }
        for (fallback in listOf(false, true)) {
            val j = OriginalData.DefaultInfo.getWithFallback(fallback); val k = CurrencyData.DefaultInfo.getWithFallback(fallback)
            for (code in listOf("USD", "bogus", null)) {
                assertEquals(j.getName(code), k.getName(code)); assertEquals(j.getSymbol(code), k.getSymbol(code)); assertEquals(j.getPluralName(code, null), k.getPluralName(code, null))
            }
            assertEquals(j.unitPatterns, k.getUnitPatterns()); assertEquals(j.spacingInfo == null, k.getSpacingInfo() == null)
        }
    }
}
