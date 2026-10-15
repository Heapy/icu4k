package io.github.heapy.icu4k.math

import kotlin.test.*

class IcuDecimalArithmeticTest {
    @Test fun fixedPointScalesAndOperandPreservation() {
        val left = IcuDecimalValue.parse("1.20"); val right = IcuDecimalValue.parse("3.4")
        val digits = left.mant
        assertEquals("4.60", left.add(right).toString())
        assertEquals("-2.20", left.subtract(right).toString())
        assertEquals("4.080", left.multiply(right).toString())
        assertEquals(-1, left.compareTo(right))
        assertSame(left, left.min(right)); assertSame(right, left.max(right))
        assertSame(left, left.plus()); assertSame(left, left.abs())
        assertEquals("-1.20", left.negate().toString())
        assertEquals("1.20", left.toString()); assertEquals("3.4", right.toString()); assertSame(digits, left.mant)
    }
    @Test fun contextRoundingAndCanonicalZero() {
        val context = MathContext(2)
        assertEquals("1.0E+2", IcuDecimalValue.parse("9.99").multiply(IcuDecimalValue.parse("9.99"), context).toString())
        assertEquals("1.3", IcuDecimalValue.parse("1.25").add(IcuDecimalValue.parse("0.006"), context).toString())
        assertEquals("1.00E+3", IcuDecimalValue.parse("999").add(IcuDecimalValue.parse("1"), MathContext(3)).toString())
        assertEquals("1.00E+20", IcuDecimalValue.parse("1E20").add(IcuDecimalValue.parse("1"), MathContext(3)).toString())
        assertSame(IcuDecimalValue.ZERO_VALUE, IcuDecimalValue.parse("1").add(IcuDecimalValue.parse("-1"), MathContext.DEFAULT))
        val left = IcuDecimalValue.parse("1.25"); val right = IcuDecimalValue.parse("1.24")
        assertEquals(0, left.compareTo(right, MathContext(1)))
        assertEquals("1", left.max(right, MathContext(1)).toString())
        val fail = assertFailsWith<ArithmeticException> { left.add(right, MathContext(2, MathContext.SCIENTIFIC, true)) }
        assertEquals("Too many digits: 1.25", fail.message)
        assertEquals("1.25", left.toString()); assertEquals("1.24", right.toString())
    }
}
