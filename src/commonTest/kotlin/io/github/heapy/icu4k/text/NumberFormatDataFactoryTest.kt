// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class NumberFormatDataFactoryTest {
    private val noAlgorithmic = NumberFormatDataFactory.AlgorithmicFactory { _, _, _ -> error("Unexpected algorithmic dispatch") }
    @Test fun numericStylesAndParsing() {
        for (style in 0..9) {
            val f = NumberFormatDataFactory.create(ULocale("en_US"), style, noAlgorithmic) as DecimalFormat
            assertEquals(f.getDecimalFormatSymbols().getLocale(ULocale.ACTUAL_LOCALE), f.getLocale(ULocale.ACTUAL_LOCALE))
            assertEquals(f.getDecimalFormatSymbols().getLocale(ULocale.VALID_LOCALE), f.getLocale(ULocale.VALID_LOCALE))
            if (style == 4) {
                assertEquals(0, f.getMaximumFractionDigits())
                assertFalse(f.isDecimalSeparatorAlwaysShown())
                assertTrue(f.isParseIntegerOnly())
                val pos = ParsePosition(0)
                assertEquals(12L, f.parse("12.5", pos))
                assertEquals(2, pos.index)
            }
            if (style == 8) assertEquals(Currency.CurrencyUsage.CASH, f.getCurrencyUsage())
            if (style == 6) assertNotNull(f.getCurrencyPluralInfo())
        }
        val f = NumberFormatDataFactory.create(ULocale("en_US"), 0, noAlgorithmic)!!
        assertEquals("1,234.5", f.format(1234.5))
        assertEquals("12%", NumberFormatDataFactory.create(ULocale("en_US"), 2, noAlgorithmic)!!.format(0.12))
        val iso = NumberFormatDataFactory.create(ULocale("en_US"), 5, noAlgorithmic) as DecimalFormat
        assertTrue(iso.toPattern().contains("¤¤"))
    }
    @Test fun algorithmicRuleRoutingAndMetadataAfterCallback() {
        val prototype = DecimalFormat("0", DecimalFormatSymbols(ULocale.FRENCH))
        prototype.setLocale(ULocale.FRENCH, ULocale.FRENCH)
        val events = arrayListOf<String>()
        val creator = NumberFormatDataFactory.AlgorithmicFactory { locale, type, rule ->
            events.add("${locale?.getName()}:$type:$rule")
            assertEquals(ULocale.FRENCH, prototype.getLocale(ULocale.ACTUAL_LOCALE))
            prototype
        }
        val f = NumberFormatDataFactory.create(ULocale("en_US@numbers=roman"), 0, creator)
        assertSame(prototype, f)
        assertEquals(listOf("en_US@numbers=roman:4:%roman-upper"), events)
        val symbols = DecimalFormatSymbols(ULocale("en_US@numbers=roman"))
        assertEquals(symbols.getLocale(ULocale.ACTUAL_LOCALE), f!!.getLocale(ULocale.ACTUAL_LOCALE))
        events.clear()
        prototype.setLocale(ULocale.FRENCH, ULocale.FRENCH)
        NumberFormatDataFactory.create(ULocale("en_US@numbers=hans"), 4, creator)
        assertEquals(listOf("zh:1:%spellout-cardinal"), events)
    }
    @Test fun cashRoundingAndFactoryServiceComposition() {
        val cash = NumberFormatDataFactory.create(ULocale("de_CH@currency=CHF"), 8, noAlgorithmic) as DecimalFormat
        assertEquals(Currency.CurrencyUsage.CASH, cash.getCurrencyUsage())
        assertEquals(BigDecimal("0.05"), cash.getRoundingIncrement())
        val registry = NumberFormatServiceCore { locale, style -> NumberFormatDataFactory.create(locale, style, noAlgorithmic) }
        val a = registry.createInstance(ULocale("en_US"), 0)
        val b = registry.createInstance(ULocale("en_US"), 0)
        assertNotSame(a, b)
        assertEquals("1,234.5", a.format(1234.5))
        assertEquals(NumberFormat.getPatternForStyle(ULocale.ENGLISH, 0), NumberFormat.getPatternForStyleAndNumberingSystem(ULocale.ENGLISH, "latn", 0))
    }
}
