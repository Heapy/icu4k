// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.number.LocalizedNumberFormatter
import io.github.heapy.icu4k.number.NumberFormatter
import io.github.heapy.icu4k.util.ULocale
import java.io.Externalizable
import java.io.ObjectInput
import java.io.ObjectOutput
import java.text.AttributedCharacterIterator
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition

actual typealias LocalizedNumberFormatterAsFormat = LocalizedNumberFormatterAsFormatJvm

/** Java Format bridge; serialization uses the source language-tag/skeleton externalizable proxy. */
open class LocalizedNumberFormatterAsFormatJvm(private @Transient val formatter: LocalizedNumberFormatter?, private @Transient val locale: ULocale?) : Format() {
    private @Transient val core = LocalizedNumberFormatterAsFormatCore(formatter)
    override fun format(obj: Any?, toAppendTo: StringBuffer?, pos: FieldPosition?): StringBuffer {
        core.format(obj, toAppendTo, { toAppendTo!!.length }, pos)
        return toAppendTo!!
    }
    open fun format(input: Any?, output: StringBuilder?, position: FieldPosition?): StringBuilder {
        core.format(input, output, { output!!.length }, position)
        return output!!
    }
    override fun formatToCharacterIterator(input: Any?): AttributedCharacterIterator = core.iterator(input)
    override fun parseObject(source: String?, pos: ParsePosition?): Any? = throw UnsupportedOperationException()
    open fun parseObject(source: String?, position: io.github.heapy.icu4k.text.ParsePosition?): Any? = throw UnsupportedOperationException()
    open fun getNumberFormatter(): LocalizedNumberFormatter? = formatter
    override fun hashCode(): Int = formatter!!.hashCode()
    override fun equals(other: Any?): Boolean = localizedFormatEquals(this, formatter, other)
    private fun writeReplace(): Any {
        val proxy = Proxy()
        proxy.languageTag = locale!!.toLanguageTag()
        proxy.skeleton = formatter!!.toSkeleton()
        return proxy
    }
    class Proxy : Externalizable {
        internal var languageTag: String? = null
        internal var skeleton: String? = null
        override fun writeExternal(out: ObjectOutput) { out.writeByte(0); out.writeUTF(languageTag); out.writeUTF(skeleton) }
        override fun readExternal(input: ObjectInput) { input.readByte(); languageTag = input.readUTF(); skeleton = input.readUTF() }
        private fun readResolve(): Any = NumberFormatter.forSkeleton(skeleton).locale(ULocale.forLanguageTag(languageTag!!)).toFormat()
        private companion object { private const val serialVersionUID = 1L }
    }
    private companion object { private const val serialVersionUID = 1L }
}
