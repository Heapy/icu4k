package io.github.heapy.icu4k.impl.units

import kotlin.test.*

class UnitDecimalTest {
    @Test fun exactArithmeticRetainsScaleAndSign() {
        assertEquals("3.00", UnitDecimal("2.50").add(UnitDecimal("0.5")).toString())
        assertEquals("-0.0100", UnitDecimal("0.10").multiply(UnitDecimal("-0.10")).toString())
        assertEquals("0.000", UnitDecimal("1.000").subtract(UnitDecimal("1")).toString())
        assertEquals("1E+9", UnitDecimal("1e9").toString())
        assertEquals("0.000001", UnitDecimal("1e-6").toString())
        assertEquals("1E-7", UnitDecimal("1e-7").toString())
        assertNotEquals(UnitDecimal("1.0"), UnitDecimal("1"))
        assertEquals(0, UnitDecimal("1.0").compareTo(UnitDecimal("1")))
    }
    @Test fun decimal128AndHalfEvenRounding() {
        assertEquals("0.3333333333333333333333333333333333", UnitDecimal.ONE.divide(UnitDecimal("3")).toString())
        assertEquals("0.125", UnitDecimal.ONE.divide(UnitDecimal("8")).toString())
        assertEquals("2", UnitDecimal("2.5").round(1).toString())
        assertEquals("4", UnitDecimal("3.5").round(1).toString())
        assertEquals("1.00E+3", UnitDecimal("999.5").round(3).toString())
        assertEquals("0.03125", UnitDecimal("2").powRounded(-5).toString())
        assertEquals("1267650600228229401496703205376", UnitDecimal("2").pow(100).toString())
        assertFailsWith<ArithmeticException> { UnitDecimal.ONE.divide(UnitDecimal.ZERO) }
    }
}
