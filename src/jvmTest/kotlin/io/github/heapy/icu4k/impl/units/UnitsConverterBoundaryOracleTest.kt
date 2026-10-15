package com.ibm.icu.impl.units

import io.github.heapy.icu4k.impl.units.UnitsConverter
import io.github.heapy.icu4k.impl.units.ConversionRates
import io.github.heapy.icu4k.impl.units.MeasureUnitImpl
import io.github.heapy.icu4k.impl.units.SingleUnitImpl
import io.github.heapy.icu4k.impl.units.UnitConversionData

import com.ibm.icu.impl.units.UnitsConverter as JConverter
import com.ibm.icu.impl.units.ConversionRates as JRates
import com.ibm.icu.impl.units.MeasureUnitImpl as JUnit
import com.ibm.icu.impl.units.SingleUnitImpl as JSingle
import io.github.heapy.icu4k.util.MeasurePrefix
import java.math.BigDecimal
import kotlin.test.*

class UnitsConverterBoundaryOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            is AssertionError -> "assert:${e.message}"
            else -> e.javaClass.simpleName + ":" + e.message
        }
    }
    @Test fun conversionsInverseInfoAndMutableInfoSnapshots() {
        for ((from, to) in listOf("meter" to "foot", "celsius" to "fahrenheit", "kelvin" to "celsius",
            "liter-per-100-kilometer" to "mile-per-gallon", "beaufort" to "meter-per-second",
            "meter-per-second" to "beaufort", "beaufort" to "beaufort", "square-kilometer" to "square-mile")) {
            val j = JConverter(from, to); val k = UnitsConverter(from, to)
            assertEquals(j.toString(), k.toString())
            val ji = j.conversionInfo; val ki = k.getConversionInfo()
            assertEquals(ji.conversionRate, ki.conversionRate); assertEquals(ji.offset, ki.offset); assertEquals(ji.reciprocal, ki.reciprocal)
            ki.conversionRate = BigDecimal.TEN; ki.offset = null
            assertEquals(ji.conversionRate, k.getConversionInfo().conversionRate)
            for (value in listOf(null, "0", "1", "-1", "0.3", "3.4", "12.3456789", "100", "1E50", "-5E-20")) {
                val number = value?.let(::BigDecimal)
                assertEquals(outcome { j.convert(number) }, outcome { k.convert(number) }, "$from/$to/$value")
                assertEquals(outcome { j.convertInverse(number) }, outcome { k.convertInverse(number) }, "inverse $from/$to/$value")
            }
        }
        for ((from, to) in listOf("meter" to "second", "meter-and-foot" to "meter", "invalid" to "meter"))
            assertEquals(outcome { JConverter(from, to) }, outcome { UnitsConverter(from, to) })
    }
    @Test fun factorsAllConstantsPrefixesAndOperations() {
        val expressions = listOf("1", "2.5", "1/3", "ft_to_m", "ft2_to_m2", "ft3_to_m3", "in3_to_m3", "gal_to_m3", "PI",
            "gravity", "G", "gal_imp_to_m3", "lb_to_kg", "glucose_molar_mass", "item_per_mole", "meters_per_AU", "sec_per_julian_year",
            "speed_of_light_meters_per_second", "sho_to_m3", "tsubo_to_m2", "shaku_to_m", "AMU", "ft_to_m^2/PI", " 2 * 3 / 5 ", "2^-3")
        for (expression in expressions) {
            val j = JConverter.Factor.processFactor(expression); val k = UnitsConverter.Factor.processFactor(expression)
            assertEquals(j.conversionRate, k.getConversionRate(), expression)
            for (power in -3..3) assertEquals(j.power(power).conversionRate, k.power(power).getConversionRate(), "$expression ^$power")
            for (prefix in MeasurePrefix.entries) assertEquals(j.applyPrefix(com.ibm.icu.util.MeasureUnit.MeasurePrefix.valueOf(prefix.name)).conversionRate,
                k.applyPrefix(prefix).getConversionRate(), "$expression $prefix")
            val jo = JConverter.Factor.processFactor("PI/2"); val ko = UnitsConverter.Factor.processFactor("PI/2")
            assertEquals(j.divide(jo).conversionRate, k.divide(ko).getConversionRate())
            assertEquals(j.multiply(jo).conversionRate, k.multiply(ko).getConversionRate())
            assertEquals(j.divide(BigDecimal("0.3")).conversionRate, k.divide(BigDecimal("0.3")).getConversionRate())
        }
        for (expression in listOf<String?>(null, "", "/", "1/2/3", "2^3^4", "bogus", "2^2147483648", "2/0"))
            assertEquals(outcome { JConverter.Factor.processFactor(expression).conversionRate }, outcome { UnitsConverter.Factor.processFactor(expression).getConversionRate() }, expression)
    }
    @Test fun resourceRatesAndRecordParsing() {
        val j = JRates(); val k = ConversionRates()
        for (identifier in UnitConversionData().identifiers()) {
            val ju = JUnit.forIdentifier(identifier); val ku = MeasureUnitImpl.forIdentifier(identifier)
            assertEquals(outcome { j.getFactorToBase(ju).conversionRate }, outcome { k.getFactorToBase(ku).getConversionRate() }, identifier)
            assertEquals(j.extractCompoundBaseUnit(ju).identifier, k.extractCompoundBaseUnit(ku).getIdentifier())
            assertEquals(j.extractBaseUnits(ju).map { it.simpleUnitID to it.dimensionality }, k.extractBaseUnits(ku).map { it.getSimpleUnitID() to it.getDimensionality() })
            assertEquals(j.extractSystems(ju.singleUnits[0]), k.extractSystems(ku!!.getSingleUnits()[0]))
        }
        for (offset in listOf<String?>(null, "0", "1/3", "-12.5", "1/2/3", "1/", "/", "bad", "1/0")) {
            assertEquals(outcome { JRates.ConversionRateInfo("x", "y", "2", offset, null, "metric").offset },
                outcome { ConversionRates.ConversionRateInfo("x", "y", "2", offset, null, "metric").getOffset() }, offset)
        }
    }
    @Test fun factorCopyAndRatesSubclassCallbacks() {
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        val jf = object : JConverter.Factor() { override fun copy(): JConverter.Factor { jt.add("copy"); return JConverter.Factor.processFactor("2") } }
        val kf = object : UnitsConverter.Factor() { override fun copy(): UnitsConverter.Factor { kt.add("copy"); return UnitsConverter.Factor.processFactor("2") } }
        assertEquals(jf.conversionRate, kf.getConversionRate())
        assertEquals(jf.applyPrefix(com.ibm.icu.util.MeasureUnit.MeasurePrefix.KILO).conversionRate, kf.applyPrefix(MeasurePrefix.KILO).getConversionRate())
        assertEquals(jf.divide(BigDecimal("3")).conversionRate, kf.divide(BigDecimal("3")).getConversionRate())
        assertEquals(jt, kt)
        jt.clear(); kt.clear()
        val jr = object : JRates() {
            override fun extractBaseUnits(unit: JUnit): ArrayList<JSingle> { jt.add("base:${unit.identifier}"); return super.extractBaseUnits(unit) }
            override fun getSpecialMappingName(unit: JUnit): String? { jt.add("special:${unit.identifier}"); return super.getSpecialMappingName(unit) }
            override fun getFactorToBase(unit: JUnit): JConverter.Factor { jt.add("factor:${unit.identifier}"); return super.getFactorToBase(unit) }
            override fun getOffset(s: JUnit, t: JUnit, sf: JConverter.Factor, tf: JConverter.Factor, c: JConverter.Convertibility): BigDecimal {
                jt.add("offset:$c"); return super.getOffset(s, t, sf, tf, c)
            }
        }
        val kr = object : ConversionRates() {
            override fun extractBaseUnits(measureUnitImpl: MeasureUnitImpl?): ArrayList<SingleUnitImpl> { kt.add("base:${measureUnitImpl!!.getIdentifier()}"); return super.extractBaseUnits(measureUnitImpl) }
            override fun getSpecialMappingName(simpleUnit: MeasureUnitImpl?): String? { kt.add("special:${simpleUnit!!.getIdentifier()}"); return super.getSpecialMappingName(simpleUnit) }
            override fun getFactorToBase(measureUnit: MeasureUnitImpl?): UnitsConverter.Factor { kt.add("factor:${measureUnit!!.getIdentifier()}"); return super.getFactorToBase(measureUnit) }
            override fun getOffset(source: MeasureUnitImpl?, target: MeasureUnitImpl?, sourceToBase: UnitsConverter.Factor?, targetToBase: UnitsConverter.Factor?, convertibility: UnitsConverter.Convertibility?): BigDecimal {
                kt.add("offset:$convertibility"); return super.getOffset(source, target, sourceToBase, targetToBase, convertibility)
            }
        }
        val j = JConverter(JUnit.forIdentifier("celsius"), JUnit.forIdentifier("fahrenheit"), jr)
        val k = UnitsConverter(MeasureUnitImpl.forIdentifier("celsius"), MeasureUnitImpl.forIdentifier("fahrenheit"), kr)
        assertEquals(jt, kt); assertEquals(j.convert(BigDecimal("10")), k.convert(BigDecimal("10")))
    }
    @Test fun decimalSubclassCallbacksReceiveStandardContextIdentity() {
        fun scenario(source: Boolean): List<String> {
            val trace = mutableListOf<String>()
            val value = object : BigDecimal("3") {
                override fun toByte(): Byte = toInt().toByte()
                override fun toShort(): Short = toInt().toShort()
                override fun subtract(subtrahend: BigDecimal?): BigDecimal { trace.add("subtract"); return this }
                override fun divide(divisor: BigDecimal?, context: java.math.MathContext?): BigDecimal {
                    trace.add("divide:${context === java.math.MathContext.DECIMAL128}")
                    return BigDecimal.ZERO
                }
            }
            if (source) JConverter("meter", "foot").convertInverse(value) else UnitsConverter("meter", "foot").convertInverse(value)
            return trace
        }
        assertEquals(scenario(true), scenario(false))
    }

}
