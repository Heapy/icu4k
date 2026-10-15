// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmStatic

/** Selects a name handler after the parent resolves an output unit. */
open class LongNameMultiplexer(private val parent: MicroPropsGenerator?) : MicroPropsGenerator {
    fun interface ParentlessMicroPropsGenerator {
        fun processQuantityWithMicros(quantity: DecimalQuantity?, micros: MicroProps?): MicroProps?
    }
    private var handlers: MutableList<ParentlessMicroPropsGenerator>? = null
    private var units: MutableList<MeasureUnit?>? = null
    override fun processQuantity(quantity: DecimalQuantity?): MicroProps? {
        val micros = parent!!.processQuantity(quantity)
        var i = 0
        while (i < handlers!!.size) {
            if (units!![i]!!.equals(micros!!.outputUnit)) return handlers!![i].processQuantityWithMicros(quantity, micros)
            i++
        }
        throw AssertionError(" We shouldn't receive any outputUnit for which we haven't already got a LongNameHandler")
    }
    companion object {
        @JvmStatic fun forMeasureUnits(locale: ULocale?, input: List<MeasureUnit?>?, width: UnitWidth?, case: String?, rules: PluralRules?, parent: MicroPropsGenerator?): LongNameMultiplexer {
            val result = LongNameMultiplexer(parent)
            assertLongNameMultiplexer { input!!.size > 0 }
            result.units = ArrayList()
            result.handlers = ArrayList()
            var i = 0
            while (i < input!!.size) {
                val unit = input[i]
                result.units!!.add(unit)
                val handler = if (unit!!.getComplexity() == MeasureUnit.Complexity.MIXED)
                    MixedUnitLongNameHandler.forMeasureUnit(locale, unit, width, case, rules, null)
                else LongNameHandler.forMeasureUnit(locale, unit, width, case, rules, null)
                result.handlers!!.add(handler)
                i++
            }
            return result
        }
    }
}
internal expect fun assertLongNameMultiplexer(condition: () -> Boolean)
