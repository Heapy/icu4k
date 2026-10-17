// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.MessageFormatFormatCore
import io.github.heapy.icu4k.impl.number.LocalizedNumberFormatterAsFormat
import io.github.heapy.icu4k.util.*

actual typealias MessageFormatLocale = ULocale
internal actual fun messageFormatLocale(locale: ULocale?): MessageFormatLocale = locale!!
internal actual fun messageFormatFormatter(format: Format): MessageFormatFormatCore.Formatter = when (format) {
    is UFormatBase -> MessageFormatFormatCore.CommonFormatter(format)
    is LocalizedNumberFormatterAsFormat -> object : MessageFormatFormatCore.Formatter {
        override fun format(value: Any?): String = format.format(value)
        override fun iterator(value: Any?): FormatAttributedCharacterIterator = format.formatToCharacterIterator(value)
    }
    else -> throw ClassCastException("Unknown portable Format")
}
internal actual fun messageFormatParseFormat(format: Format, source: String?, position: ParsePosition?): Any? = when (format) {
    is UFormatBase -> format.parseObject(source, position)
    is LocalizedNumberFormatterAsFormat -> format.parseObject(source, position)
    else -> throw ClassCastException("Unknown portable Format")
}
internal actual fun messageFormatDateLike(value: Any): Boolean = value is Date
internal actual fun messageFormatAssertionsEnabled(): Boolean = false
internal actual fun invalidMessageFormatField(message: String): Nothing = throw IllegalArgumentException(message)
internal actual fun messageFormatWrite(output: PluralObjectOutputStream, owner: MessageFormat): Unit = throw UnsupportedOperationException("Java serialization is only available on JVM")
internal actual fun messageFormatRead(input: PluralObjectInputStream, owner: MessageFormat): Unit = throw UnsupportedOperationException("Java serialization is only available on JVM")
