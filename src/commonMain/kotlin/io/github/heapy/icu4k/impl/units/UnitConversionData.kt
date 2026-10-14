// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.IllegalIcuArgumentException
import io.github.heapy.icu4k.impl.UResource
import io.github.heapy.icu4k.util.MeasurePrefix
import io.github.heapy.icu4k.util.UnitComplexity
import io.github.heapy.icu4k.util.UResourceBundle

/** Resource lookup, base dimensions, decimal factors and offsets shared by the unit conversion port. */
internal class UnitConversionData {
    class Rate(val target: String?, private val factor: String?, val offsetExpression: String, val special: String?, val systems: String?) {
        fun conversionRate(): String = factor ?: throw IllegalIcuArgumentException("trying to use a null conversion rate (for special?)")
    }
    class Sink : UResource.Sink() {
        val rates = HashMap<String, Rate>()
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val table = value.getTable()
            var index = 0
            while (table.getKeyAndValue(index++, key, value)) {
                val name = key.toString()
                val fields = value.getTable()
                var target: String? = null; var factor: String? = null; var offset = "0"
                var special: String? = null; var systems: String? = null
                var fieldIndex = 0
                while (fields.getKeyAndValue(fieldIndex++, key, value)) {
                    val field = key.toString()
                    val text = value.toString().replace(" ", "")
                    when (field) {
                        "target" -> target = text
                        "factor" -> factor = text
                        "offset" -> offset = text
                        "special" -> special = text
                        "systems" -> systems = value.toString()
                    }
                }
                rates[name] = Rate(target, factor, offset, special, systems)
            }
        }
    }
    private val rates = Sink().also {
        (UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "units") as ICUResourceBundle)
            .getAllItemsWithFallback(UnitIdentifierData.Constants.CONVERSION_UNIT_TABLE_NAME, it)
    }.rates
    fun rate(simpleUnit: String?): Rate? = rates[simpleUnit]
    fun identifiers(): Set<String> = rates.keys
    fun extractBaseUnits(unit: SingleUnitImpl): ArrayList<SingleUnitImpl> {
        val target = MeasureUnitImpl.UnitsParser.parseForIdentifier(rates[unit.getSimpleUnitID()]!!.target)!!
        target.applyDimensionality(unit.getDimensionality())
        return target.getSingleUnits()
    }
    fun extractBaseUnits(unit: MeasureUnitImpl): ArrayList<SingleUnitImpl> {
        val result = ArrayList<SingleUnitImpl>()
        for (single in unit.getSingleUnits()) result.addAll(extractBaseUnits(single))
        return result
    }
    fun extractCompoundBaseUnit(unit: MeasureUnitImpl): MeasureUnitImpl {
        val result = MeasureUnitImpl()
        for (single in extractBaseUnits(unit)) result.appendSingleUnit(single)
        return result
    }
    fun extractSystems(unit: SingleUnitImpl): String? = rates[unit.getSimpleUnitID()]!!.systems
    fun getFactorToBase(unit: MeasureUnitImpl): UnitConversionFactor {
        var result = UnitConversionFactor()
        for (single in unit.getSingleUnits()) {
            val factor = UnitConversionFactor.processFactor(rates[single.getSimpleUnitID()]!!.conversionRate())
                .applyPrefix(single.getPrefix()).power(single.getDimensionality())
            result = result.multiply(factor)
        }
        if (unit.getConstantDenominator() != 0L) result = result.divide(UnitDecimal.valueOf(unit.getConstantDenominator()))
        return result
    }
    fun getOffset(source: MeasureUnitImpl, target: MeasureUnitImpl, targetToBase: UnitConversionFactor, convertible: Boolean): UnitDecimal {
        if (!convertible || !isSimpleUnprefixed(source) || !isSimpleUnprefixed(target)) return UnitDecimal.ZERO
        fun offset(unit: MeasureUnitImpl): UnitDecimal {
            val parts = rates[unit.getSingleUnits()[0].getSimpleUnitID()]!!.offsetExpression.split('/')
            return if (parts.size == 1) UnitDecimal(parts[0]) else UnitDecimal(parts[0]).divide(UnitDecimal(parts[1]))
        }
        return offset(source).subtract(offset(target)).divide(targetToBase.getConversionRate())
    }
    private fun isSimpleUnprefixed(unit: MeasureUnitImpl): Boolean {
        if (unit.getComplexity() != UnitComplexity.SINGLE) return false
        val single = unit.getSingleUnits()[0]
        return single.getPrefix() == MeasurePrefix.ONE && single.getDimensionality() == 1
    }
    fun getSpecialMappingName(unit: MeasureUnitImpl): String? {
        if (!isSimpleUnprefixed(unit)) return null
        return rates[unit.getSingleUnits()[0].getSimpleUnitID()]!!.special
    }
    fun getCategory(unit: MeasureUnitImpl): String {
        val base = extractCompoundBaseUnit(unit)
        base.serialize()
        val indices = UnitIdentifierData.getBaseUnitToCategoryIndex()
        var index = indices[base.getIdentifier()]
        if (index == null) {
            base.takeReciprocal(); base.serialize(); index = indices[base.getIdentifier()]
        }
        // Preserve the source's unconditional reciprocal before simplifying.
        base.takeReciprocal()
        val simplified = base.copyAndSimplify()
        if (index == null) {
            simplified.serialize(); index = indices[simplified.getIdentifier()]
        }
        if (index == null) {
            simplified.takeReciprocal(); simplified.serialize(); index = indices[simplified.getIdentifier()]
        }
        if (index == null) throw IllegalIcuArgumentException("This unit does not has a category" + unit.getIdentifier())
        return UnitIdentifierData.getCategoryNames()[index]
    }
}
