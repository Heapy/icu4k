package io.github.heapy.icu4k.impl.units

import kotlin.test.*

class UnitConversionDataTest {
    @Test fun baseDimensionsIgnorePrefixesAndPreservePowers() {
        val data = UnitConversionData()
        val base = data.extractCompoundBaseUnit(MeasureUnitImpl.forIdentifier("square-kilofoot-per-hour")!!)
        base.serialize()
        assertEquals("square-meter-per-second", base.getIdentifier())
        assertEquals("speed", data.getCategory(MeasureUnitImpl.forIdentifier("mile-per-hour")!!))
        assertEquals("consumption", data.getCategory(MeasureUnitImpl.forIdentifier("liter-per-100-kilometer")!!))
        assertEquals("temperature", data.getCategory(MeasureUnitImpl.forIdentifier("fahrenheit")!!))
        // The source fails earlier with an AssertionError when JVM assertions are enabled.
        assertFails { data.getCategory(MeasureUnitImpl.forIdentifier("milligram-per-deciliter")!!) }
        assertEquals("uksystem", data.extractSystems(MeasureUnitImpl.forIdentifier("stone")!!.getSingleUnitImpl()))
    }
    @Test fun expressionsAndSpecialMappingsRemainDistinct() {
        val data = UnitConversionData()
        assertEquals("5/9", data.rate("fahrenheit")!!.conversionRate())
        assertEquals("2298.35/9", data.rate("fahrenheit")!!.offsetExpression)
        val special = data.rate("beaufort")!!
        assertEquals("beaufort", special.special)
        assertFails { special.conversionRate() }
        assertEquals("beaufort", data.getSpecialMappingName(MeasureUnitImpl.forIdentifier("beaufort")!!))
        assertNull(data.getSpecialMappingName(MeasureUnitImpl.forIdentifier("square-beaufort")!!))
    }
}
