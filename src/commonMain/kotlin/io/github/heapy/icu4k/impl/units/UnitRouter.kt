// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.IllegalIcuArgumentException
import io.github.heapy.icu4k.util.ULocale

/** Routing core from UnitsRouter; public MeasureUnit and MicroProps adapters remain separate. */
internal class UnitRouter(input: MeasureUnitImpl, locale: ULocale?, usage: String?) {
    constructor(input: String?, locale: ULocale?, usage: String?) : this(MeasureUnitImpl.forIdentifier(input)!!, locale, usage)
    private class Preference(val target: MeasureUnitImpl, val converter: ComplexUnitConverter, val limit: UnitDecimal, val skeleton: String)
    private val preferences = ArrayList<Preference>()
    private val outputUnits = ArrayList<MeasureUnitImpl>()
    init {
        val data = UnitConversionData()
        val category = data.getCategory(input)
        val selected = UnitPreferences().getPreferencesFor(category, usage, locale, data)!!
        for (preference in selected) {
            val target = MeasureUnitImpl.UnitsParser.parseForIdentifier(preference.getUnit())!!
            val skeleton = preference.getSkeleton()
            if (skeleton.isNotEmpty() && !skeleton.startsWith("precision-increment")) throw AssertionError("Only `precision-increment` is allowed")
            target.serialize()
            outputUnits.add(target.copy())
            preferences.add(Preference(target, ComplexUnitConverter(input, target, data), preference.getGeq(), skeleton))
        }
    }
    sealed class Precision {
        class Increment(val value: UnitDecimal) : Precision()
        data object IntegerMinTwo : Precision()
    }
    sealed class Rounder {
        class Resolved(val apply: (UnitDecimal) -> UnitDecimal) : Rounder()
        class Unresolved(val resolve: (Precision) -> Resolved) : Rounder()
        companion object {
            fun automatic(mode: UnitDecimal.Rounding = UnitDecimal.Rounding.HALF_EVEN): Rounder = Unresolved { precision ->
                Resolved { value ->
                    when (precision) {
                        is Precision.Increment -> value.divideToScale(precision.value, 0, mode).multiply(precision.value).stripTrailingZeros()
                        Precision.IntegerMinTwo -> {
                            val scale = maxOf(0L, value.scale.toLong() - value.precision + 2)
                            if (scale > Int.MAX_VALUE) throw ArithmeticException("Underflow")
                            value.setScale(scale.toInt(), mode).stripTrailingZeros()
                        }
                    }
                }
            }
        }
    }
    class Context(var rounder: Rounder?)
    class Result(val conversion: ComplexUnitConverter.Result, val outputUnit: MeasureUnitImpl)
    fun getOutputUnits(): MutableList<MeasureUnitImpl> = outputUnits
    fun route(quantity: UnitDecimal, context: Context? = null): Result {
        var rounder = context?.rounder
        var selected: Preference? = null
        for (preference in preferences) {
            selected = preference
            if (preference.converter.greaterThanOrEqual(quantity.abs(), preference.limit)) break
        }
        val preference = selected!!
        if (rounder is Rounder.Unresolved) rounder = rounder.resolve(if (preference.skeleton.isEmpty()) Precision.IntegerMinTwo else parseSkeleton(preference.skeleton))
        if (context != null) context.rounder = rounder
        return Result(preference.converter.convert(quantity, (rounder as Rounder.Resolved?)?.apply), preference.target)
    }
    companion object {
        fun parseSkeleton(skeleton: String): Precision.Increment {
            val prefix = "precision-increment/"
            if (!skeleton.startsWith(prefix)) throw IllegalIcuArgumentException("precisionSkeleton is only precision-increment")
            val increment = UnitDecimal(skeleton.substring(prefix.length))
            if (increment <= UnitDecimal.ZERO) throw IllegalArgumentException("Rounding increment must be positive")
            return Precision.Increment(increment)
        }
    }
}
