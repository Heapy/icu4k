// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.ComplexUnitsConverter
import io.github.heapy.icu4k.impl.units.MeasureUnitImpl
import io.github.heapy.icu4k.impl.units.UnitsRouter
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmStatic

/** Measurement preferences stage, retaining the selected measure list and fractional quantity. */
open class UsagePrefsHandler(locale: ULocale?, inputUnit: MeasureUnit?, usage: String?, private val parent: MicroPropsGenerator?) : MicroPropsGenerator {
    private val router: UnitsRouter
    init {
        assertUsagePrefsHandler { parent != null }
        router = UnitsRouter(MeasureUnitImpl.forIdentifier(inputUnit!!.getIdentifier()), locale, usage)
    }
    open fun getOutputUnits(): MutableList<MeasureUnit> = router.getOutputUnits()
    override fun processQuantity(quantity: DecimalQuantity?): MicroProps {
        val micros = parent!!.processQuantity(quantity)
        quantity!!.roundToInfinity()
        val routed = router.route(quantity.toBigDecimal(), micros)
        val unit = routed.outputUnit!!.build()
        micros!!.outputUnit = unit
        applyMixedMeasuresToMicros(routed.complexConverterResult, quantity, micros)
        return micros
    }
    companion object {
        @JvmStatic protected fun mixedMeasuresToMicros(result: ComplexUnitsConverter.ComplexConverterResult?, quantity: DecimalQuantity?, outMicros: MicroProps?) =
            applyMixedMeasuresToMicros(result, quantity, outMicros)
    }
}
internal fun applyMixedMeasuresToMicros(result: ComplexUnitsConverter.ComplexConverterResult?, quantity: DecimalQuantity?, outMicros: MicroProps?) {
    val measures = result!!.measures
    outMicros!!.mixedMeasures = measures
    outMicros.indexOfQuantity = result.indexOfQuantity
    val number = outMicros.mixedMeasures!![outMicros.indexOfQuantity]!!.getNumber() as BigDecimal?
    quantity!!.setToBigDecimal(number)
}
internal expect fun assertUsagePrefsHandler(condition: () -> Boolean)
