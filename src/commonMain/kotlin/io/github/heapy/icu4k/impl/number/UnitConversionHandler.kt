// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.ComplexUnitsConverter
import io.github.heapy.icu4k.impl.units.ConversionRates
import io.github.heapy.icu4k.impl.units.MeasureUnitImpl
import io.github.heapy.icu4k.util.MeasureUnit

/** Converts from the largest target unit and exposes the selected fractional component. */
open class UnitConversionHandler(private val targetUnit: MeasureUnit?, private val parent: MicroPropsGenerator?) : MicroPropsGenerator {
    private val converter = ComplexUnitsConverter(MeasureUnitImpl.forIdentifier(targetUnit!!.getIdentifier()), ConversionRates())
    override fun processQuantity(quantity: DecimalQuantity?): MicroProps {
        val result = parent!!.processQuantity(quantity)
        quantity!!.roundToInfinity()
        val value = quantity.toBigDecimal()
        val converted = converter.convert(value, result!!.rounder)
        result.outputUnit = targetUnit
        applyMixedMeasuresToMicros(converted, quantity, result)
        return result
    }
}
