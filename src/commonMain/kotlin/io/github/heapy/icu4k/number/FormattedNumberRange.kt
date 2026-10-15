// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.FormattedValueStringBuilderImpl
import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.number.NumberRangeFormatter.RangeIdentityResult
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.BigDecimal
import kotlin.jvm.JvmField

open class FormattedNumberRange internal constructor(
    @JvmField internal val string: FormattedStringBuilder?, @JvmField internal val quantity1: DecimalQuantity?,
    @JvmField internal val quantity2: DecimalQuantity?, @JvmField internal val identityResult: RangeIdentityResult?
) : FormattedValue {
    override fun toString(): String = string!!.toString()
    override fun <A : Appendable> appendTo(appendable: A): A = Utility.appendTo(string!!, appendable)
    override fun get(index: Int): Char = string!![index]
    override val length: Int get() = string!!.length
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = string!!.subString(startIndex, endIndex)
    override fun nextPosition(position: ConstrainedFieldPosition): Boolean = FormattedValueStringBuilderImpl.nextPosition(string!!, position, null)
    override fun toCharacterIterator(): FormatAttributedCharacterIterator = FormattedValueStringBuilderImpl.toCharacterIterator(string!!, null)
    open fun getFirstBigDecimal(): BigDecimal = quantity1!!.toBigDecimal()
    open fun getSecondBigDecimal(): BigDecimal = quantity2!!.toBigDecimal()
    open fun getIdentityResult(): RangeIdentityResult? = identityResult
    override fun hashCode(): Int = string!!.toCharArray().contentHashCode() xor string.toFieldArray().contentHashCode() xor
        quantity1!!.toBigDecimal().hashCode() xor quantity2!!.toBigDecimal().hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FormattedNumberRange) return false
        return string!!.contentEquals(other.string!!) && quantity1!!.toBigDecimal().equals(other.quantity1!!.toBigDecimal()) &&
            quantity2!!.toBigDecimal().equals(other.quantity2!!.toBigDecimal())
    }
    @Deprecated("This API is ICU internal only.")
    open fun getFirstFixedDecimal(): PluralRules.IFixedDecimal? = quantity1
    @Deprecated("This API is ICU internal only.")
    open fun getSecondFixedDecimal(): PluralRules.IFixedDecimal? = quantity2
}
