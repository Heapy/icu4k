package io.github.heapy.icu4k.util

import kotlin.test.*

class MeasureAmountTest {
    @Test fun amountsRetainUnitsAndFloatingRepresentations() {
        val usd = Currency.getInstance("USD")
        assertEquals("12 USD", CurrencyAmount(12, usd).toString())
        assertEquals("12.0 USD", CurrencyAmount(12.0, usd).toString())
        assertEquals("0.1 USD", CurrencyAmount(0.1f, usd).toString())
        val float = Measure(0.1f, usd)
        val exactFloatAsDouble = Measure(0.10000000149011612, usd)
        assertEquals(0.10000000149011612, float.getNumber().toDouble())
        assertEquals(float, exactFloatAsDouble)
        assertEquals(float.hashCode(), exactFloatAsDouble.hashCode())
        assertNotEquals(float, Measure(0.1, usd))
        assertEquals("-0.0 USD", CurrencyAmount(-0.0, usd).toString())
        assertEquals("1.4E-45 USD", CurrencyAmount(Float.MIN_VALUE, usd).toString())
        val time = TimeUnitAmount(2.0, MeasureUnit.SECOND)
        assertEquals("2.0 duration-second", time.toString())
        assertSame(MeasureUnit.SECOND, time.getTimeUnit())
        assertSame(usd, CurrencyAmount(12, usd).getCurrency())
        assertEquals(Measure(12, usd), CurrencyAmount(12.0, usd))
        assertEquals(Measure(Double.NaN, usd), Measure(Double.NaN, usd))
        assertEquals(Measure(Float.NaN, usd), Measure(Float.NaN, usd))
        assertNotEquals(Measure(Float.NaN, usd), Measure(Double.NaN, usd))
        assertEquals(Measure(-0.0, usd), Measure(0.0, usd))
        assertNotEquals(Measure(-0.0, usd).hashCode(), Measure(0.0, usd).hashCode())
        assertEquals(Measure(9007199254740992L, usd), Measure(9007199254740993L, usd))
        assertFailsWith<NullPointerException> { Measure(null as Number?, null) }
    }
    @Test fun decimalInputsRetainScaleAndObjectIdentity() {
        val decimal = BigDecimal("123.4500")
        val amount = Measure(decimal, MeasureUnit.METER)
        assertSame(decimal, amount.getNumber())
        assertEquals("123.4500 length-meter", amount.toString())
        assertEquals(amount, Measure(BigDecimal("123.45"), MeasureUnit.METER))
    }
}
