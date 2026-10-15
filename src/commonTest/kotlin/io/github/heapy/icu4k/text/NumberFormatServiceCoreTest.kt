// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class NumberFormatServiceCoreTest {
    private fun decimal() = DecimalFormat("0.00", DecimalFormatSymbols(ULocale.ENGLISH))
    @Test fun singletonLocaleNameAndVisibility() {
        val f = object : NumberFormat.SimpleNumberFormatFactory(ULocale("en_CA@currency=EUR"), false) {
            override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat? = null
        }
        assertEquals(setOf("en_CA"), f.getSupportedLocaleNames())
        assertSame(f.getSupportedLocaleNames(), f.getSupportedLocaleNames())
        assertFalse(f.visible())
        @Suppress("UNCHECKED_CAST")
        val names = f.getSupportedLocaleNames() as MutableSet<String>
        assertFalse(names.remove("missing"))
        assertFailsWith<UnsupportedOperationException> { names.add("other") }
        assertFailsWith<UnsupportedOperationException> { names.clear() }
        assertEquals(0, NumberFormat.NumberFormatFactory.FORMAT_NUMBER)
        assertEquals(4, NumberFormat.NumberFormatFactory.FORMAT_INTEGER)
    }
    @Test fun factoryChainCacheCopiesAndActualLocale() {
        val calls = arrayListOf<String>()
        val service = NumberFormatServiceCore { _, _ -> null }
        val prototype = decimal()
        val first = object : NumberFormatFactory() {
            override fun getSupportedLocaleNames(): Set<String> = setOf("en")
            override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat {
                calls.add("first:${loc!!.getName()}:$formatType")
                return prototype
            }
        }
        val key = service.registerFactory(first)
        val second = object : SimpleNumberFormatFactory(ULocale("en")) {
            override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat? {
                calls.add("second:${loc!!.getName()}:$formatType")
                return null
            }
        }
        val secondKey = service.registerFactory(second)
        val requested = ULocale("en_CA@currency=EUR")
        val a = service.createInstance(requested, 0)
        val b = service.createInstance(requested, 0)
        assertNotSame(prototype, a); assertNotSame(a, b)
        assertEquals(listOf("second:en_CA@currency=EUR:0", "first:en_CA@currency=EUR:0"), calls)
        assertEquals("en@currency=EUR", a.getLocale(ULocale.ACTUAL_LOCALE)!!.getName())
        assertEquals(a.getLocale(ULocale.ACTUAL_LOCALE), a.getLocale(ULocale.VALID_LOCALE))
        (a as DecimalFormat).applyPattern("0.0000")
        assertEquals("1.00", b.format(1.0))
        assertTrue(service.unregister(secondKey)); assertFalse(service.unregister(secondKey))
        assertTrue(service.unregister(key))
        assertFailsWith<MissingResourceException> { service.createInstance(requested, 0) }
        assertFailsWith<ClassCastException> { service.unregister("wrong key") }
        assertFailsWith<NullPointerException> { service.unregister(null) }
    }
    @Test fun requestedCurrencyOverridesFallbackPrototypeForEveryCurrencyStyle() {
        val service = NumberFormatServiceCore { _, _ -> null }
        val prototype = decimal()
        prototype.setCurrency(Currency.getInstance("USD"))
        service.registerFactory(object : SimpleNumberFormatFactory(ULocale.ENGLISH) {
            override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat = prototype
        })
        for (style in 0..9) {
            val result = service.createInstance(ULocale("en_CA"), style)
            val expected = if (style in listOf(1, 5, 6, 7, 8, 9)) "CAD" else "USD"
            assertEquals(expected, result.getCurrency()!!.getCurrencyCode())
        }
        assertEquals("USD", prototype.getCurrency()!!.getCurrencyCode())
    }
    @Test fun availabilityUsesVisibleFactoriesAndReturnsToDefaultAfterRemoval() {
        val service = NumberFormatServiceCore { _, _ -> decimal() }
        val default = service.getAvailableULocales()
        assertSame(default, service.getAvailableULocales())
        val key = service.registerFactory(object : SimpleNumberFormatFactory(ULocale("zz_ZZ")) {
            override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat? = null
        })
        assertTrue(service.getAvailableULocales()!!.any { it.getName() == "zz_ZZ" })
        val hidden = service.registerFactory(object : SimpleNumberFormatFactory(ULocale("zz_ZZ"), false) {
            override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat? = null
        })
        assertFalse(service.getAvailableULocales()!!.any { it.getName() == "zz_ZZ" })
        assertTrue(service.unregister(hidden)); assertTrue(service.unregister(key))
        assertSame(default, service.getAvailableULocales())
    }
}
