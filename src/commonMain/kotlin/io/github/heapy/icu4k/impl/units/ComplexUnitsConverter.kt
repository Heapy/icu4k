// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.BigDecimals
import io.github.heapy.icu4k.util.Measure
import io.github.heapy.icu4k.util.RoundingMode
import kotlin.jvm.JvmField

/** Decimal mixed-unit conversion with rounding/carry and caller-selected unit order. */
open class ComplexUnitsConverter {
    @JvmField var unitsConverters_: ArrayList<UnitsConverter?>? = null
    @JvmField var units_: MutableList<MeasureUnitImpl.MeasureUnitImplWithIndex?>? = null
    private val inputUnit: MeasureUnitImpl?

    @Suppress("UNCHECKED_CAST")
    constructor(targetUnit: MeasureUnitImpl?, conversionRates: ConversionRates?) {
        units_ = targetUnit!!.extractIndividualUnitsWithIndices() as MutableList<MeasureUnitImpl.MeasureUnitImplWithIndex?>
        assertComplexUnitsConverter { units_!!.isNotEmpty() }
        var biggest = units_!![0]!!.unitImpl
        for (unit in units_!!) if (compare(unit!!.unitImpl, biggest, conversionRates) > 0) biggest = unit.unitImpl
        inputUnit = biggest
        initialize(conversionRates)
    }
    constructor(inputUnitIdentifier: String?, outputUnitsIdentifier: String?) : this(
        MeasureUnitImpl.forIdentifier(inputUnitIdentifier), MeasureUnitImpl.forIdentifier(outputUnitsIdentifier), ConversionRates())
    @Suppress("UNCHECKED_CAST")
    constructor(inputUnit: MeasureUnitImpl?, outputUnits: MeasureUnitImpl?, conversionRates: ConversionRates?) {
        this.inputUnit = inputUnit
        units_ = outputUnits!!.extractIndividualUnitsWithIndices() as MutableList<MeasureUnitImpl.MeasureUnitImplWithIndex?>
        assertComplexUnitsConverter { units_!!.isNotEmpty() }
        initialize(conversionRates)
    }
    private fun initialize(rates: ConversionRates?) {
        sortMixedUnitList(units_!!, Comparator { a, b -> compare(b!!.unitImpl, a!!.unitImpl, rates) })
        unitsConverters_ = ArrayList()
        for (i in units_!!.indices) {
            val source = if (i == 0) inputUnit else units_!![i - 1]!!.unitImpl
            unitsConverters_!!.add(UnitsConverter(source, units_!![i]!!.unitImpl, rates))
        }
    }
    private fun compare(a: MeasureUnitImpl, b: MeasureUnitImpl, rates: ConversionRates?): Int {
        val first = rates!!.specialMappingName(a)
        val second = rates.specialMappingName(b)
        if (first != null || second != null) {
            if (first == null) return -1
            if (second == null) return 1
            return first.compareTo(second)
        }
        val factorA = rates.getFactorToBase(a).getConversionRate()
        val factorB = rates.getFactorToBase(b).getConversionRate()
        return factorA.compareTo(factorB)
    }
    open fun greaterThanOrEqual(quantity: BigDecimal?, limit: BigDecimal?): Boolean {
        assertComplexUnitsConverter { units_!!.isNotEmpty() }
        return unitsConverters_!![0]!!.convert(quantity)!!.multiply(EPSILON_MULTIPLIER).compareTo(limit!!) >= 0
    }
    open class ComplexConverterResult internal constructor(@JvmField val indexOfQuantity: Int,
        @JvmField val measures: MutableList<Measure?>?)

