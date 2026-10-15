// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.util.MeasurePrefix

/** Symbolic numerator/denominator model from UnitsConverter.Factor, using common exact decimal arithmetic. */
internal class UnitConversionFactor {
    private var numerator = UnitDecimal.ONE
    private var denominator = UnitDecimal.ONE
    private var exponents = IntArray(15)
    fun copy(): UnitConversionFactor = UnitConversionFactor().also {
        it.numerator = numerator; it.denominator = denominator; it.exponents = exponents.copyOf()
    }
    fun getConversionRate(): UnitDecimal {
        val result = copy()
        val constants = arrayOf(
            UnitDecimal("0.3048"), UnitDecimal("411557987.0").divide(UnitDecimal("131002976.0")),
            UnitDecimal("9.80665"), UnitDecimal("6.67408E-11"), UnitDecimal("0.00454609"),
            UnitDecimal("0.45359237"), UnitDecimal("180.1557"), UnitDecimal("6.02214076E+23"),
            UnitDecimal("149597870700"), UnitDecimal("31557600"), UnitDecimal("299792458"),
            UnitDecimal("0.001803906836964688204"), UnitDecimal("3.305785123966942"),
            UnitDecimal("0.033057851239669"), UnitDecimal("1.66053878283E-27"),
        )
        for (i in constants.indices) {
            val power = exponents[i]
            if (power == 0) continue
            val value = constants[i].powRounded(kotlin.math.abs(power))
            if (power > 0) result.numerator = result.numerator.multiply(value) else result.denominator = result.denominator.multiply(value)
        }
        return result.numerator.divide(result.denominator)
    }
    fun applyPrefix(prefix: MeasurePrefix?): UnitConversionFactor {
        val result = copy()
        if (prefix == MeasurePrefix.ONE) return result
        val power = prefix!!.getPower()
        val factor = UnitDecimal.valueOf(prefix.getBase().toLong()).powRounded(kotlin.math.abs(power))
        if (power < 0) result.denominator = denominator.multiply(factor) else result.numerator = numerator.multiply(factor)
        return result
    }
    fun power(power: Int): UnitConversionFactor {
        val result = UnitConversionFactor()
        if (power == 0) return result
        result.numerator = (if (power > 0) numerator else denominator).pow(if (power > 0) power else -power)
        result.denominator = (if (power > 0) denominator else numerator).pow(if (power > 0) power else -power)
        for (i in exponents.indices) result.exponents[i] = exponents[i] * power
        return result
    }
    fun multiply(other: UnitConversionFactor): UnitConversionFactor = UnitConversionFactor().also {
        it.numerator = numerator.multiply(other.numerator); it.denominator = denominator.multiply(other.denominator)
        for (i in exponents.indices) it.exponents[i] = exponents[i] + other.exponents[i]
    }
    fun divide(other: UnitConversionFactor): UnitConversionFactor = UnitConversionFactor().also {
        it.numerator = numerator.multiply(other.denominator); it.denominator = denominator.multiply(other.numerator)
        for (i in exponents.indices) it.exponents[i] = exponents[i] - other.exponents[i]
    }
    fun divide(value: UnitDecimal): UnitConversionFactor = copy().also { it.denominator = denominator.multiply(value) }
    private fun addPoweredEntity(entity: String) {
        val parts = javaSplit(entity, '^')
        assertUnitFactor(parts.size == 1 || parts.size == 2)
        val power = if (parts.size == 2) io.github.heapy.icu4k.impl.number.DecimalNumberParsing.int(parts[1]) else 1
        addEntity(parts[0], power)
    }
    private fun addEntity(entity: String, power: Int) {
        when (entity) {
            "ft_to_m" -> exponents[0] += power
            "ft2_to_m2" -> exponents[0] += 2 * power
            "ft3_to_m3" -> exponents[0] += 3 * power
            "in3_to_m3" -> { exponents[0] += 3 * power; denominator = denominator.multiply(UnitDecimal("1728.0")) }
            "gal_to_m3" -> {
                numerator = numerator.multiply(UnitDecimal("231")); exponents[0] += 3 * power
                denominator = denominator.multiply(UnitDecimal("1728"))
            }
            else -> {
                val index = constantNames.indexOf(entity)
                if (index >= 0) exponents[index] += power
                else numerator = numerator.multiply(UnitDecimal(entity).powRounded(power))
            }
        }
    }
    companion object {
        private val constantNames = arrayOf("ft_to_m", "PI", "gravity", "G", "gal_imp_to_m3", "lb_to_kg", "glucose_molar_mass", "item_per_mole", "meters_per_AU", "sec_per_julian_year", "speed_of_light_meters_per_second", "sho_to_m3", "tsubo_to_m2", "shaku_to_m", "AMU")
        fun processFactor(factor: String): UnitConversionFactor {
            assertUnitFactor(factor.isNotEmpty())
            val fractions = javaSplit(factor.filterNot { it == ' ' || it in '\t'..'\r' }, '/')
            assertUnitFactor(fractions.size == 1 || fractions.size == 2)
            val numerator = withoutDivision(fractions[0])
            return if (fractions.size == 1) numerator else numerator.divide(withoutDivision(fractions[1]))
        }
        private fun withoutDivision(expression: String): UnitConversionFactor = UnitConversionFactor().also { result ->
            for (entity in javaSplit(expression, '*')) result.addPoweredEntity(entity)
        }
        private fun javaSplit(text: String, separator: Char): List<String> = if (text.isEmpty()) listOf("") else text.split(separator).dropLastWhile { it.isEmpty() }
    }
}
internal expect fun assertUnitFactor(condition: Boolean)
