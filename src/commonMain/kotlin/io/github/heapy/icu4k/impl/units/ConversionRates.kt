// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.impl.units.UnitsConverter.Convertibility
import io.github.heapy.icu4k.impl.units.UnitsConverter.Factor

/** CLDR conversion-rate records and overridable factor/offset lookup. */
open class ConversionRates {
    private val rates: HashMap<String, ConversionRateInfo> = ConversionRatesSink().also {
        val resource = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "units") as ICUResourceBundle
        resource.getAllItemsWithFallback(UnitIdentifierData.Constants.CONVERSION_UNIT_TABLE_NAME, it)
    }.getMapToConversionRate()

    open fun getFactorToBase(measureUnit: MeasureUnitImpl?): Factor {
        var result = Factor()
        for (single in measureUnit!!.getSingleUnits()) {
            val power = single.getDimensionality()
            val prefix = single.getPrefix()
            val factor = Factor.processFactor(rates[single.getSimpleUnitID()]!!.getConversionRate())
            result = result.multiply(factor.applyPrefix(prefix).power(power))
        }
        if (measureUnit.getConstantDenominator() != 0L) result = result.divide(BigDecimals.valueOf(measureUnit.getConstantDenominator()))
        return result
    }
    protected open fun getOffset(source: MeasureUnitImpl?, target: MeasureUnitImpl?, sourceToBase: Factor?,
        targetToBase: Factor?, convertibility: Convertibility?): BigDecimal {
        if (convertibility != Convertibility.CONVERTIBLE) return BigDecimals.valueOf(0)
        if (!(isSimple(source!!) && isSimple(target!!))) return BigDecimals.valueOf(0)
        val sourceID = source.getSingleUnits()[0].getSimpleUnitID()
        val targetID = target.getSingleUnits()[0].getSimpleUnitID()
        val sourceOffset = rates[sourceID]!!.getOffset()
        val targetOffset = rates[targetID]!!.getOffset()
        return sourceOffset.subtract(targetOffset).divide(targetToBase!!.getConversionRate(), UnitsConverter.DECIMAL128)
    }
    protected open fun getSpecialMappingName(simpleUnit: MeasureUnitImpl?): String? {
        if (!isSimple(simpleUnit!!)) return null
        return rates[simpleUnit.getSingleUnits()[0].getSimpleUnitID()]!!.getSpecialMappingName()
    }
    internal fun specialMappingName(unit: MeasureUnitImpl?): String? = getSpecialMappingName(unit)
    internal fun offset(source: MeasureUnitImpl?, target: MeasureUnitImpl?, sourceFactor: Factor?, targetFactor: Factor?,
        convertibility: Convertibility?): BigDecimal = getOffset(source, target, sourceFactor, targetFactor, convertibility)
    open fun extractCompoundBaseUnit(measureUnit: MeasureUnitImpl?): MeasureUnitImpl {
        val baseUnits = extractBaseUnits(measureUnit)
        return MeasureUnitImpl().also { for (unit in baseUnits) it.appendSingleUnit(unit) }
    }
    open fun extractBaseUnits(measureUnitImpl: MeasureUnitImpl?): ArrayList<SingleUnitImpl> {
        val result = ArrayList<SingleUnitImpl>()
        for (unit in measureUnitImpl!!.getSingleUnits()) result.addAll(extractBaseUnits(unit))
        return result
    }
    open fun extractBaseUnits(singleUnit: SingleUnitImpl?): ArrayList<SingleUnitImpl> {
        val target = rates[singleUnit!!.getSimpleUnitID()]!!.getTarget()
        val targetImpl = MeasureUnitImpl.UnitsParser.parseForIdentifier(target)!!
        targetImpl.applyDimensionality(singleUnit.getDimensionality())
        return targetImpl.getSingleUnits()
    }
    open fun extractSystems(singleUnit: SingleUnitImpl?): String? = rates[singleUnit!!.getSimpleUnitID()]!!.getSystems()
    private fun isSimple(unit: MeasureUnitImpl): Boolean {
        if (unit.getComplexity() != UnitComplexity.SINGLE) return false
        val single = unit.getSingleUnits()[0]
        if (single.getPrefix() != MeasurePrefix.ONE) return false
        if (single.getDimensionality() != 1) return false
        return true
    }

    open class ConversionRatesSink : UResource.Sink() {
        private val rates = HashMap<String, ConversionRateInfo>()
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            assertConversionRates { key.toString() == UnitIdentifierData.Constants.CONVERSION_UNIT_TABLE_NAME }
            val table = value.getTable()
            var i = 0
            while (table.getKeyAndValue(i++, key, value)) {
                assertConversionRates { value.getType() == UResourceBundle.TABLE }
                val unit = key.toString()
                val fields = value.getTable()
                var target: String? = null; var factor: String? = null; var offset = "0"
                var special: String? = null; var systems: String? = null
                var j = 0
                while (fields.getKeyAndValue(j++, key, value)) {
                    assertConversionRates { value.getType() == UResourceBundle.STRING }
                    val name = key.toString()
                    val text = value.toString().replace(" ", "")
                    when (name) {
                        "target" -> target = text
                        "factor" -> factor = text
                        "offset" -> offset = text
                        "special" -> special = text
                        "systems" -> systems = value.toString()
                        else -> assertConversionRates(false) { "The key must be target, factor, offset, special, or systems" }
                    }
                }
                assertConversionRates { target != null }
                assertConversionRates { factor != null || special != null }
                rates[unit] = ConversionRateInfo(unit, target, factor, offset, special, systems)
            }
        }
        open fun getMapToConversionRate(): HashMap<String, ConversionRateInfo> = rates
    }
    open class ConversionRateInfo(private val simpleUnit: String?, private val target: String?,
        private val conversionRate: String?, offset: String?, private val specialMappingName: String?, private val systems: String?) {
        private val offset = parseOffset(offset)
        open fun getTarget(): String? = target
        open fun getOffset(): BigDecimal = offset
        open fun getConversionRate(): String = conversionRate
            ?: throw IllegalIcuArgumentException("trying to use a null conversion rate (for special?)")
        open fun getSpecialMappingName(): String? = specialMappingName
        open fun getSystems(): String? = systems
        private companion object {
            fun parseOffset(value: String?): BigDecimal {
                val numbers = if (value!!.isEmpty()) listOf("") else value.split('/').dropLastWhile { it.isEmpty() }
                assertConversionRates { numbers.size <= 2 }
                if (numbers.size == 1) return BigDecimal(numbers[0])
                return BigDecimal(numbers[0]).divide(BigDecimal(numbers[1]), UnitsConverter.DECIMAL128)
            }
        }
    }
}
internal expect fun assertConversionRates(condition: () -> Boolean)
internal expect fun assertConversionRates(condition: Boolean, message: () -> String)