    open fun convert(quantity: BigDecimal?, rounder: Precision?): ComplexConverterResult {
        var value = quantity
        var sign = MixedUnitInteger.one()
        if (value!!.compareTo(BigDecimals.ZERO) < 0 && unitsConverters_!!.size > 1) {
            value = value.abs(); sign = sign.negate()
        }
        val integers = ArrayList<MixedUnitInteger>(unitsConverters_!!.size - 1)
        var i = 0
        val n = unitsConverters_!!.size
        while (i < n) {
            value = unitsConverters_!![i]!!.convert(value)
            if (i < n - 1) {
                val floored = MixedUnitInteger(value!!.multiply(EPSILON_MULTIPLIER).setScale(0, RoundingMode.FLOOR))
                integers.add(floored)
                val remainder = value.subtract(BigDecimals.valueOf(floored.toLong()))
                value = if (remainder.compareTo(BigDecimals.ZERO) == -1) BigDecimals.ZERO else remainder
            }
            i++
        }
        value = applyRounder(integers, value, rounder)
        val measures = ArrayList<Measure?>(unitsConverters_!!.size)
        i = 0
        while (i < unitsConverters_!!.size) { measures.add(null); i++ }
        var indexOfQuantity = -1
        i = 0
        val count = unitsConverters_!!.size
        while (i < count) {
            if (i < count - 1) {
                val measure = Measure(integers[i].multiply(sign).toNumber(), units_!![i]!!.unitImpl.build())
                measures[units_!![i]!!.index] = measure
            } else {
                indexOfQuantity = units_!![i]!!.index
                val measure = Measure(value!!.multiply(BigDecimals.valueOf(sign.toLong())), units_!![i]!!.unitImpl.build())
                measures[indexOfQuantity] = measure
            }
            i++
        }
        return ComplexConverterResult(indexOfQuantity, measures)
    }
    private fun applyRounder(integers: MutableList<MixedUnitInteger>, quantity: BigDecimal?, rounder: Precision?): BigDecimal? {
        if (rounder == null) return quantity
        val bcd = DecimalQuantity_DualStorageBCD(quantity)
        rounder.apply(bcd)
        var value = bcd.toBigDecimal()
        if (integers.isEmpty()) return value
        val last = unitsConverters_!!.size - 1
        var carry = unitsConverters_!![last]!!.convertInverse(value)!!.multiply(EPSILON_MULTIPLIER).setScale(0, RoundingMode.FLOOR)
        if (carry.compareTo(BigDecimals.ZERO) <= 0) return value
        value = value.subtract(unitsConverters_!![last]!!.convert(carry))
        integers[last - 1] = integers[last - 1].add(MixedUnitInteger(carry))
        for (j in last - 1 downTo 1) {
            carry = unitsConverters_!![j]!!.convertInverse(BigDecimals.valueOf(integers[j].toLong()))!!
                .multiply(EPSILON_MULTIPLIER).setScale(0, RoundingMode.FLOOR)
            if (carry.compareTo(BigDecimals.ZERO) <= 0) break
            integers[j] = integers[j].subtract(MixedUnitInteger(unitsConverters_!![j]!!.convert(carry)!!))
            integers[j - 1] = integers[j - 1].add(MixedUnitInteger(carry))
        }
        return value
    }
    companion object {
        @JvmField val EPSILON: BigDecimal = BigDecimals.valueOf(2.220446049250313E-16)
        @JvmField val EPSILON_MULTIPLIER: BigDecimal = BigDecimals.valueOf(1).add(EPSILON)
    }
}
internal expect fun assertComplexUnitsConverter(condition: () -> Boolean)
internal expect class MixedUnitInteger {
    constructor(value: BigDecimal)
    fun negate(): MixedUnitInteger
    fun add(other: MixedUnitInteger): MixedUnitInteger
    fun subtract(other: MixedUnitInteger): MixedUnitInteger
    fun multiply(other: MixedUnitInteger): MixedUnitInteger
    fun toLong(): Long
    fun toNumber(): Number
    companion object { fun one(): MixedUnitInteger }
}

/** Arbitrary-precision integral Number for hosts without java.math.BigInteger. */
internal class MixedUnitIntegerNumber(private val value: UnitDecimal) : Number() {
    override fun toByte(): Byte = value.longValue().toByte()
    override fun toShort(): Short = value.longValue().toShort()
    override fun toInt(): Int = value.intValue()
    override fun toLong(): Long = value.longValue()
    override fun toFloat(): Float = value.toString().toFloat()
    override fun toDouble(): Double = value.toString().toDouble()
    override fun toString(): String = value.toString()
    override fun equals(other: Any?): Boolean = other is MixedUnitIntegerNumber && value == other.value
    override fun hashCode(): Int {
        val words = mutableListOf(0u)
        for (digit in value.magnitude) {
            var carry = (digit - '0').toULong()
            for (i in words.indices) {
                val product = words[i].toULong() * 10u + carry
                words[i] = product.toUInt(); carry = product shr 32
            }
            if (carry != 0uL) words.add(carry.toUInt())
        }
        var hash = 0
        for (i in words.indices.reversed()) hash = 31 * hash + words[i].toInt()
        return hash * value.sign
    }
}

internal expect fun sortMixedUnitList(units: MutableList<MeasureUnitImpl.MeasureUnitImplWithIndex?>, comparator: Comparator<MeasureUnitImpl.MeasureUnitImplWithIndex?>)
