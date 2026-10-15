package com.ibm.icu.impl.units

import io.github.heapy.icu4k.impl.units.UnitConversionFactor
import io.github.heapy.icu4k.impl.units.UnitConversionData
import io.github.heapy.icu4k.impl.units.UnitDecimal
import io.github.heapy.icu4k.impl.units.MeasureUnitImpl

import io.github.heapy.icu4k.util.MeasurePrefix
import kotlin.test.*
import com.ibm.icu.impl.units.UnitsConverter.Factor as JFactor

class UnitConversionFactorOracleTest {
    private fun check(j: JFactor, k: UnitConversionFactor, label: String) = assertEquals(j.conversionRate.toString(), k.getConversionRate().toString(), label)
    @Test fun everyResourceExpressionAndAllPrefixesMatchSource() {
        val data = UnitConversionData()
        for (id in data.identifiers()) {
            val rate = data.rate(id)!!
            if (rate.special != null) continue
            val text = rate.conversionRate()
            val j = JFactor.processFactor(text); val k = UnitConversionFactor.processFactor(text)
            check(j, k, id)
            for (power in listOf(-3, -1, 0, 2, 5)) check(j.power(power), k.power(power), "$id^$power")
            for (prefix in MeasurePrefix.entries) {
                val jp = com.ibm.icu.util.MeasureUnit.MeasurePrefix.valueOf(prefix.name)
                check(j.applyPrefix(jp), k.applyPrefix(prefix), "$id/$prefix")
            }
        }
    }
    @Test fun compositeExpressionsPreserveSourcePowerAndScaleBehavior() {
        val expressions = listOf("٢^٣", "1", "1.00", "1e-20", " 2 \t* 3 / 4 ", "PI", "PI^2", "ft2_to_m2", "ft3_to_m3", "in3_to_m3^0", "gal_to_m3^2", "in3_to_m3^-1", "G*gravity", "3^-2", "ft_to_m/ft_to_m", "PI*12.0/PI", "AMU*item_per_mole", "meters_per_AU/sec_per_julian_year")
        for (a in expressions) {
            val j = JFactor.processFactor(a); val k = UnitConversionFactor.processFactor(a)
            check(j, k, a)
            for (b in expressions.take(7)) {
                val jb = JFactor.processFactor(b); val kb = UnitConversionFactor.processFactor(b)
                check(j.multiply(jb), k.multiply(kb), "$a * $b")
                check(j.divide(jb), k.divide(kb), "$a / $b")
            }
            check(j.divide(java.math.BigDecimal("100.0")), k.divide(UnitDecimal("100.0")), "$a / 100.0")
        }
    }
    @Test fun fullUnitFactorsAndOffsetsMatchSource() {
        class Rates : com.ibm.icu.impl.units.ConversionRates() {
            fun offset(source: com.ibm.icu.impl.units.MeasureUnitImpl, target: com.ibm.icu.impl.units.MeasureUnitImpl, convertible: Boolean): java.math.BigDecimal =
                getOffset(source, target, getFactorToBase(source), getFactorToBase(target), if (convertible) com.ibm.icu.impl.units.UnitsConverter.Convertibility.CONVERTIBLE else com.ibm.icu.impl.units.UnitsConverter.Convertibility.RECIPROCAL)
        }
        val j = Rates(); val k = UnitConversionData()
        for (id in listOf("square-kilofoot-per-hour", "liter-per-100-kilometer", "milligram-per-deciliter", "kilogram-meter-per-square-second", "pound-per-square-inch", "fahrenheit", "celsius", "kelvin", "meter-per-meter")) {
            val ju = com.ibm.icu.impl.units.MeasureUnitImpl.forIdentifier(id)
            val ku = MeasureUnitImpl.forIdentifier(id)!!
            check(j.getFactorToBase(ju), k.getFactorToBase(ku), id)
        }
        for (source in listOf("fahrenheit", "celsius", "kelvin", "kilocelsius", "square-celsius")) for (target in listOf("fahrenheit", "celsius", "kelvin")) for (convertible in listOf(false, true)) {
            val js = com.ibm.icu.impl.units.MeasureUnitImpl.forIdentifier(source); val jt = com.ibm.icu.impl.units.MeasureUnitImpl.forIdentifier(target)
            val ks = MeasureUnitImpl.forIdentifier(source)!!; val kt = MeasureUnitImpl.forIdentifier(target)!!
            assertEquals(j.offset(js, jt, convertible).toString(), k.getOffset(ks, kt, k.getFactorToBase(kt), convertible).toString(), "$source/$target/$convertible")
        }
    }

}
