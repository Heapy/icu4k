// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.FormattedValueStringBuilderImpl
import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.text.ConstrainedFieldPosition
import io.github.heapy.icu4k.text.DisplayOptions
import io.github.heapy.icu4k.text.FormatAttributedCharacterIterator
import io.github.heapy.icu4k.text.FormattedValue
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.MeasureUnit
import kotlin.jvm.JvmField

/** Formatted numeric text, attributes, rounded quantity and resolved output unit. */
open class FormattedNumber internal constructor(
    @JvmField internal val string: FormattedStringBuilder?,
    @JvmField internal val fq: DecimalQuantity?,
    @JvmField internal val outputUnit: MeasureUnit?,
    @JvmField internal val gender: String?
) : FormattedValue {
    override fun toString(): String = string!!.toString()
    override val length: Int get() = string!!.length
    override fun get(index: Int): Char = string!![index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = string!!.subString(startIndex, endIndex)
    override fun <A : Appendable> appendTo(appendable: A): A = Utility.appendTo(string!!, appendable)
    override fun nextPosition(position: ConstrainedFieldPosition): Boolean = FormattedValueStringBuilderImpl.nextPosition(string!!, position, null)
    override fun toCharacterIterator(): FormatAttributedCharacterIterator = FormattedValueStringBuilderImpl.toCharacterIterator(string!!, null)
    open fun toBigDecimal(): BigDecimal = fq!!.toBigDecimal()
    open fun getOutputUnit(): MeasureUnit? = outputUnit
    open fun getNounClass(): DisplayOptions.NounClass = DisplayOptions.NounClass.fromIdentifier(gender)
    @Deprecated("This API is for technology preview only.")
    open fun getGender(): String = gender ?: ""
    @Deprecated("This API is ICU internal only.")
    open fun getFixedDecimal(): PluralRules.IFixedDecimal? = fq
}
