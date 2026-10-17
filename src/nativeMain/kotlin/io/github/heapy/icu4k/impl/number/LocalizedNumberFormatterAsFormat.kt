// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.number.LocalizedNumberFormatter
import io.github.heapy.icu4k.text.FormatAttributedCharacterIterator
import io.github.heapy.icu4k.text.FormatFieldPosition
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.ULocale

actual open class LocalizedNumberFormatterAsFormat actual constructor(private val formatter: LocalizedNumberFormatter?, private val locale: ULocale?) : io.github.heapy.icu4k.text.PortableFormat() {
    private val core = LocalizedNumberFormatterAsFormatCore(formatter)
    actual fun format(input: Any?): String {
        val output = StringBuilder()
        format(input, output, FormatFieldPosition(0))
        return output.toString()
    }
    actual open fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        core.format(input, output, { output!!.length }, position)
        return output!!
    }
    actual open fun formatToCharacterIterator(input: Any?): FormatAttributedCharacterIterator = core.iterator(input)
    actual open fun parseObject(source: String?, position: ParsePosition?): Any? = throw UnsupportedOperationException()
    actual open fun getNumberFormatter(): LocalizedNumberFormatter? = formatter
    actual override fun hashCode(): Int = formatter!!.hashCode()
    actual override fun equals(other: Any?): Boolean = localizedFormatEquals(this, formatter, other)
}
