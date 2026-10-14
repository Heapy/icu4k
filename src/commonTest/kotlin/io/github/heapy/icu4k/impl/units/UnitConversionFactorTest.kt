package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.util.MeasurePrefix
import kotlin.test.*

class UnitConversionFactorTest {
    @Test fun constantsCancelBeforeDecimalRounding() {
        val foot = UnitConversionFactor.processFactor("ft_to_m")
        assertEquals("0.3048", foot.getConversionRate().toString())
        assertEquals("1", foot.divide(foot).getConversionRate().toString())
        assertEquals("0.09290304", foot.power(2).getConversionRate().toString())
        assertEquals("304.8000", foot.applyPrefix(MeasurePrefix.KILO).getConversionRate().toString())
        assertEquals("0.3048", foot.getConversionRate().toString())
        assertEquals("0.125", UnitConversionFactor.processFactor("2^-3").getConversionRate().toString())
        assertEquals("0.003785411784", UnitConversionFactor.processFactor("gal_to_m3").getConversionRate().toString())
    }
    @Test fun dataFactorsAndTemperatureOffsetUseCommonArithmetic() {
        val data = UnitConversionData()
        val celsius = MeasureUnitImpl.forIdentifier("celsius")!!
        val kelvin = MeasureUnitImpl.forIdentifier("kelvin")!!
        val kelvinFactor = data.getFactorToBase(kelvin)
        assertEquals("273.15", data.getOffset(celsius, kelvin, kelvinFactor, true).toString())
        assertEquals(UnitDecimal.ZERO, data.getOffset(celsius, kelvin, kelvinFactor, false))
        assertEquals(UnitDecimal.ZERO, data.getOffset(MeasureUnitImpl.forIdentifier("kilocelsius")!!, kelvin, kelvinFactor, true))
        assertEquals("0.3048", data.getFactorToBase(MeasureUnitImpl.forIdentifier("foot")!!).getConversionRate().toString())
    }

}
