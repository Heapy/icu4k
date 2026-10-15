package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.MeasurePrefix
import kotlin.test.*

class UnitsConverterBoundaryTest {
    @Test fun decimalConversionsAndFactors() {
        assertEquals("33.80000000000000000000000000000004", UnitsConverter("celsius", "fahrenheit").convert(BigDecimal("1"))!!.stripTrailingZeros().toString())
        assertEquals("1", UnitsConverter("meter", "centimeter").convertInverse(BigDecimal("100"))!!.stripTrailingZeros().toString())
        val factor = UnitsConverter.Factor.processFactor("ft_to_m").applyPrefix(MeasurePrefix.KILO)
        assertEquals("304.8", factor.getConversionRate().stripTrailingZeros().toString())
        assertEquals("0.125", UnitsConverter.Factor.processFactor("2^-3").getConversionRate().toString())
        val base = ConversionRates().extractCompoundBaseUnit(MeasureUnitImpl.forIdentifier("foot"))
        assertNull(base.getIdentifier())
        base.serialize()
        assertEquals("meter", base.getIdentifier())
    }
}
