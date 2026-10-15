package io.github.heapy.icu4k.math

import kotlin.test.*

class IcuDecimalDivisionTest {
    @Test fun scaledDivideIntegerAndRemainder() {
        val one = IcuDecimalValue.parse("1.00"); val three = IcuDecimalValue.parse("3")
        assertEquals("0.33", one.divide(three).toString())
        assertEquals("0.3333", one.divide(three, MathContext(4)).toString())
        assertEquals("0.125", one.divide(IcuDecimalValue.parse("8"), 3, MathContext.ROUND_HALF_UP).toString())
        val value = IcuDecimalValue.parse("-10.25")
        assertEquals("-3", value.divideInteger(three).toString())
        assertEquals("-1.25", value.remainder(three).toString())
        assertEquals("-10.25", value.toString())
        val zero = IcuDecimalValue.parse("0.000")
        assertSame(zero, zero.divide(three))
        assertSame(IcuDecimalValue.ZERO_VALUE, one.remainder(IcuDecimalValue.parse("1"), MathContext.DEFAULT))
        assertEquals("Divide by 0", assertFailsWith<ArithmeticException> { zero.divide(IcuDecimalValue.ZERO_VALUE) }.message)
        assertEquals("Integer overflow", assertFailsWith<ArithmeticException> { IcuDecimalValue.parse("1000").divideInteger(three, MathContext(2)) }.message)
        assertEquals("Rounding necessary", assertFailsWith<ArithmeticException> { one.divide(three, 2, MathContext.ROUND_UNNECESSARY) }.message)
    }
    @Test fun powerScaleAndExactInteger() {
        val two = IcuDecimalValue.parse("2")
        assertEquals("1024", two.pow(IcuDecimalValue.parse("10")).toString())
        assertEquals("0.125", two.pow(IcuDecimalValue.parse("-3"), MathContext(9)).toString())
        assertSame(IcuDecimalValue.ONE_VALUE, two.pow(IcuDecimalValue.ZERO_VALUE))
        assertEquals("Negative power: -1", assertFailsWith<ArithmeticException> { two.pow(IcuDecimalValue.parse("-1")) }.message)
        val scale = IcuDecimalValue.parse("1.20")
        assertSame(scale, scale.setScale(2, -1))
        assertEquals("1.2000", scale.setScale(4).toString())
        assertEquals("10.0", IcuDecimalValue.parse("9.99").setScale(1, MathContext.ROUND_HALF_UP).toString())
        assertEquals(2, scale.scale())
        assertEquals(Int.MIN_VALUE, IcuDecimalValue.parse("-2147483648").intValueExact())
        assertEquals(2, IcuDecimalValue.parse("2.00").intValueExact())
        assertFailsWith<ArithmeticException> { IcuDecimalValue.parse("2147483648").intValueExact() }
        assertFailsWith<ArithmeticException> { IcuDecimalValue.parse("2.01").intValueExact() }
    }
}
