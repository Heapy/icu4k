// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.IllegalIcuArgumentException

/** Common decimal conversion engine from ICU4J UnitsConverter, including reciprocal and special-scale mappings. */
internal class UnitConverter(source: MeasureUnitImpl, target: MeasureUnitImpl, data: UnitConversionData) {
    constructor(sourceIdentifier: String?, targetIdentifier: String?) : this(MeasureUnitImpl.forIdentifier(sourceIdentifier)!!, MeasureUnitImpl.forIdentifier(targetIdentifier)!!, UnitConversionData())
    private val conversionRate: UnitDecimal
    private val offset: UnitDecimal
    private val reciprocal: Boolean
    private val specialSource: String?
    private val specialTarget: String?
    init {
        val convertibility = extractConvertibility(source, target, data)
        if (convertibility == Convertibility.UNCONVERTIBLE) throw IllegalIcuArgumentException("input units must be convertible or reciprocal")
        specialSource = data.getSpecialMappingName(source)
        specialTarget = data.getSpecialMappingName(target)
        if (specialSource == null && specialTarget == null) {
            val sourceToBase = data.getFactorToBase(source)
            val targetToBase = data.getFactorToBase(target)
            reciprocal = convertibility == Convertibility.RECIPROCAL
            conversionRate = (if (reciprocal) sourceToBase.multiply(targetToBase) else sourceToBase.divide(targetToBase)).getConversionRate()
            offset = data.getOffset(source, target, targetToBase, !reciprocal)
        } else {
            reciprocal = false; offset = UnitDecimal.ZERO
            conversionRate = when {
                specialSource == null -> data.getFactorToBase(source).getConversionRate()
                specialTarget == null -> data.getFactorToBase(target).getConversionRate()
                else -> UnitDecimal.ONE
            }
        }
    }
    fun convert(input: UnitDecimal): UnitDecimal {
        if (specialSource != null || specialTarget != null) {
            val base = when (specialSource) {
                null -> input.multiply(conversionRate)
                "beaufort" -> scaleToBase(input)
                else -> input
            }
            return when (specialTarget) {
                null -> base.divide(conversionRate)
                "beaufort" -> baseToScale(base)
                else -> base
            }
        }
        val result = input.multiply(conversionRate).add(offset)
        return if (!reciprocal || result.compareTo(UnitDecimal.ZERO) == 0) {
            if (reciprocal) UnitDecimal.ZERO else result
        } else UnitDecimal.ONE.divide(result)
    }
    fun convertInverse(input: UnitDecimal): UnitDecimal {
        if (specialSource != null || specialTarget != null) {
            val base = when (specialTarget) {
                null -> input.multiply(conversionRate)
                "beaufort" -> scaleToBase(input)
                else -> input
            }
            return when (specialSource) {
                null -> base.divide(conversionRate)
                "beaufort" -> baseToScale(base)
                else -> base
            }
        }
        var result = input
        if (reciprocal) {
            if (result.compareTo(UnitDecimal.ZERO) == 0) return UnitDecimal.ZERO
            result = UnitDecimal.ONE.divide(result)
        }
        return result.subtract(offset).divide(conversionRate)
    }
    class ConversionInfo(var conversionRate: UnitDecimal, var offset: UnitDecimal, var reciprocal: Boolean)
    fun getConversionInfo(): ConversionInfo = ConversionInfo(conversionRate, offset, reciprocal)
    override fun toString(): String = "UnitsConverter [conversionRate=$conversionRate, offset=$offset]"
    private fun scaleToBase(input: UnitDecimal): UnitDecimal {
        val adjusted = input.abs().add(HALF)
        val maximum = UnitDecimal.valueOf((beaufortBounds.size - 2).toLong())
        val index = (if (adjusted <= maximum) adjusted else maximum).intValue()
        return beaufortBounds[index].add(beaufortBounds[index + 1]).multiply(HALF)
    }
    private fun baseToScale(input: UnitDecimal): UnitDecimal {
        val value = input.abs()
        var low = 0; var high = beaufortBounds.lastIndex
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val comparison = beaufortBounds[middle].compareTo(value)
            if (comparison < 0) low = middle + 1 else if (comparison > 0) high = middle - 1 else { found = middle; break }
        }
        val index = if (found >= 0) found else low - 1
        return UnitDecimal.valueOf(minOf(index, beaufortBounds.size - 2).toLong())
    }
    enum class Convertibility { CONVERTIBLE, RECIPROCAL, UNCONVERTIBLE }
    companion object {
        private val HALF = UnitDecimal("0.5")
        private val beaufortBounds = arrayOf("0.0", "0.3", "1.6", "3.4", "5.5", "8.0", "10.8", "13.9", "17.2", "20.8", "24.5", "28.5", "32.7", "36.9", "41.4", "46.1", "51.1", "55.8", "61.4").map(::UnitDecimal)
        fun extractConvertibility(source: MeasureUnitImpl, target: MeasureUnitImpl, data: UnitConversionData): Convertibility {
            val sourceUnits = data.extractBaseUnits(source)
            val targetUnits = data.extractBaseUnits(target)
            val dimensions = HashMap<String?, Int>()
            fun insert(units: List<SingleUnitImpl>, multiplier: Int) {
                for (unit in units) {
                    val id = unit.getSimpleUnitID()
                    dimensions[id] = (dimensions[id] ?: 0) + unit.getDimensionality() * multiplier
                }
            }
            insert(sourceUnits, 1); insert(targetUnits, -1)
            if (dimensions.values.all { it == 0 }) return Convertibility.CONVERTIBLE
            insert(targetUnits, 2)
            return if (dimensions.values.all { it == 0 }) Convertibility.RECIPROCAL else Convertibility.UNCONVERTIBLE
        }
    }
}
