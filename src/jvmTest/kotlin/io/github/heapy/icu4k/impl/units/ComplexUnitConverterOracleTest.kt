package io.github.heapy.icu4k.impl.units

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.*
import com.ibm.icu.impl.units.ComplexUnitsConverter as JConverter
import com.ibm.icu.impl.units.MeasureUnitImpl as JUnit
import com.ibm.icu.number.Precision

class ComplexUnitConverterOracleTest {
    private fun compare(j: JConverter, k: ComplexUnitConverter, input: String, fraction: Int?) {
        val rounder = fraction?.let { Precision.fixedFraction(it) }
        val original = j.convert(BigDecimal(input), rounder)
        val actual = k.convert(UnitDecimal(input), fraction?.let { digits -> { value -> value.setScale(digits).stripTrailingZeros() } })
        assertEquals(original.indexOfQuantity, actual.indexOfQuantity, input)
        assertEquals(original.measures.map { it.number.toString() }, actual.measures.map { it.quantity.toString() }, "$input/$fraction")
        assertEquals(original.measures.map { it.unit.identifier }, actual.measures.map { it.unit.copy().also { unit -> unit.serialize() }.getIdentifier() }, input)
        assertEquals(original.measures.map { it.number is java.math.BigInteger }, actual.measures.map { it.integer }, input)
    }
    @Test fun mixedOutputsRoundingSignsAndLargeIntegersMatchSource() {
        for ((input, output) in listOf("meter" to "foot-and-inch", "meter" to "inch-and-foot", "second" to "second-and-hour-and-minute", "kilogram" to "stone-and-pound-and-ounce", "celsius" to "fahrenheit", "beaufort" to "meter-per-second")) {
            val j = JConverter(input, output); val k = ComplexUnitConverter(input, output)
            for (value in listOf("0", "-0.00", "1", "-1", "2.5", "3599.9", "-3599.9", "0.9999999999999999", "123456789012345678901234.99")) for (fraction in listOf(null, 0, 2)) compare(j, k, value, fraction)
            for (quantity in listOf("0", "0.9999999999999999", "1", "2.5")) for (limit in listOf("0", "1", "2")) assertEquals(j.greaterThanOrEqual(BigDecimal(quantity), BigDecimal(limit)), k.greaterThanOrEqual(UnitDecimal(quantity), UnitDecimal(limit)))
        }
    }
    @Test fun implicitInputAndMixedOrderMatchSource() {
        for (output in listOf("inch-and-foot", "hour-and-second-and-minute", "meter-and-centimeter")) {
            val j = JConverter(JUnit.forIdentifier(output), com.ibm.icu.impl.units.ConversionRates())
            val k = ComplexUnitConverter(MeasureUnitImpl.forIdentifier(output)!!, UnitConversionData())
            for (value in listOf("0", "1.99", "-2.5")) compare(j, k, value, null)
        }
    }
    @Test fun decimalScaleRoundingAndLongConversionMatchBigDecimal() {
        val random = kotlin.random.Random(23801)
        repeat(400) {
            val text = "${random.nextLong()}${random.nextLong().toString().removePrefix("-")}E${random.nextInt(-50, 51)}"
            val j = BigDecimal(text); val k = UnitDecimal(text)
            assertEquals(j.toLong(), k.longValue(), text)
            assertEquals(j.toBigInteger().toString(), k.integerValue().toString(), text)
            for (mode in RoundingMode.entries) for (scale in listOf(-4, 0, 3, 12)) {
                val expected = try { j.setScale(scale, mode).toString() } catch (_: ArithmeticException) { "throws" }
                val actual = try { k.setScale(scale, UnitDecimal.Rounding.valueOf(mode.name)).toString() } catch (_: ArithmeticException) { "throws" }
                assertEquals(expected, actual, "$text/$scale/$mode")
            }
            assertEquals(j.stripTrailingZeros().toString(), k.stripTrailingZeros().toString(), text)
        }
    }
}
