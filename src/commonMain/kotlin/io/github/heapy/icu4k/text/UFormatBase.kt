// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ICUSerializable
import io.github.heapy.icu4k.util.ParseException

/** JVM java.text.Format bridge; portable overloads use StringBuilder and ParsePosition. */
expect abstract class UFormatBase() : Format {
    fun format(input: Any?): String
    abstract fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder
    abstract fun parseObject(source: String?, position: ParsePosition?): Any?
    open fun parseObject(source: String?): Any?
    open fun formatToCharacterIterator(input: Any?): FormatAttributedCharacterIterator
    open fun clone(): Any
    protected fun cloneHost(): Any?
}

/** Default java.text.Format protocol for platforms without the JDK. */
abstract class PortableUFormatBase : PortableFormat() {
    fun format(input: Any?): String = format(input, StringBuilder(), FormatFieldPosition(0)).toString()
    abstract fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder
    abstract fun parseObject(source: String?, position: ParsePosition?): Any?
    open fun parseObject(source: String?): Any? {
        val position = ParsePosition(0)
        val result = parseObject(source, position)
        if (position.index == 0) throw ParseException("Format.parseObject(String) failed", position.errorIndex)
        return result
    }
    open fun formatToCharacterIterator(input: Any?): FormatAttributedCharacterIterator = attributedCharacterIterator(format(input), emptyList())
    open fun clone(): Any = cloneHost() ?: throw UnsupportedOperationException("Native format subclasses must supply a typed clone")
    protected fun cloneHost(): Any? = clonePortableUFormat(this)
}
internal expect fun clonePortableUFormat(value: Any): Any?
