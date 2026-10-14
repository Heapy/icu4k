package io.github.heapy.icu4k.impl.units

import kotlin.test.*

class UnitConverterTest {
    @Test fun linearReciprocalAndTemperatureConversions() {
        assertEquals("0.3048", UnitConverter("foot", "meter").convert(UnitDecimal.ONE).toString())
        val temperature = UnitConverter("celsius", "kelvin")
        assertEquals("273.15", temperature.convert(UnitDecimal.ZERO).toString())
        assertEquals("0.00", temperature.convertInverse(UnitDecimal("273.15")).toString())
        val inverse = UnitConverter("meter", "per-meter")
        assertTrue(inverse.getConversionInfo().reciprocal)
        assertEquals("0.25", inverse.convert(UnitDecimal("4")).toString())
        assertEquals(UnitDecimal.ZERO, inverse.convert(UnitDecimal("0.00")))
        assertFailsWith<IllegalArgumentException> { UnitConverter("meter", "second") }
    }
    @Test fun beaufortUsesAbsoluteRoundedScaleAndThresholds() {
        val toBase = UnitConverter("beaufort", "meter-per-second")
        assertEquals("0.15", toBase.convert(UnitDecimal.ZERO).toString())
        assertEquals(toBase.convert(UnitDecimal("2.5")), toBase.convert(UnitDecimal("-2.5")))
        val toScale = UnitConverter("meter-per-second", "beaufort")
        assertEquals("1", toScale.convert(UnitDecimal("0.3")).toString())
        assertEquals("0", toScale.convert(UnitDecimal("0.299999999999999999999999" )).toString())
        assertEquals("17", toScale.convert(UnitDecimal("1000")).toString())
        val info = toBase.getConversionInfo()
        info.conversionRate = UnitDecimal.ZERO
        assertNotEquals(UnitDecimal.ZERO, toBase.getConversionInfo().conversionRate)
    }
}
