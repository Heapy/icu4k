// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

actual typealias UFormatBase = UFormatBaseJvm

/** Native Java callers retain StringBuffer and java.text.ParsePosition entry points. */
abstract class UFormatBaseJvm : java.text.Format() {
    abstract fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder
    abstract fun parseObject(source: String?, position: ParsePosition?): Any?
    override fun format(obj: Any?, toAppendTo: StringBuffer?, pos: java.text.FieldPosition?): StringBuffer {
        val portable = toAppendTo?.let { StringBuilder(it) }
        val result: StringBuilder
        try { result = format(obj, portable, pos) }
        finally { if (toAppendTo != null && portable != null) { toAppendTo.setLength(0); toAppendTo.append(portable) } }
        return if (result === portable) toAppendTo!! else StringBuffer(result)
    }
    override fun parseObject(source: String?, pos: java.text.ParsePosition?): Any? {
        val portable = pos?.let { ParsePosition(it.index).also { copy -> copy.errorIndex = it.errorIndex } }
        try { return parseObject(source, portable) }
        finally { if (pos != null && portable != null) { pos.index = portable.index; pos.errorIndex = portable.errorIndex } }
    }
    protected fun cloneHost(): Any? = super.clone()
    private companion object { private const val serialVersionUID = 1L }
}
// The JVM UFormat hierarchy uses Format.clone(); a standalone portable base uses typed copies.
internal actual fun clonePortableUFormat(value: Any): Any? = null
