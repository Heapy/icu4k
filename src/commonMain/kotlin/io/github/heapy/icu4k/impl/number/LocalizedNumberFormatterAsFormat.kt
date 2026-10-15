// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.FormattedValueStringBuilderImpl
import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.number.LocalizedNumberFormatter
import io.github.heapy.icu4k.text.FormatAttributedCharacterIterator
import io.github.heapy.icu4k.text.FormatFieldPosition
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.ULocale

/** JVM instances are java.text.Format; portable callers can use the StringBuilder overload. */
expect open class LocalizedNumberFormatterAsFormat(formatter: LocalizedNumberFormatter?, locale: ULocale?) {
    fun format(input: Any?): String
    open fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder
    open fun formatToCharacterIterator(input: Any?): FormatAttributedCharacterIterator
    open fun parseObject(source: String?, position: ParsePosition?): Any?
    open fun getNumberFormatter(): LocalizedNumberFormatter?
    override fun hashCode(): Int
    override fun equals(other: Any?): Boolean
}

internal class LocalizedNumberFormatterAsFormatCore(private val formatter: LocalizedNumberFormatter?) {
    fun format(input: Any?, output: Appendable?, outputLength: () -> Int, position: FormatFieldPosition?) {
        if (input !is Number) throw IllegalArgumentException()
        val quantity = DecimalQuantity_DualStorageBCD(input)
        val buffer = FormattedStringBuilder()
        formatter!!.formatImpl(quantity, buffer)
        position!!.setBeginIndex(0)
        position.setEndIndex(0)
        val found = FormattedValueStringBuilderImpl.nextFieldPosition(buffer, position)
        if (found && outputLength() != 0) {
            position.setBeginIndex(position.getBeginIndex() + outputLength())
            position.setEndIndex(position.getEndIndex() + outputLength())
        }
        Utility.appendTo(buffer, output!!)
    }
    fun iterator(input: Any?): FormatAttributedCharacterIterator {
        if (input !is Number) throw IllegalArgumentException()
        return formatter!!.format(input).toCharacterIterator()
    }
}
internal fun localizedFormatEquals(self: LocalizedNumberFormatterAsFormat, formatter: LocalizedNumberFormatter?, other: Any?): Boolean {
    if (self === other) return true
    if (other !is LocalizedNumberFormatterAsFormat) return false
    return formatter!!.equals(other.getNumberFormatter())
}
