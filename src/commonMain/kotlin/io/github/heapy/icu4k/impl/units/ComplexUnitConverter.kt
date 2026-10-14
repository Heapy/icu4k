// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

/** Mixed-unit arithmetic from ComplexUnitsConverter; public Measure/Precision bridges remain separate. */
internal class ComplexUnitConverter {
    val units: MutableList<MeasureUnitImpl.MeasureUnitImplWithIndex>
    val converters = ArrayList<UnitConverter>()
    private val input: MeasureUnitImpl
    constructor(target: MeasureUnitImpl, data: UnitConversionData) {
        units = target.extractIndividualUnitsWithIndices()
        var largest = units[0].unitImpl
        val comparator = MeasureUnitImpl.MeasureUnitImplComparator(data)
        for (unit in units) if (comparator.compare(unit.unitImpl, largest) > 0) largest = unit.unitImpl
        input = largest
        initialize(data)
    }
    constructor(input: MeasureUnitImpl, output: MeasureUnitImpl, data: UnitConversionData) {
        this.input = input; units = output.extractIndividualUnitsWithIndices(); initialize(data)
    }
    constructor(input: String?, output: String?) : this(MeasureUnitImpl.forIdentifier(input)!!, MeasureUnitImpl.forIdentifier(output)!!, UnitConversionData())
    private fun initialize(data: UnitConversionData) {
        val comparator = MeasureUnitImpl.MeasureUnitImplWithIndexComparator(data)
        units.sortWith { a, b -> comparator.compare(b, a) }
        for (i in units.indices) converters.add(UnitConverter(if (i == 0) input else units[i - 1].unitImpl, units[i].unitImpl, data))
    }
    fun greaterThanOrEqual(quantity: UnitDecimal, limit: UnitDecimal): Boolean = converters[0].convert(quantity).multiply(EPSILON_MULTIPLIER) >= limit
    class MeasureValue(val quantity: UnitDecimal, val unit: MeasureUnitImpl, val integer: Boolean)
    class Result(val indexOfQuantity: Int, val measures: List<MeasureValue>)
    fun convert(quantity: UnitDecimal, rounder: ((UnitDecimal) -> UnitDecimal)? = null): Result {
        var value = quantity
        var sign = 1L
        if (value < UnitDecimal.ZERO && converters.size > 1) { value = value.abs(); sign = -1L }
        val integers = ArrayList<UnitDecimal>()
        for (i in converters.indices) {
            value = converters[i].convert(value)
            if (i < converters.lastIndex) {
                val floored = value.multiply(EPSILON_MULTIPLIER).setScale(0, UnitDecimal.Rounding.FLOOR)
                integers.add(floored)
                // ICU4J deliberately converts via the low 64 bits here; retain that behavior.
                val remainder = value.subtract(UnitDecimal.valueOf(floored.longValue()))
                value = if (remainder < UnitDecimal.ZERO) UnitDecimal.ZERO else remainder
            }
        }
        value = applyRounder(integers, value, rounder)
        val measures = arrayOfNulls<MeasureValue>(converters.size)
        var indexOfQuantity = -1
        for (i in converters.indices) {
            val integer = i < converters.lastIndex
            val result = (if (integer) integers[i] else value).multiply(UnitDecimal.valueOf(sign))
            val index = units[i].index
            measures[index] = MeasureValue(result, units[i].unitImpl, integer)
            if (!integer) indexOfQuantity = index
        }
        return Result(indexOfQuantity, measures.map { it!! })
    }
    private fun applyRounder(integers: MutableList<UnitDecimal>, quantity: UnitDecimal, rounder: ((UnitDecimal) -> UnitDecimal)?): UnitDecimal {
        if (rounder == null) return quantity
        var value = rounder(quantity)
        if (integers.isEmpty()) return value
        val last = converters.lastIndex
        var carry = converters[last].convertInverse(value).multiply(EPSILON_MULTIPLIER).setScale(0, UnitDecimal.Rounding.FLOOR)
        if (carry <= UnitDecimal.ZERO) return value
        value = value.subtract(converters[last].convert(carry))
        integers[last - 1] = integers[last - 1].add(carry)
        for (j in last - 1 downTo 1) {
            carry = converters[j].convertInverse(UnitDecimal.valueOf(integers[j].longValue())).multiply(EPSILON_MULTIPLIER).setScale(0, UnitDecimal.Rounding.FLOOR)
            if (carry <= UnitDecimal.ZERO) break
            integers[j] = integers[j].subtract(converters[j].convert(carry).integerValue())
            integers[j - 1] = integers[j - 1].add(carry)
        }
        return value
    }
    companion object {
        val EPSILON = UnitDecimal("2.220446049250313E-16")
        val EPSILON_MULTIPLIER = UnitDecimal.ONE.add(EPSILON)
    }
}
