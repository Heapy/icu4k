// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.impl.number.LocalizedNumberFormatterAsFormat
import io.github.heapy.icu4k.impl.number.MicroProps
import io.github.heapy.icu4k.util.Measure
import io.github.heapy.icu4k.util.MeasureUnit
import kotlin.concurrent.Volatile

/** Locale-bound immutable formatter with a shared compiled execution path and unit override cache. */
open class LocalizedNumberFormatter internal constructor(parent: NumberFormatterSettings<*>?, key: Int, value: Any?) :
    NumberFormatterSettings<LocalizedNumberFormatter>(parent, key, value) {
    private val execution = NumberFormatterExecution { resolve() }
    @Volatile private var savedWithUnit: LocalizedNumberFormatter? = null
    open fun format(input: Long): FormattedNumber = formatQuantity(DecimalQuantity_DualStorageBCD(input))
    open fun format(input: Double): FormattedNumber = formatQuantity(DecimalQuantity_DualStorageBCD(input))
    open fun format(input: Number?): FormattedNumber = formatQuantity(DecimalQuantity_DualStorageBCD(input))
    open fun format(input: Measure?): FormattedNumber {
        val quantity = DecimalQuantity_DualStorageBCD(input!!.getNumber())
        val unit = input.getUnit()
        val buffer = FormattedStringBuilder()
        val micros = formatImpl(quantity, unit, buffer)
        return FormattedNumber(buffer, quantity, micros.outputUnit, micros.gender)
    }
    open fun toFormat(): LocalizedNumberFormatterAsFormat = LocalizedNumberFormatterAsFormat(this, resolve().loc)
    open fun withoutLocale(): UnlocalizedNumberFormatter = UnlocalizedNumberFormatter(this, KEY_LOCALE, null)
    private fun formatQuantity(quantity: DecimalQuantity): FormattedNumber {
        val buffer = FormattedStringBuilder()
        val micros = formatImpl(quantity, buffer)
        return FormattedNumber(buffer, quantity, micros.outputUnit, micros.gender)
    }
    @Deprecated("This API is ICU internal only.")
    open fun formatImpl(quantity: DecimalQuantity?, output: FormattedStringBuilder?): MicroProps = execution.formatImpl(quantity, output)
    @Deprecated("This API is ICU internal only.")
    open fun formatImpl(quantity: DecimalQuantity?, unit: MeasureUnit?, output: FormattedStringBuilder?): MicroProps {
        if (objectsEqual(resolve().unit, unit)) return formatImpl(quantity, output)
        var withUnit = savedWithUnit
        if (withUnit == null || !objectsEqual(withUnit.resolve().unit, unit)) {
            withUnit = LocalizedNumberFormatter(this, KEY_UNIT, unit)
            savedWithUnit = withUnit
        }
        return withUnit.formatImpl(quantity, output)
    }
    @Deprecated("This API is ICU internal only.")
    open fun getAffixImpl(isPrefix: Boolean, isNegative: Boolean): String {
        val buffer = FormattedStringBuilder()
        val sign = (if (isNegative) -1 else 1).toByte()
        val prefixLength = execution.getPrefixSuffix(sign, StandardPlural.OTHER, buffer)
        return if (isPrefix) buffer.subSequence(0, prefixLength).toString() else buffer.subSequence(prefixLength, buffer.length).toString()
    }
    internal override fun create(key: Int, value: Any?): LocalizedNumberFormatter = LocalizedNumberFormatter(this, key, value)
    private fun objectsEqual(a: Any?, b: Any?): Boolean = a === b || (a != null && a.equals(b))
}
