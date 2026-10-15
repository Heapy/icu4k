// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.test.*
import io.github.heapy.icu4k.impl.number.DecimalQuantityCore

class BigDecimalTest {
    @Test fun scaleArithmeticExactDivisionAndModes() {
        val value = BigDecimal("1.20")
        assertEquals("3.50", value.add(BigDecimal("2.3")).toString())
        assertEquals("0.15", value.divide(BigDecimal(8)).toString())
        assertEquals("1.5E+2", value.divide(BigDecimal("0.008")).toString())
        assertEquals("0.667", BigDecimal(2).divide(BigDecimal(3), MathContext(3, RoundingMode.HALF_UP)).toString())
        assertEquals("-0.667", BigDecimal(-2).divide(BigDecimal(3), 3, RoundingMode.FLOOR).toString())
        assertFailsWith<ArithmeticException> { BigDecimals.ONE.divide(BigDecimal(3)) }
        assertEquals("1.2", value.setScale(1).toString())
        assertFailsWith<ArithmeticException> { BigDecimal("1.25").setScale(1) }
        assertEquals("0.0120", value.movePointLeft(2).toPlainString())
        assertEquals("120", value.scaleByPowerOfTen(2).toString())
        assertEquals("100", BigDecimal("1E+2").movePointRight(0).toString())
        assertEquals("1E+2", BigDecimal("1E+2").scaleByPowerOfTen(0).toString())
    }
    @Test fun binaryDoubleConstructorDiffersFromDecimalValueFactory() {
        assertEquals("0.1000000000000000055511151231257827021181583404541015625", BigDecimal(0.1).toString())
        assertEquals("0.1", BigDecimals.valueOf(0.1).toString())
        assertEquals("0.100", BigDecimal(0.1, MathContext(3)).toString())
        assertEquals(0, BigDecimal(-0.0).scale())
        assertEquals(1, BigDecimals.valueOf(-0.0).scale())
        assertFailsWith<NumberFormatException> { BigDecimal(Double.NaN) }
    }
    @Test fun valuesEqualityFactoriesAndLowBits() {
        val a = BigDecimal("1.0"); val b = BigDecimal("1.00")
        assertEquals(0, a.compareTo(b)); assertNotEquals(a, b)
        assertEquals(311, a.hashCode()); assertEquals(3102, b.hashCode())
        assertSame(a, a.plus()); assertSame(a, a.setScale(1)); assertSame(a, a.abs())
        assertSame(a, a.min(b)); assertSame(a, a.max(b))
        assertSame(BigDecimals.ZERO, BigDecimal("0.000").stripTrailingZeros())
        assertSame(BigDecimals.ONE, BigDecimals.valueOf(1))
        assertEquals(1L, BigDecimal("18446744073709551617").toLong())
        assertEquals(1, BigDecimal("4294967297").toInt())
        val number: Number = BigDecimal("257.99")
        assertEquals(1.toByte(), number.toByte())
        assertEquals(257.toShort(), number.toShort())
        assertEquals(0L, BigDecimal("1E+1000000000").toLong())
        assertEquals("0.01", BigDecimal("123.45").ulp().toString())
    }
    @Test fun quantityBoundaryRetainsScaleAndNegativeZeroPolicy() {
        val value = DecimalQuantityCore()
        value.setToBigDecimal(BigDecimal("1.2500"))
        assertEquals("1.25", value.toBigDecimal().toString())
        value.multiplyBy(BigDecimal("2.00"))
        assertEquals("2.5", value.toBigDecimal().toString())
        val zero = DecimalQuantityCore(-0.0)
        zero.multiplyBy(null as BigDecimal?)
        assertTrue(zero.isNegative())
    }

    @Test fun extremeScaleZeroMultiplicationRetainsHostValidationOrder() {
        val tiny = BigDecimals.valueOf(1, Int.MAX_VALUE)
        val zero = BigDecimals.valueOf(0, 1)
        assertFailsWith<ArithmeticException> { tiny.multiply(zero) }
        assertEquals(Int.MAX_VALUE, zero.multiply(tiny).scale())
        assertFailsWith<ArithmeticException> { tiny.scaleByPowerOfTen(-1) }
    }

}
