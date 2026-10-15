package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class ComplexUnitsBoundaryTest {
    @Test fun mixedOrderAndCarry() {
        val converter = ComplexUnitsConverter("foot", "inch-and-foot")
        val result = converter.convert(BigDecimal("2.9999"), Precision.integer())
        assertEquals(0, result.indexOfQuantity)
        assertEquals(listOf("inch", "foot"), result.measures!!.map { it!!.getUnit().getIdentifier() })
        assertEquals(listOf(0.0, 3.0), result.measures.map { it!!.getNumber().toDouble() })
        val exact = MixedUnitInteger(BigDecimal("18446744073709551616"))
        assertEquals(0L, exact.toLong())
        val integer = exact.toNumber()
        assertEquals("18446744073709551616", integer.toString()); assertEquals(961, integer.hashCode())
    }
    @Test fun preferenceHandlerResolvesBogusRounderAndExposesMeasures() {
        val micros = MicroProps(false).apply { rounder = Precision.BogusRounder() }
        val handler = UsagePrefsHandler(ULocale("en_US"), MeasureUnit.METER, "person-height", MicroPropsGenerator { micros })
        assertSame(handler.getOutputUnits(), handler.getOutputUnits())
        val quantity = DecimalQuantity_DualStorageBCD(1.75)
        assertSame(micros, handler.processQuantity(quantity))
        assertEquals("foot-and-inch", micros.outputUnit!!.getIdentifier())
        assertEquals(listOf(5.0, 8.9), micros.mixedMeasures!!.map { it!!.getNumber().toDouble() })
        assertEquals(8.9, quantity.toDouble()); assertFalse(micros.rounder is Precision.BogusRounder)
    }
    @Test fun directHandlerKeepsTargetIdentity() {
        val target = MeasureUnit.forIdentifier("inch-and-foot")
        val micros = MicroProps(false).apply { rounder = Precision.integer() }
        val handler = UnitConversionHandler(target, MicroPropsGenerator { micros })
        val quantity = DecimalQuantity_DualStorageBCD(2.5)
        handler.processQuantity(quantity)
        assertSame(target, micros.outputUnit)
        assertEquals(6.0, quantity.toDouble()); assertEquals(0, micros.indexOfQuantity)
    }
}
