package io.github.heapy.icu4k.impl.number

import kotlin.test.*

class DecimalDoubleLoaderTest {
    @Test fun shortestDecimalAndExceptionalValues() {
        assertEquals("4.9E-324", CommonDoubleToDecimal.format(Double.MIN_VALUE))
        assertEquals("9.9E-324", CommonDoubleToDecimal.format(Double.fromBits(2)))
        assertEquals("-0.0", CommonDoubleToDecimal.format(-0.0))
        assertEquals("0.001", CommonDoubleToDecimal.format(0.001))
        assertEquals("1.0E-4", CommonDoubleToDecimal.format(0.0001))
        assertEquals("1.0E7", CommonDoubleToDecimal.format(1e7))
        assertEquals("NaN", CommonDoubleToDecimal.format(Double.NaN))
        assertEquals("Infinity", CommonDoubleToDecimal.format(Double.POSITIVE_INFINITY))
    }
    @Test fun exactLoadingAndRoundingBoundary() {
        val storage = DecimalBcdStorage()
        val state = DecimalDoubleLoader.load(storage, 2.675)
        DecimalBcdRounding.round(storage, state.negative, -2, 0, 6, false, "value") { DecimalDoubleLoader.makeAccurate(storage, state) }
        assertEquals("2.68", storage.toDecimal(state.negative).toString())
        assertTrue(state.explicitlyExact)
        val tiny = DecimalDoubleLoader.load(storage, Double.MIN_VALUE)
        assertTrue(tiny.explicitlyExact)
        assertEquals("4.9E-324", storage.toDecimal(tiny.negative).toString())
        val zero = DecimalDoubleLoader.load(storage, -0.0)
        assertTrue(zero.negative)
        assertFalse(zero.nan)
        assertEquals(0, storage.precision)
    }
}
