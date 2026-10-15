// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.impl.ICULocaleService as JService
import com.ibm.icu.impl.ICUService as JBaseService
import com.ibm.icu.text.NumberFormat as JNumber
import com.ibm.icu.text.DecimalFormat as JDecimal
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.util.*
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class NumberFormatServiceCoreOracleTest {
    private class TestService(builtin: (JLocale?, Int) -> JNumber?) : JService("NumberFormat") {
        init {
            registerFactory(object : ICUResourceBundleFactory() {
                override fun handleCreate(loc: JLocale?, kind: Int, srvc: JBaseService?): Any? = builtin(loc, kind)
            })
            markDefault()
        }
    }
    private class Source(private val shim: Any) {
        private val type = shim.javaClass
        private fun call(name: String, params: Array<Class<*>>, vararg args: Any?): Any? = type.getDeclaredMethod(name, *params).apply { isAccessible = true }.invoke(shim, *args)
        fun create(locale: JLocale?, style: Int): JNumber? = call("createInstance", arrayOf(JLocale::class.java, Int::class.javaPrimitiveType!!), locale, style) as JNumber?
        fun register(factory: JNumber.NumberFormatFactory?): Any = call("registerFactory", arrayOf(JNumber.NumberFormatFactory::class.java), factory)!!
        fun unregister(key: Any?): Boolean = call("unregister", arrayOf(Any::class.java), key) as Boolean
        fun locales(): Array<JLocale>? = call("getAvailableULocales", emptyArray()) as Array<JLocale>?
        fun javaLocales(): Array<java.util.Locale> = call("getAvailableLocales", emptyArray()) as Array<java.util.Locale>
    }
    private fun withSource(builtin: (JLocale?, Int) -> JNumber?, block: (Source) -> Unit) {
        val cls = Class.forName("com.ibm.icu.text.NumberFormatServiceShim")
        val service = cls.getDeclaredField("service").apply { isAccessible = true }
        val previous = service.get(null)
        val shim = cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        service.set(null, TestService(builtin))
        try { block(Source(shim)) } finally { service.set(null, previous) }
    }
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (e: Throwable) {
        val t = if (e is InvocationTargetException) e.targetException else e
        when (t) {
            is NullPointerException -> "NPE"
            is ClassCastException -> "cast"
            is java.util.MissingResourceException -> "missing:${t.message}:${t.className}:${t.key}"
            is MissingResourceException -> "missing:${t.message}:${t.getClassName()}:${t.getKey()}"
            else -> "${t.javaClass.simpleName}:${t.message}"
        }
    }
    private fun decimal() = DecimalFormat("0.00", DecimalFormatSymbols(ULocale.ENGLISH))
    private fun jdecimal() = JDecimal("0.00", JSymbols(JLocale.ENGLISH))
    @Test fun callbackFallbackCacheCurrencyCloneAndRegistration() = withSource({ _, _ -> null }) { s ->
        val t = NumberFormatServiceCore { _, _ -> null }
        val se = arrayListOf<String>(); val te = arrayListOf<String>()
        val sp = jdecimal(); val tp = decimal()
        sp.currency = com.ibm.icu.util.Currency.getInstance("USD"); tp.setCurrency(Currency.getInstance("USD"))
        fun sourceFactory(label: String, result: Boolean) = object : JNumber.NumberFormatFactory() {
            override fun getSupportedLocaleNames(): Set<String> = if (label == "second") setOf("en", "en_CA") else setOf("en")
            override fun createFormat(loc: JLocale?, formatType: Int): JNumber? { se.add("$label:${loc?.name}:$formatType"); return if (result) sp else null }
        }
        fun targetFactory(label: String, result: Boolean) = object : NumberFormatFactory() {
            override fun getSupportedLocaleNames(): Set<String> = if (label == "second") setOf("en", "en_CA") else setOf("en")
            override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat? { te.add("$label:${loc?.getName()}:$formatType"); return if (result) tp else null }
        }
        val sa = s.register(sourceFactory("first", true)); val ta = t.registerFactory(targetFactory("first", true))
        val sb = s.register(sourceFactory("second", false)); val tb = t.registerFactory(targetFactory("second", false))
        for (id in listOf("en_CA", "en_CA@currency=EUR", "en_US_POSIX")) for (style in 0..9) repeat(2) {
            val a = s.create(JLocale(id), style)!!
            val b = t.createInstance(ULocale(id), style)
            assertEquals(a.currency?.currencyCode, b.getCurrency()?.getCurrencyCode())
            assertEquals(a.getLocale(JLocale.ACTUAL_LOCALE)?.name, b.getLocale(ULocale.ACTUAL_LOCALE)?.getName())
            assertEquals(a.getLocale(JLocale.VALID_LOCALE)?.name, b.getLocale(ULocale.VALID_LOCALE)?.getName())
            assertEquals(a.format(1.25), b.format(1.25))
            assertNotSame(sp, a); assertNotSame(tp, b)
            (a as JDecimal).applyPattern("0.000"); (b as DecimalFormat).applyPattern("0.000")
            assertEquals(se, te)
        }
        assertEquals(s.unregister(sb), t.unregister(tb)); assertEquals(s.unregister(sb), t.unregister(tb))
        assertEquals(s.unregister(sa), t.unregister(ta))
        assertEquals(outcome { s.create(JLocale.ENGLISH, 0) }, outcome { t.createInstance(ULocale.ENGLISH, 0) })
        for (key in listOf(null, "wrong key")) assertEquals(outcome { s.unregister(key) }, outcome { t.unregister(key) })
        assertEquals(outcome { s.register(null) }, outcome { t.registerFactory(null) })
    }
    @Test fun defaultFactoryCacheAndAvailableLocales() = withSource({ _, _ -> jdecimal() }) { s ->
        val calls = arrayListOf<String>()
        val t = NumberFormatServiceCore { loc, kind -> calls.add("${loc?.getName()}:$kind"); decimal() }
        val sourceDefault = s.locales()
        val targetDefault = t.getAvailableULocales()
        assertEquals(sourceDefault!!.map { it.name }, targetDefault!!.map { it.getName() })
        assertSame(sourceDefault, s.locales()); assertSame(targetDefault, t.getAvailableULocales())
        assertEquals(s.javaLocales().toList(), t.getAvailableLocales().toList())
        assertSame(t.getAvailableLocales(), t.getAvailableLocales())
        val a = t.createInstance(ULocale.ENGLISH, 0)
        val b = t.createInstance(ULocale.ENGLISH, 0)
        assertNotSame(a, b); assertEquals(listOf("en:0"), calls)
        val sf = object : JNumber.SimpleNumberFormatFactory(JLocale("zz_ZZ")) {}
        val tf = object : SimpleNumberFormatFactory(ULocale("zz_ZZ")) {}
        val sk = s.register(sf); val tk = t.registerFactory(tf)
        assertEquals(s.locales()!!.map { it.name }.toSet(), t.getAvailableULocales()!!.map { it.getName() }.toSet())
        assertEquals(s.javaLocales().toSet(), t.getAvailableLocales().toSet())
        assertEquals(s.unregister(sk), t.unregister(tk))
        assertSame(targetDefault, t.getAvailableULocales())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> nullClone(): T = null as T
    @Test fun nullClonePreservesCurrencyArgumentEvaluationBeforeReceiverFailure() = withSource({ _, _ -> null }) { s ->
        val t = NumberFormatServiceCore { _, _ -> null }
        val sp = object : JDecimal("0", JSymbols(JLocale.ENGLISH)) {
            override fun clone(): JDecimal = nullClone()
        }
        val tp = object : DecimalFormat("0", DecimalFormatSymbols(ULocale.ENGLISH)) {
            override fun clone(): DecimalFormat = nullClone()
        }
        s.register(object : JNumber.SimpleNumberFormatFactory(JLocale.ENGLISH) {
            override fun createFormat(loc: JLocale?, formatType: Int): JNumber = sp
        })
        t.registerFactory(object : SimpleNumberFormatFactory(ULocale.ENGLISH) {
            override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat = tp
        })
        for (id in listOf("en@currency=INVALID", "en_CA")) for (style in listOf(0, 1, 5, 6, 7, 8, 9)) {
            assertEquals(outcome { s.create(JLocale(id), style) }, outcome { t.createInstance(ULocale(id), style) }, "$id $style")
        }
    }
}
