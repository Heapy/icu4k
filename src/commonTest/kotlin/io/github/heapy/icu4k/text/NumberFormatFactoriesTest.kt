// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class NumberFormatFactoriesTest {
    @Test fun numericAndAlgorithmicFactories() {
        val locale = ULocale("en_US")
        assertEquals("1,234.5", NumberFormat.getNumberInstance(locale).format(1234.5))
        assertEquals("1,234", NumberFormat.getIntegerInstance(locale).format(1234.5))
        assertEquals("12%", NumberFormat.getPercentInstance(locale).format(0.12))
        val roman = NumberFormat.getInstance(ULocale("en_US@numbers=roman"))
        assertIs<RuleBasedNumberFormat>(roman)
        assertEquals("MMXXVI", roman.format(2026L))
        val copy = NumberFormat.getInstance(ULocale("en_US@numbers=roman"))
        assertNotSame(roman, copy)
        roman.setMaximumFractionDigits(0)
        assertEquals(3, copy.getMaximumFractionDigits())
        assertFailsWith<IllegalArgumentException> { NumberFormat.getInstance(locale, -1) }
        assertFailsWith<IllegalArgumentException> { NumberFormat.registerFactory(null) }
        assertFailsWith<IllegalArgumentException> { NumberFormat.unregister(null) }
    }
    @Test fun registrationFallbackVisibilityAndRemoval() {
        val locale = ULocale("qaa_XY")
        val calls = mutableListOf<String>()
        val factory = object : SimpleNumberFormatFactory(locale) {
            override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat? {
                calls.add("${loc!!.getName()}:$formatType")
                return if (formatType == NumberFormat.NUMBERSTYLE) DecimalFormat("'custom '0") else null
            }
        }
        val key = NumberFormat.registerFactory(factory)
        try {
            assertTrue(NumberFormat.getAvailableULocales()!!.contains(locale))
            assertEquals("custom 42", NumberFormat.getInstance(locale).format(42L))
            assertEquals(listOf("qaa_XY:0"), calls)
            assertEquals("12%", NumberFormat.getPercentInstance(locale).format(0.12))
        } finally { assertTrue(NumberFormat.unregister(key)) }
        assertFalse(NumberFormat.unregister(key))
        assertFalse(NumberFormat.getAvailableULocales()!!.contains(locale))
        assertEquals("42", NumberFormat.getInstance(locale).format(42L))
    }
}
