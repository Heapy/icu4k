// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.IllegalIcuArgumentException
import io.github.heapy.icu4k.impl.number.MicroProps
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.BigDecimals
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.decimalFromValue
import kotlin.jvm.JvmField

/** Locale/usage preference selection, conversion and preference-specific precision. */
open class UnitsRouter(inputUnit: MeasureUnitImpl?, locale: ULocale?, usage: String?) {
    constructor(inputUnitIdentifier: String?, locale: ULocale?, usage: String?) : this(MeasureUnitImpl.forIdentifier(inputUnitIdentifier), locale, usage)
    private val outputUnits = ArrayList<MeasureUnit>()
    private val preferences = ArrayList<ConverterPreference>()
    init {
        val data = UnitConversionData()
        val category = data.getCategory(inputUnit!!)
        val selected = UnitPreferences().getPreferencesFor(category, usage, locale, data)!!
        val rates = ConversionRates()
        for (preference in selected) {
            val target = MeasureUnitImpl.UnitsParser.parseForIdentifier(preference.getUnit())!!
            val skeleton = preference.getSkeleton()
            if (skeleton.isNotEmpty() && !skeleton.startsWith("precision-increment")) throw AssertionError("Only `precision-increment` is allowed")
            outputUnits.add(target.build())
            preferences.add(ConverterPreference(inputUnit, target, decimalFromValue(preference.getGeq()), skeleton, rates))
        }
    }
    open fun route(quantity: BigDecimal?, micros: MicroProps?): RouteResult {
        var rounder = micros?.rounder
        var selected: ConverterPreference? = null
        for (preference in preferences) {
            selected = preference
            if (preference.converter.greaterThanOrEqual(quantity!!.abs(), preference.limit)) break
        }
        assertUnitsRouter { selected != null }
        assertUnitsRouter { selected!!.precision != null }
        val preference = selected!!
        if (rounder is Precision.BogusRounder) {
            rounder = rounder.into(if (preference.precision!!.isNotEmpty()) parseSkeleton(preference.precision)
                else Precision.integer().withMinDigits(2))
        }
        if (micros != null) micros.rounder = rounder
        return RouteResult(preference.converter.convert(quantity, rounder), preference.targetUnit)
    }
    open fun getOutputUnits(): MutableList<MeasureUnit> = outputUnits
    open class ConverterPreference(source: MeasureUnitImpl?, @JvmField internal val targetUnit: MeasureUnitImpl?,
        @JvmField internal val limit: BigDecimal?, @JvmField internal val precision: String?, rates: ConversionRates?) {
        @JvmField internal val converter = ComplexUnitsConverter(source, targetUnit, rates)
        constructor(source: MeasureUnitImpl?, targetUnit: MeasureUnitImpl?, precision: String?, conversionRates: ConversionRates?) :
            this(source, targetUnit, BigDecimals.valueOf(Double.MIN_VALUE), precision, conversionRates)
    }
    open inner class RouteResult internal constructor(@JvmField val complexConverterResult: ComplexUnitsConverter.ComplexConverterResult,
        @JvmField val outputUnit: MeasureUnitImpl?)
    private companion object {
        fun parseSkeleton(skeleton: String): Precision {
            val prefix = "precision-increment/"
            if (!skeleton.startsWith(prefix)) throw IllegalIcuArgumentException("precisionSkeleton is only precision-increment")
            return Precision.increment(BigDecimal(skeleton.substring(prefix.length)))
        }
    }
}
internal expect fun assertUnitsRouter(condition: () -> Boolean)
