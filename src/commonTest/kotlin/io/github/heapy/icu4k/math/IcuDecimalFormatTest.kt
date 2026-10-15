package io.github.heapy.icu4k.math

import kotlin.test.*

class IcuDecimalFormatTest {
    @Test fun widthsExponentsAndCarry() {
        assertEquals("   -12.35", IcuDecimalValue.parse("-12.345").format(6, 2))
        assertEquals("  1.23E+005", IcuDecimalValue.parse("1.234E5").format(3, 2, 3, 0, MathContext.SCIENTIFIC, MathContext.ROUND_HALF_UP))
        assertEquals("12.3E+03", IcuDecimalValue.parse("12345").format(-1, 1, 2, 0, MathContext.ENGINEERING, MathContext.ROUND_HALF_UP))
        assertEquals("1.00E+1", IcuDecimalValue.parse("9.999").format(-1, 2, -1, 0, MathContext.SCIENTIFIC, MathContext.ROUND_HALF_UP))
        val failure = assertFailsWith<IllegalArgumentException> { IcuDecimalValue.parse("1").format(-1, -1, 3, -2, 1, 4) }
        assertEquals("Bad argument 4 to format: 3", failure.message)
    }
    @Test fun exactLongAndRepresentation() {
        assertEquals(Long.MAX_VALUE, IcuDecimalValue.parse("9223372036854775807.0").longValueExact())
        assertEquals(Long.MIN_VALUE, IcuDecimalValue.parse("-9223372036854775808.0").longValueExact())
        assertFailsWith<ArithmeticException> { IcuDecimalValue.parse("9223372036854775807").longValueExact() }
        assertEquals((-128).toByte(), IcuDecimalValue.parse("-128").byteValueExact())
        assertEquals(32767.toShort(), IcuDecimalValue.parse("32767").shortValueExact())
        val a = IcuDecimalValue.parse("1E2"); val b = IcuDecimalValue.parse("100")
        assertFalse(a.sameRepresentation(b))
        assertTrue(IcuDecimalValue.parse("1E0").sameRepresentation(IcuDecimalValue.parse("1")))
        assertEquals("1230", IcuDecimalValue.parse("1.230").movePointRight(3).toString())
        assertEquals("0.001230", IcuDecimalValue.parse("1.230").movePointLeft(3).toString())
    }
    @Test fun floatingPointMidpointAndUnderflow() {
        val above = "1.0000000596046447753906250000000000000001"
        assertEquals(Float.fromBits(0x3f800001).toDouble(), IcuDecimalValue.parse(above).floatValue().toDouble())
        assertEquals(1.0f, IcuDecimalValue.parse("1.0000000596046447753906249999999999999999").floatValue())
        assertEquals(Double.POSITIVE_INFINITY, IcuDecimalValue.parse("1E999999999").doubleValue())
        assertEquals(Float.POSITIVE_INFINITY, IcuDecimalValue.parse("1E999999999").floatValue())
        assertEquals((-0.0f).toRawBits(), IcuDecimalValue.parse("-1E-999999999").floatValue().toRawBits())
        val midpoint = IcuDecimalValue.parse("5").pow(IcuDecimalValue.parse("150")).also { it.exp -= 150 }
        assertEquals(0.0f, midpoint.floatValue())
        assertEquals(Float.fromBits(1), midpoint.add(IcuDecimalValue.parse("1E-200")).floatValue())
    }
    @Test fun subnormalFloatMidpoints() {
        val unit = IcuDecimalValue.parse("5").pow(IcuDecimalValue.parse("150")).also { it.exp -= 150 }
        val epsilon = IcuDecimalValue.parse("1E-200")
        for (lower in listOf(0, 1, 2, 3, 0x12345, 0x7ffffe, 0x7fffff)) {
            val midpoint = unit.multiply(IcuDecimalValue.parse((lower * 2 + 1).toString()))
            assertEquals(Float.fromBits(lower), midpoint.subtract(epsilon).floatValue(), "below $lower")
            assertEquals(Float.fromBits(if (lower and 1 == 0) lower else lower + 1), midpoint.floatValue(), "tie $lower")
            assertEquals(Float.fromBits(lower + 1), midpoint.add(epsilon).floatValue(), "above $lower")
        }
    }

}
