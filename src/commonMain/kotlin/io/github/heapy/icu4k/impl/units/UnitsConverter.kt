// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.IllegalIcuArgumentException
import io.github.heapy.icu4k.impl.number.DecimalNumberParsing
import io.github.heapy.icu4k.util.*
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.math.abs

/** Public decimal unit conversion boundary, retaining overridable conversion-rate policies. */
open class UnitsConverter {
    private val conversionRate: BigDecimal
    private val reciprocal: Boolean
    private val offset: BigDecimal
    private val specialSource: String?
    private val specialTarget: String?

    constructor(sourceIdentifier: String?, targetIdentifier: String?) : this(
        MeasureUnitImpl.forIdentifier(sourceIdentifier), MeasureUnitImpl.forIdentifier(targetIdentifier), ConversionRates())
    constructor(source: MeasureUnitImpl?, target: MeasureUnitImpl?, conversionRates: ConversionRates?) {
        val convertibility = extractConvertibility(source, target, conversionRates)
        if (convertibility != Convertibility.CONVERTIBLE && convertibility != Convertibility.RECIPROCAL)
            throw IllegalIcuArgumentException("input units must be convertible or reciprocal")
        specialSource = conversionRates!!.specialMappingName(source)
        specialTarget = conversionRates.specialMappingName(target)
        if (specialSource == null && specialTarget == null) {
            val sourceToBase = conversionRates.getFactorToBase(source)
            val targetToBase = conversionRates.getFactorToBase(target)
            conversionRate = if (convertibility == Convertibility.CONVERTIBLE) sourceToBase.divide(targetToBase).getConversionRate()
                else {
                    assertUnitsConverter { convertibility == Convertibility.RECIPROCAL }
                    sourceToBase.multiply(targetToBase).getConversionRate()
                }
            reciprocal = convertibility == Convertibility.RECIPROCAL
            offset = conversionRates.offset(source, target, sourceToBase, targetToBase, convertibility)
            assertUnitsConverter { convertibility != Convertibility.RECIPROCAL || offset === BigDecimals.ZERO }
        } else {
            reciprocal = false; offset = BigDecimals.ZERO
            conversionRate = when {
                specialSource == null -> conversionRates.getFactorToBase(source).getConversionRate()
                specialTarget == null -> conversionRates.getFactorToBase(target).getConversionRate()
                else -> BigDecimals.ONE
            }
        }
    }
    open fun convert(inputValue: BigDecimal?): BigDecimal? {
        if (specialSource != null || specialTarget != null) {
            val base = if (specialSource != null) {
                if (specialSource == "beaufort") scaleToBase(inputValue) else inputValue
            } else inputValue!!.multiply(conversionRate)
            return if (specialTarget != null) {
                if (specialTarget == "beaufort") baseToScale(base) else base
            } else base!!.divide(conversionRate, DECIMAL128)
        }
        var result = inputValue!!.multiply(conversionRate).add(offset)
        if (reciprocal) {
            assertUnitsConverter { offset === BigDecimals.ZERO }
            if (result.compareTo(BigDecimals.ZERO) == 0) return BigDecimals.ZERO
            result = BigDecimals.ONE.divide(result, DECIMAL128)
        }
        return result
    }
    open fun convertInverse(inputValue: BigDecimal?): BigDecimal? {
        if (specialSource != null || specialTarget != null) {
            val base = if (specialTarget != null) {
                if (specialTarget == "beaufort") scaleToBase(inputValue) else inputValue
            } else inputValue!!.multiply(conversionRate)
            return if (specialSource != null) {
                if (specialSource == "beaufort") baseToScale(base) else base
            } else base!!.divide(conversionRate, DECIMAL128)
        }
        var result = inputValue
        if (reciprocal) {
            assertUnitsConverter { offset === BigDecimals.ZERO }
            if (result!!.compareTo(BigDecimals.ZERO) == 0) return BigDecimals.ZERO
            result = BigDecimals.ONE.divide(result, DECIMAL128)
        }
        return result!!.subtract(offset).divide(conversionRate, DECIMAL128)
    }
    enum class Convertibility { CONVERTIBLE, RECIPROCAL, UNCONVERTIBLE }
    open fun getConversionInfo(): ConversionInfo = ConversionInfo().also {
        it.conversionRate = conversionRate; it.offset = offset; it.reciprocal = reciprocal
    }
    open class ConversionInfo {
        @JvmField var conversionRate: BigDecimal? = null
        @JvmField var offset: BigDecimal? = null
        @JvmField var reciprocal = false
    }
    override fun toString(): String = "UnitsConverter [conversionRate=$conversionRate, offset=$offset]"
    private fun scaleToBase(value: BigDecimal?): BigDecimal {
        val half = BigDecimals.valueOf(0.5)
        val index = value!!.abs().add(half).min(BigDecimals.valueOf((bounds.size - 2).toLong())).toInt()
        return bounds[index].add(bounds[index + 1]).multiply(half)
    }
    private fun baseToScale(value: BigDecimal?): BigDecimal {
        val absolute = value!!.abs()
        var low = 0; var high = bounds.lastIndex; var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val comparison = bounds[middle].compareTo(absolute)
            if (comparison < 0) low = middle + 1 else if (comparison > 0) high = middle - 1 else { found = middle; break }
        }
        return BigDecimals.valueOf(minOf(if (found >= 0) found else low - 1, bounds.size - 2).toLong())
    }

    open class Factor {
        private var numerator = BigDecimals.ONE
        private var denominator = BigDecimals.ONE
        private var exponents = IntArray(15)
        protected open fun copy(): Factor = Factor().also {
            it.numerator = numerator; it.denominator = denominator; it.exponents = exponents.copyOf()
        }
        open fun getConversionRate(): BigDecimal {
            val collector = copy()
            val constants = arrayOf(BigDecimal("0.3048"), BigDecimal("411557987.0").divide(BigDecimal("131002976.0"), DECIMAL128),
                BigDecimal("9.80665"), BigDecimal("6.67408E-11"), BigDecimal("0.00454609"), BigDecimal("0.45359237"),
                BigDecimal("180.1557"), BigDecimal("6.02214076E+23"), BigDecimal("149597870700"), BigDecimal("31557600"),
                BigDecimal("299792458"), BigDecimal("0.001803906836964688204"), BigDecimal("3.305785123966942"),
                BigDecimal("0.033057851239669"), BigDecimal("1.66053878283E-27"))
            for (i in constants.indices) {
                val power = exponents[i]
                if (power == 0) continue
                val value = unitFactorPower(constants[i], abs(power), true)
                if (power > 0) collector.numerator = collector.numerator.multiply(value)
                else collector.denominator = collector.denominator.multiply(value)
            }
            return collector.numerator.divide(collector.denominator, DECIMAL128)
        }
        open fun applyPrefix(unitPrefix: MeasurePrefix?): Factor {
            val result = copy()
            if (unitPrefix == MeasurePrefix.ONE) return result
            val base = unitPrefix!!.getBase()
            val power = unitPrefix.getPower()
            val factor = unitFactorPower(BigDecimals.valueOf(base.toLong()), abs(power), true)
            if (power < 0) result.denominator = denominator.multiply(factor) else result.numerator = numerator.multiply(factor)
            return result
        }
        open fun power(power: Int): Factor = Factor().also {
            if (power != 0) {
                it.numerator = unitFactorPower(if (power > 0) numerator else denominator, if (power > 0) power else -power, false)
                it.denominator = unitFactorPower(if (power > 0) denominator else numerator, if (power > 0) power else -power, false)
                for (i in exponents.indices) it.exponents[i] = exponents[i] * power
            }
        }
        open fun divide(other: Factor?): Factor = Factor().also {
            it.numerator = numerator.multiply(other!!.denominator); it.denominator = denominator.multiply(other.numerator)
            for (i in exponents.indices) it.exponents[i] = exponents[i] - other.exponents[i]
        }
        open fun multiply(other: Factor?): Factor = Factor().also {
            it.numerator = numerator.multiply(other!!.numerator); it.denominator = denominator.multiply(other.denominator)
            for (i in exponents.indices) it.exponents[i] = exponents[i] + other.exponents[i]
        }
        open fun divide(value: BigDecimal?): Factor = copy().also { it.denominator = denominator.multiply(value) }
        private fun addEntity(entity: String, power: Int) {
            when (entity) {
                "ft2_to_m2" -> exponents[0] += 2 * power
                "ft3_to_m3" -> exponents[0] += 3 * power
                "in3_to_m3" -> { exponents[0] += 3 * power; denominator = denominator.multiply(BigDecimals.valueOf(1728.0)) }
                "gal_to_m3" -> { numerator = numerator.multiply(BigDecimals.valueOf(231)); exponents[0] += 3 * power; denominator = denominator.multiply(BigDecimals.valueOf(1728)) }
                else -> {
                    val index = names.indexOf(entity)
                    if (index >= 0) exponents[index] += power
                    else numerator = numerator.multiply(unitFactorPower(BigDecimal(entity), power, true))
                }
            }
        }
        companion object {
            private val names = arrayOf("ft_to_m", "PI", "gravity", "G", "gal_imp_to_m3", "lb_to_kg", "glucose_molar_mass", "item_per_mole", "meters_per_AU", "sec_per_julian_year", "speed_of_light_meters_per_second", "sho_to_m3", "tsubo_to_m2", "shaku_to_m", "AMU")
            @JvmStatic fun processFactor(factor: String?): Factor {
                assertUnitsConverter { factor!!.isNotEmpty() }
                val fractions = split(factor!!.filterNot { it == ' ' || it in '\t'..'\r' }, '/')
                assertUnitsConverter { fractions.size == 1 || fractions.size == 2 }
                fun withoutDivision(text: String): Factor = Factor().also { result ->
                    for (entity in split(text, '*')) {
                        val parts = split(entity, '^')
                        assertUnitsConverter { parts.size == 1 || parts.size == 2 }
                        result.addEntity(parts[0], if (parts.size == 2) parseExponent(parts[1]) else 1)
                    }
                }
                if (fractions.size == 1) return withoutDivision(fractions[0])
                val numerator = withoutDivision(fractions[0])
                val denominator = withoutDivision(fractions[1])
                return numerator.divide(denominator)
            }
            private fun parseExponent(text: String): Int = try { DecimalNumberParsing.int(text) }
                catch (_: NumberFormatException) { throw NumberFormatException("For input string: \"$text\"") }
            private fun split(text: String, char: Char): List<String> = if (text.isEmpty()) listOf("") else text.split(char).dropLastWhile { it.isEmpty() }
        }
    }
    companion object {
        internal val DECIMAL128 = unitConversionMathContext()
        private val bounds = arrayOf("0.0", "0.3", "1.6", "3.4", "5.5", "8.0", "10.8", "13.9", "17.2", "20.8", "24.5", "28.5", "32.7", "36.9", "41.4", "46.1", "51.1", "55.8", "61.4").map(::BigDecimal)
        @JvmStatic fun extractConvertibility(source: MeasureUnitImpl?, target: MeasureUnitImpl?, conversionRates: ConversionRates?): Convertibility {
            val sourceUnits = conversionRates!!.extractBaseUnits(source)
            val targetUnits = conversionRates.extractBaseUnits(target)
            val dimensions = HashMap<String?, Int>()
            fun insert(units: List<SingleUnitImpl>, multiplier: Int) {
                for (unit in units) {
                    if (dimensions.containsKey(unit.getSimpleUnitID())) {
                        val id = unit.getSimpleUnitID()
                        dimensions[id] = dimensions[unit.getSimpleUnitID()]!! + unit.getDimensionality() * multiplier
                    } else dimensions[unit.getSimpleUnitID()] = unit.getDimensionality() * multiplier
                }
            }
            insert(sourceUnits, 1); insert(targetUnits, -1)
            if (dimensions.values.all { it == 0 }) return Convertibility.CONVERTIBLE
            insert(targetUnits, 2)
            return if (dimensions.values.all { it == 0 }) Convertibility.RECIPROCAL else Convertibility.UNCONVERTIBLE
        }
    }
}
internal expect fun assertUnitsConverter(condition: () -> Boolean)
internal expect fun unitFactorPower(value: BigDecimal, power: Int, rounded: Boolean): BigDecimal

internal expect fun unitConversionMathContext(): MathContext
