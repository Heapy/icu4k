// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.MessagePattern
import io.github.heapy.icu4k.text.NumberFormat
import io.github.heapy.icu4k.text.ParsePosition

/** MessageFormat-backed numeric leap-month parser, including stock-format construction. */
internal class SimpleDateFormatNumericLeapMonthCore(
    pattern: String,
    createFormat: (String, String) -> MessageFormatParseCore.Parser?,
) : SimpleDateFormatParseCore.NumericLeapMonth, MessageFormatParseCore.Owner {
    override val messagePattern = MessagePattern(pattern)
    private val formats = MessageFormatCacheCore({ messagePattern }, createFormat)
    override val cachedParsers get() = formats.cachedFormatters
    private val parser = MessageFormatParseCore(this)

    init { formats.cacheExplicitFormats() }

    override fun setNumberFormat(format: NumberFormat?) {
        formats.setFormatByArgumentIndex(0, format?.let { value ->
            MessageFormatParseCore.Parser { source, position -> value.parseObject(source, position) }
        })
    }

    override fun parse(text: String, position: ParsePosition): Array<Any?>? = parser.parse(text, position)
}
