@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import kotlin.test.*

class MeasureUnitCurrencyTest {
    @Test fun publicUnitCompositionAndSingletons() {
        assertSame(MeasureUnit.METER, MeasureUnit.forIdentifier("meter"))
        assertSame(MeasureUnit.KILOMETER, MeasureUnit.METER.withPrefix(MeasureUnit.MeasurePrefix.KILO))
        assertSame(MeasureUnit.SQUARE_METER, MeasureUnit.METER.withDimensionality(2))
        assertSame(MeasureUnit.METER_PER_SECOND, MeasureUnit.METER.product(MeasureUnit.SECOND.reciprocal()))
        assertNull(MeasureUnit.forIdentifier(null)); assertNull(MeasureUnit.forIdentifier(""))
        assertNull(NoUnit.BASE); assertSame(MeasureUnit.PERCENT, NoUnit.PERCENT)
        val custom = MeasureUnit.forIdentifier("kilometer-per-100-second")!!
        assertEquals("kilometer-per-100-second", custom.getIdentifier())
        assertNull(custom.getType()); assertEquals(100L, custom.getConstantDenominator())
        assertEquals(listOf("kilometer", "per-second"), custom.splitToSingleUnits().map { it.getIdentifier() })
        assertEquals("kilometer-per-second", custom.withConstantDenominator(0).getIdentifier())
        assertEquals(MeasureUnit.Complexity.COMPOUND, custom.getComplexity())
        assertFailsWith<UnsupportedOperationException> { custom.reciprocal() }
        assertTrue(MeasureUnit.METER in MeasureUnit.getAvailable("length"))
        assertTrue(Currency.getInstance("USD") in MeasureUnit.getAvailable("currency"))
        assertEquals(listOf("second", "minute", "hour", "day", "week", "month", "year"), TimeUnit.values().map { it.getIdentifier() })
        assertSame(MeasureUnit.SECOND, TimeUnit.SECOND)
        assertSame(MeasureUnit.METER, MeasureUnit.METER.getCopyOfMeasureUnitImpl().build())
        val absent = MeasureUnit.getAvailable("unknown-type") as MutableSet<MeasureUnit>
        absent.clear()
        assertFalse(absent.remove(MeasureUnit.METER))
        assertFalse(absent.addAll(emptyList()))
        assertFailsWith<UnsupportedOperationException> { absent.add(MeasureUnit.METER) }
    }
    @Test fun publicCurrencyDataAndRegistration() {
        val usd = Currency.getInstance("usd")
        assertSame(usd, Currency.getInstance("USD"))
        assertSame(usd, Currency.getInstance(ULocale("en_US")))
        assertSame(usd, MeasureUnit.internalGetInstance("currency", "USD"))
        assertEquals(840, usd.getNumericCode()); assertEquals("USD", usd.getCurrencyCode())
        assertEquals("currency", usd.getType()); assertEquals("USD", usd.getIdentifier())
        assertEquals("$", usd.getSymbol(ULocale("en")))
        assertEquals(2, usd.getDefaultFractionDigits())
        assertEquals(0.05, Currency.getInstance("CHF").getRoundingIncrement(Currency.CurrencyUsage.CASH))
        assertTrue(Currency.isAvailable("USD", null, null))
        val key = Currency.registerInstance(usd, ULocale("zz_ZZ"))
        try {
            assertSame(usd, Currency.getInstance(ULocale("zz_ZZ")))
            assertSame(usd, Currency.getInstance(ULocale("zz_ZZ_VARIANT")))
            assertTrue(Currency.getAvailableULocales().any { it.getName() == "zz_ZZ" })
            assertSame(Currency.getInstance("EUR"), Currency.getInstance(ULocale("zz_ZZ@currency=eur")))
        } finally { assertTrue(Currency.unregister(key)) }
        assertFalse(Currency.unregister(key))
    }
}
