package io.github.heapy.icu4k.impl.units

import kotlin.test.*

class ComplexUnitConverterTest {
    @Test fun mixedUnitsPreserveOutputOrderAndBubbleCarries() {
        val converter = ComplexUnitConverter("second", "second-and-hour-and-minute")
        val result = converter.convert(UnitDecimal("3599.9")) { it.setScale(0).stripTrailingZeros() }
        assertEquals(0, result.indexOfQuantity)
        assertEquals(listOf("0", "1", "0"), result.measures.map { it.quantity.toString() })
        assertEquals(listOf(false, true, true), result.measures.map { it.integer })
        val negative = converter.convert(UnitDecimal("-3599.9")) { it.setScale(0).stripTrailingZeros() }
        assertEquals(listOf("0", "-1", "0"), negative.measures.map { it.quantity.toString() })
    }
    @Test fun implicitInputUsesLargestUnitAndThresholdNudge() {
        val converter = ComplexUnitConverter(MeasureUnitImpl.forIdentifier("inch-and-foot")!!, UnitConversionData())
        val result = converter.convert(UnitDecimal("2.5"))
        assertEquals("2", result.measures[1].quantity.toString())
        assertEquals(0, result.indexOfQuantity)
        assertTrue(converter.greaterThanOrEqual(UnitDecimal("0.9999999999999999"), UnitDecimal.ONE))
    }
}
