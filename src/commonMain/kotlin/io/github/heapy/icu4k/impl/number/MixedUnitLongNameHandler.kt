// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.SimpleFormatterImpl
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.number.NumberFormatter
import io.github.heapy.icu4k.number.LocalizedNumberFormatter
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.text.ListFormatter
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.text.SimpleFormatter
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmStatic

/** Combines localized integral components and a placeholder for the fractional unit. */
open class MixedUnitLongNameHandler private constructor(private val rules: PluralRules?, private val parent: MicroPropsGenerator?) :
    MicroPropsGenerator, ModifierStore, LongNameMultiplexer.ParentlessMicroPropsGenerator {
    private var data: MutableList<Array<String?>>? = null
    private var integerFormatter: LocalizedNumberFormatter? = null
    private var listFormatter: ListFormatter? = null
    override fun processQuantity(quantity: DecimalQuantity?): MicroProps {
        assertMixedUnitLongNameHandler({ data!!.size > 1 }) { null }
        val micros = parent!!.processQuantity(quantity)
        val modifier = getMixedUnitModifier(quantity, micros)
        micros!!.modOuter = modifier
        return micros
    }
    override fun processQuantityWithMicros(quantity: DecimalQuantity?, micros: MicroProps?): MicroProps {
        assertMixedUnitLongNameHandler({ data!!.size > 1 }) { null }
        val modifier = getMixedUnitModifier(quantity, micros)
        micros!!.modOuter = modifier
        return micros
    }
    override fun getModifier(signum: Modifier.Signum?, plural: StandardPlural?): Modifier? {
        assertMixedUnitLongNameHandler({ false }) { "should be unreachable" }
        return null
    }
    private fun getMixedUnitModifier(quantity: DecimalQuantity?, micros: MicroProps?): Modifier {
        if (micros!!.mixedMeasures!!.size == 0) {
            assertMixedUnitLongNameHandler({ false }) { "Mixed unit: we must have more than one unit value" }
            throw UnsupportedOperationException()
        }
        val output = ArrayList<String>()
        var quantityPlural = StandardPlural.OTHER
        var i = 0
        while (i < micros.mixedMeasures!!.size) {
            if (i == micros.indexOfQuantity) {
                if (i > 0 && quantity!!.isNegative()) quantity.negate()
                quantityPlural = RoundingUtils.getPluralSafe(micros.rounder, rules, quantity)
                val format = LongNameData.getWithPlural(data!![i], quantityPlural)
                val compiled = SimpleFormatter.compileMinMaxArguments(format, 0, 1)
                output.add(compiled.format("{0}"))
                i++
                continue
            }
            val decimal = DecimalQuantity_DualStorageBCD(micros.mixedMeasures!![i]!!.getNumber())
            if (i > 0 && decimal.isNegative()) decimal.negate()
            val plural = RoundingUtils.getPluralSafe(micros.rounder, rules, decimal)
            val format = LongNameData.getWithPlural(data!![i], plural)
            val compiled = SimpleFormatter.compileMinMaxArguments(format, 0, 1)
            val buffer = FormattedStringBuilder()
            integerFormatter!!.formatImpl(decimal, buffer)
            output.add(compiled.format(buffer.toString()))
            i++
        }
        val pattern = listFormatter!!.format(output)
        val compiled = SimpleFormatterImpl.compileToStringMinMaxArguments(pattern, StringBuilder(), 0, 1)
        val params = Modifier.Parameters().also { it.obj = this; it.signum = Modifier.Signum.POS_ZERO; it.plural = quantityPlural }
        return SimpleModifier(compiled, null, false, params)
    }
    companion object {
        @JvmStatic fun forMeasureUnit(locale: ULocale?, unit: MeasureUnit?, width: UnitWidth?, case: String?, rules: PluralRules?, parent: MicroPropsGenerator?): MixedUnitLongNameHandler {
            assertMixedUnitLongNameHandler({ unit!!.getComplexity() == MeasureUnit.Complexity.MIXED }) { "MixedUnitLongNameHandler only supports MIXED units" }
            val result = MixedUnitLongNameHandler(rules, parent)
            val individuals = unit!!.splitToSingleUnits()
            result.data = ArrayList()
            var i = 0
            while (i < individuals.size) {
                val data = arrayOfNulls<String>(LongNameData.ARRAY_LENGTH)
                LongNameData.getMeasureData(locale, individuals[i], width, case, data)
                result.data!!.add(data)
                i++
            }
            var listWidth = ListFormatter.Width.SHORT
            if (width == UnitWidth.NARROW) listWidth = ListFormatter.Width.NARROW
            else if (width == UnitWidth.FULL_NAME) listWidth = ListFormatter.Width.WIDE
            result.listFormatter = ListFormatter.getInstance(locale, ListFormatter.Type.UNITS, listWidth)
            result.integerFormatter = NumberFormatter.withLocale(locale)
            return result
        }
    }
}
internal expect fun assertMixedUnitLongNameHandler(condition: () -> Boolean, message: () -> String?)
