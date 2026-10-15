package io.github.heapy.icu4k.math

import kotlin.test.*

class IcuDecimalValueTest {
    @Test fun parsingAndLayouts() {
        for ((input, expected) in listOf("0001.2300" to "1.2300", "-0.000" to "0.000", "0.00E-9" to "0", "١٢٣.٤٥" to "123.45", "1e2" to "1E+2", ".5" to "0.5")) assertEquals(expected, IcuDecimalValue.parse(input).toString())
        val engineering = IcuDecimalValue.parse("12345E-10")
        engineering.form = MathContext.ENGINEERING.toByte()
        assertEquals("1.2345E-6", engineering.toString())
        val copy = engineering.copy()
        assertNotSame(engineering, copy); assertSame(engineering.mant, copy.mant)
        val error = assertFailsWith<NumberFormatException> { IcuDecimalValue.parse("xx1x".toCharArray(), 2, 2) }
        assertEquals("Not a number: xx1x", error.message)
    }
    @Test fun roundingAndCanonicalization() {
        assertEquals("1.2", IcuDecimalValue.parse("1.250").round(2, MathContext.ROUND_HALF_EVEN).toString())
        assertEquals("1.3", IcuDecimalValue.parse("1.251").round(2, MathContext.ROUND_HALF_EVEN).toString())
        assertEquals("10000", IcuDecimalValue.parse("9999").round(2, MathContext.ROUND_HALF_UP).toString())
        val failed = IcuDecimalValue.parse("1.234")
        assertFailsWith<ArithmeticException> { failed.round(2, MathContext.ROUND_UNNECESSARY) }
        assertEquals("1.2", failed.toString())
        val value = IcuDecimalValue.parse("0.0000012300")
        value.finish(MathContext(3, MathContext.ENGINEERING), true)
        assertEquals("0.00000123", value.toString())
        assertEquals("0", IcuDecimalValue.parse("0.000").finish(MathContext(9), false).toString())
    }
}
