// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.math

import io.github.heapy.icu4k.util.BigInteger
import io.github.heapy.icu4k.util.BigDecimal as StandardBigDecimal
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.number.NumberFormatter
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import kotlin.test.*

class BigDecimalTest {
    @Test fun publicArithmeticAndRepresentation() {
        val a = BigDecimal("1.20")
        assertSame(a, a.plus())
        assertSame(a, a.abs())
        assertSame(a, a.setScale(2, -99))
        assertNotEquals(a, BigDecimal("1.200"))
        assertEquals(0, a.compareTo(BigDecimal("1.200")))
        assertEquals("3.50", a.add(BigDecimal("2.30")).toString())
        assertEquals("0.1714", a.divide(BigDecimal("7"), 4, BigDecimal.ROUND_HALF_EVEN).toString())
        assertEquals("0.008", BigDecimal("5").pow(BigDecimal("-3"), MathContext(9)).toString())
        assertSame(BigDecimal.ZERO, a.subtract(a, MathContext(9)))
        assertSame(BigDecimal.ONE, a.pow(BigDecimal.ZERO))
        assertSame(BigDecimal.TEN, BigDecimal.valueOf(10L))
        assertEquals("0.010", BigDecimal.valueOf(10L, 3).toString())
        assertEquals("0.1", BigDecimal.valueOf(0.1).toString())
        assertEquals("0.1000000000000000055511151231257827021181583404541015625", BigDecimal(0.1).toString())
        val chars = a.toCharArray(); chars[0] = '9'
        assertEquals("1.20", a.toString())
    }
    @Test fun integerInterchangeAndPrimitiveConversions() {
        assertEquals("123.45", BigDecimal(BigInteger("12345"), 2).toString())
        assertEquals("123.450", BigDecimal(StandardBigDecimal("123.450")).toString())
        val a = BigDecimal("-18446744073709551617.999")
        assertEquals(BigInteger("-18446744073709551617"), a.toBigInteger())
        assertEquals(BigInteger("-18446744073709551617999"), a.unscaledValue())
        assertEquals(-1L, a.toLong())
        assertEquals(-1, a.toInt())
        assertFailsWith<ArithmeticException> { a.toBigIntegerExact() }
        assertEquals(BigInteger("123"), BigDecimal("123.00").toBigIntegerExact())
        assertFailsWith<ArithmeticException> { BigDecimal(Long.MAX_VALUE).longValueExact() }
        assertEquals(Long.MAX_VALUE, BigDecimal("9223372036854775807.0").longValueExact())
        assertEquals("  12.30", BigDecimal("12.3").format(4, 2))
        assertEquals(16777216.0, BigDecimal("16777217").toFloat().toDouble())
    }
    @Test fun legacyNumberWorksInCommonFormattingPipeline() {
        val value = BigDecimal("12345678901234567890.125")
        val number: Number = value
        val quantity = DecimalQuantity_DualStorageBCD(number)
        assertEquals(value.toBigDecimal(), quantity.toBigDecimal())
        val formatter = NumberFormatter.withLocale(ULocale("en"))
        assertEquals(formatter.format(value.toBigDecimal()).toString(), formatter.format(number).toString())
    }
}
