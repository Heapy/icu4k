package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.CustomSymbolCurrency as JCustom
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import java.io.*
import kotlin.test.*

class CustomSymbolCurrencyOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) {
            is NullPointerException -> "null"
            is ClassCastException -> "cast"
            is IndexOutOfBoundsException -> "bounds"
            else -> e.javaClass.simpleName + ":" + e.message
        }
    }
    @Test fun namesMetadataNullableSymbolsAndEqualityMatchSource() {
        for (symbol1 in listOf("custom", "", null)) for (symbol2 in listOf("ALT", "", null)) {
            val j = JCustom("USD", symbol1, symbol2); val k = CustomSymbolCurrency("USD", symbol1, symbol2)
            assertEquals(j.currencyCode, k.getCurrencyCode()); assertEquals(j.toString(), k.toString())
            assertEquals(j.numericCode, k.getNumericCode()); assertEquals(j.defaultFractionDigits, k.getDefaultFractionDigits())
            assertEquals(outcome { j.hashCode() }, outcome { k.hashCode() })
            for (locale in listOf("en", "fr", "ar", null)) for (style in -1..6) for (choiceSize in listOf(-1, 0, 1)) {
                val ja = if (choiceSize < 0) null else BooleanArray(choiceSize) { true }
                val ka = ja?.copyOf()
                val jl = locale?.let { JLocale(it) }; val kl = locale?.let { ULocale(it) }
                assertEquals(outcome { j.getName(jl, style, ja) }, outcome { k.getName(kl, style, ka) }, "3 args $locale/$style")
                assertContentEquals(ja, ka)
                assertEquals(outcome { j.getName(jl, style, "few", ja) }, outcome { k.getName(kl, style, "few", ka) }, "4 args $locale/$style")
                assertContentEquals(ja, ka)
            }
            val jo = listOf(null, "USD", j, JCustom("USD", symbol1, symbol2), JCustom("EUR", symbol1, symbol2), JCurrency.getInstance("USD"), JCurrency.getInstance("EUR"))
            val ko = listOf(null, "USD", k, CustomSymbolCurrency("USD", symbol1, symbol2), CustomSymbolCurrency("EUR", symbol1, symbol2), Currency.getInstance("USD"), Currency.getInstance("EUR"))
            for (i in jo.indices) assertEquals(outcome { j.equals(jo[i]) }, outcome { k.equals(ko[i]) }, "equals $symbol1/$symbol2/$i")
        }
    }

    @Test fun resolutionRetainsCurrencyOrSnapshotsOverrides() {
        for (locale in listOf("en_US", "fr_FR", "ja_JP", "und")) for (code in listOf<String?>(null, "USD", "EUR")) {
            for (change in 0..3) {
                val js = JSymbols(JLocale(locale)); val ks = DecimalFormatSymbols(ULocale(locale))
                if (change and 1 != 0) { js.currencySymbol = "custom"; ks.setCurrencySymbol("custom") }
                if (change and 2 != 0) { js.internationalCurrencySymbol = "ALT"; ks.setInternationalCurrencySymbol("ALT") }
                val jc = code?.let { JCurrency.getInstance(it) }; val kc = code?.let { Currency.getInstance(it) }
                val jr = JCustom.resolve(jc, null, js); val kr = CustomSymbolCurrency.resolve(kc, null, ks)
                assertEquals(jr is JCustom, kr is CustomSymbolCurrency)
                assertEquals(jr === jc, kr === kc)
                assertEquals(jr.currencyCode, kr.getCurrencyCode()); assertEquals(jr.toString(), kr.toString())
                assertEquals(jr.hashCode(), kr.hashCode())
                js.currencySymbol = "later"; ks.setCurrencySymbol("later")
                assertEquals(jr.getName(JLocale(locale), JCurrency.SYMBOL_NAME, null), kr.getName(ULocale(locale), Currency.SYMBOL_NAME, null))
            }
        }
        val js = object : JSymbols(JLocale.US) { override fun getCurrency(): JCurrency? = null }
        val ks = object : DecimalFormatSymbols(ULocale("en_US")) { override fun getCurrency(): Currency? = null }
        assertEquals(JCustom.resolve(null, null, js).currencyCode, CustomSymbolCurrency.resolve(null, null, ks).getCurrencyCode())
        assertEquals(outcome { JCustom.resolve(null, null, null) }, outcome { CustomSymbolCurrency.resolve(null, null, null) })
    }

    @Test fun resolutionCallbackOrderAndEarlyReturn() {
        fun scenario(source: Boolean, explicit: Boolean, mismatch: Boolean): List<String> {
            val trace = mutableListOf<String>()
            val jc = object : JCurrency("USD") {
                override fun equals(other: Any?): Boolean { trace.add("currency equals"); return !mismatch }
                override fun getName(locale: JLocale?, style: Int, choice: BooleanArray?): String? { trace.add("currency name"); return "original" }
                override fun getCurrencyCode(): String? { trace.add("currency code"); return "USD" }
            }
            val kc = object : Currency("USD") {
                override fun equals(other: Any?): Boolean { trace.add("currency equals"); return !mismatch }
                override fun getName(locale: ULocale?, nameStyle: Int, isChoiceFormat: BooleanArray?): String? { trace.add("currency name"); return "original" }
                override fun getCurrencyCode(): String? { trace.add("currency code"); return "USD" }
            }
            if (source) {
                val symbols = object : JSymbols(JLocale.US) {
                    override fun getCurrency(): JCurrency { trace.add("symbols currency"); return jc }
                    override fun getCurrencySymbol(): String { trace.add("symbol1"); return "custom" }
                    override fun getInternationalCurrencySymbol(): String { trace.add("symbol2"); return "ALT" }
                    override fun getULocale(): JLocale { trace.add("locale"); return JLocale.US }
                }
                trace.clear(); JCustom.resolve(if (explicit) jc else null, null, symbols)
            } else {
                val symbols = object : DecimalFormatSymbols(ULocale("en_US")) {
                    override fun getCurrency(): Currency { trace.add("symbols currency"); return kc }
                    override fun getCurrencySymbol(): String { trace.add("symbol1"); return "custom" }
                    override fun getInternationalCurrencySymbol(): String { trace.add("symbol2"); return "ALT" }
                    override fun getULocale(): ULocale { trace.add("locale"); return ULocale("en_US") }
                }
                trace.clear(); CustomSymbolCurrency.resolve(if (explicit) kc else null, null, symbols)
            }
            return trace
        }
        for (explicit in listOf(false, true)) for (mismatch in listOf(false, true))
            assertEquals(scenario(true, explicit, mismatch), scenario(false, explicit, mismatch))
    }

    @Test fun defaultSerializationRetainsCustomClassAndSymbols() {
        fun roundtrip(value: Any): Any {
            val bytes = ByteArrayOutputStream().apply { ObjectOutputStream(this).use { it.writeObject(value) } }.toByteArray()
            return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        }
        assertEquals(2497493016770137670L, ObjectStreamClass.lookup(CustomSymbolCurrency::class.java).serialVersionUID)
        val j = JCustom("USD", "custom", "ALT"); val k = CustomSymbolCurrency("USD", "custom", "ALT")
        val jc = roundtrip(j); val kc = roundtrip(k)
        assertTrue(jc is JCustom); assertTrue(kc is CustomSymbolCurrency)
        assertEquals(j, jc); assertEquals(k, kc)
        assertEquals(jc.currencyCode, kc.getCurrencyCode()); assertEquals(jc.getSymbol(JLocale.US), kc.getSymbol(ULocale("en_US")))
    }
}
