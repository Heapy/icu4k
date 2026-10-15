package io.github.heapy.icu4k.math

import io.github.heapy.icu4k.util.BigDecimal as StandardBigDecimal
import kotlin.test.*

class IcuDecimalConstructionTest {
    @Test fun integerConstructorsSharingAndFactoryScale() {
        assertEquals(Int.MIN_VALUE.toString(), IcuDecimalValue.fromInt(Int.MIN_VALUE).toString())
        assertEquals(Long.MIN_VALUE.toString(), IcuDecimalValue.fromLong(Long.MIN_VALUE).toString())
        assertEquals(Long.MAX_VALUE.toString(), IcuDecimalValue.fromLong(Long.MAX_VALUE).toString())
        assertSame(IcuDecimalValue.ONE_VALUE.mant, IcuDecimalValue.fromInt(-1).mant)
        assertNotSame(IcuDecimalValue.ONE_VALUE.mant, IcuDecimalValue.fromLong(1).mant)
        assertSame(IcuDecimalValue.ZERO_VALUE, IcuDecimalValue.valueOf(0L))
        assertSame(IcuDecimalValue.ONE_VALUE, IcuDecimalValue.valueOf(1L))
        assertSame(IcuDecimalValue.TEN_VALUE, IcuDecimalValue.valueOf(10L))
        val scaled = IcuDecimalValue.valueOf(10L, 3)
        assertEquals("0.010", scaled.toString()); assertSame(IcuDecimalValue.TEN_VALUE.mant, scaled.mant)
        assertEquals("Negative scale: -1", assertFailsWith<NumberFormatException> { IcuDecimalValue.valueOf(10L, -1) }.message)
    }
    @Test fun exactAndShortestFloatingConstruction() {
        assertEquals("0.1000000000000000055511151231257827021181583404541015625", IcuDecimalValue.fromDouble(0.1).toString())
        assertEquals("0.1", IcuDecimalValue.valueOf(0.1).toString())
        assertEquals("1.0E-5", IcuDecimalValue.valueOf(1e-5).toString())
        assertEquals("0", IcuDecimalValue.fromDouble(-0.0).toString())
        assertEquals("0.0", IcuDecimalValue.valueOf(-0.0).toString())
        assertFailsWith<NumberFormatException> { IcuDecimalValue.fromDouble(Double.POSITIVE_INFINITY) }
        assertFailsWith<NumberFormatException> { IcuDecimalValue.valueOf(Double.NaN) }
    }
    @Test fun arbitraryIntegerTextAndLowBits() {
        val value = IcuDecimalValue.parse("-18446744073709551617.999")
        assertEquals("-18446744073709551617", value.integerText())
        assertEquals(-1L, value.lowLongValue()); assertEquals(-1, value.lowIntValue())
        assertEquals("-18446744073709551617999", value.unscaledText())
        assertFailsWith<ArithmeticException> { value.exactIntegerText() }
        assertEquals("123", IcuDecimalValue.parse("123.000").exactIntegerText())
        assertEquals("0", IcuDecimalValue.parse("-0.1").integerText())
        assertEquals("100000000000000000000", IcuDecimalValue.parse("1E20").unscaledText())
        assertEquals(StandardBigDecimal("1.2300"), IcuDecimalValue.parse("1.2300").toStandardBigDecimal())
        assertEquals("0.0123", IcuDecimalValue.fromIntegerText("123", 4).toString())
    }
}
