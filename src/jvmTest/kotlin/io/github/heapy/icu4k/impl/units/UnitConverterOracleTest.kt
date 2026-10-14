package io.github.heapy.icu4k.impl.units

import java.math.BigDecimal
import kotlin.test.*
import com.ibm.icu.impl.units.UnitsConverter as JConverter
import com.ibm.icu.impl.units.MeasureUnitImpl as JUnit

class UnitConverterOracleTest {
    @Test fun decimalConversionsAndInversesMatchSource() {
        val pairs = listOf("foot" to "meter", "mile" to "kilometer", "mile-per-hour" to "meter-per-second", "fahrenheit" to "celsius", "celsius" to "fahrenheit", "kelvin" to "fahrenheit", "liter-per-100-kilometer" to "mile-per-gallon", "meter" to "per-foot", "square-foot" to "square-meter", "kilogram-meter-per-square-second" to "newton", "degree" to "radian", "beaufort" to "meter-per-second", "meter-per-second" to "beaufort", "beaufort" to "mile-per-hour", "beaufort" to "beaufort")
        val inputs = listOf("0", "0.00", "1", "-1", "2.49", "2.5", "0.3", "1.6", "61.4", "-40", "273.15", "1E-100", "1E+50", "12345.6789012345678901234567890123456789")
        for ((source, target) in pairs) {
            val j = JConverter(source, target); val k = UnitConverter(source, target)
            assertEquals(j.toString(), k.toString(), "$source/$target")
            val ji = j.conversionInfo; val ki = k.getConversionInfo()
            assertEquals(ji.conversionRate.toString(), ki.conversionRate.toString())
            assertEquals(ji.offset.toString(), ki.offset.toString())
            assertEquals(ji.reciprocal, ki.reciprocal)
            for (value in inputs) {
                assertEquals(j.convert(BigDecimal(value)).toString(), k.convert(UnitDecimal(value)).toString(), "$source/$target/$value")
                assertEquals(j.convertInverse(BigDecimal(value)).toString(), k.convertInverse(UnitDecimal(value)).toString(), "inverse $source/$target/$value")
            }
        }
    }
    @Test fun dimensionalCompatibilityMatchesSource() {
        val j = com.ibm.icu.impl.units.ConversionRates(); val k = UnitConversionData()
        val ids = listOf("meter", "foot", "second", "per-meter", "square-meter", "meter-per-meter", "candela", "lumen", "beaufort", "mile-per-hour", "liter-per-100-kilometer", "mile-per-gallon", "kilocelsius", "kelvin", "meter-and-foot")
        for (source in ids) for (target in ids) assertEquals(JConverter.extractConvertibility(JUnit.forIdentifier(source), JUnit.forIdentifier(target), j).name, UnitConverter.extractConvertibility(MeasureUnitImpl.forIdentifier(source)!!, MeasureUnitImpl.forIdentifier(target)!!, k).name, "$source/$target")
    }
    @Test fun integerTruncationMatchesBigDecimal() {
        for (value in listOf("0", "-0.9", "17.99", "-17.99", "4294967297", "-4294967297", "1E2147483648", "1E-2147483647", "-1E+50", "12345678901234567890123456789.99")) assertEquals(BigDecimal(value).toInt(), UnitDecimal(value).intValue(), value)
    }
}
