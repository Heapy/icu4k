// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.MessageFormatFormatCore
import io.github.heapy.icu4k.impl.MessageFormatPatternCore
import io.github.heapy.icu4k.util.*
import java.util.Locale

actual typealias MessageFormatLocale = Locale
fun MessageFormat(pattern: String?, locale: Locale?): MessageFormat = MessageFormat(pattern, ULocale.forLocale(locale))
fun MessageFormat.setLocale(locale: Locale?) = setLocale(ULocale.forLocale(locale))
internal actual fun messageFormatLocale(locale: ULocale?): MessageFormatLocale = locale!!.toLocale()
internal actual fun messageFormatFormatter(format: Format): MessageFormatFormatCore.Formatter = object : MessageFormatFormatCore.Formatter {
    override val complex get() = format is java.text.ChoiceFormat || format is PluralFormat || format is SelectFormat
    override val decimal get() = format is DecimalFormat
    override fun format(value: Any?): String = format.format(value)
    override fun iterator(value: Any?): FormatAttributedCharacterIterator = format.formatToCharacterIterator(value)
    override fun fixedDecimal(value: Double): PluralRules.IFixedDecimal? = (format as DecimalFormat).getFixedDecimal(value)
}
internal actual fun messageFormatParseFormat(format: Format, source: String?, position: ParsePosition?): Any? {
    val host = position?.let { java.text.ParsePosition(it.index).also { p -> p.errorIndex = it.errorIndex } }
    try { return format.parseObject(source, host) }
    finally { if (position != null && host != null) { position.index = host.index; position.errorIndex = host.errorIndex } }
}
internal actual fun messageFormatDateLike(value: Any): Boolean = value is java.util.Date || value is java.time.temporal.Temporal || value is java.time.DayOfWeek || value is java.time.Month
internal actual fun messageFormatAssertionsEnabled(): Boolean = MessageFormat::class.java.desiredAssertionStatus()
internal actual fun invalidMessageFormatField(message: String): Nothing = throw java.io.InvalidObjectException(message)
internal actual fun messageFormatWrite(output: PluralObjectOutputStream, owner: MessageFormat) {
    output.defaultWriteObject()
    output.writeObject(owner.ulocale!!.toLanguageTag())
    if (owner.msgPattern == null) owner.msgPattern = MessagePattern()
    output.writeObject(owner.msgPattern!!.getApostropheMode())
    output.writeObject(owner.msgPattern!!.getPatternString())
    val custom = owner.formats.customFormatArgStarts
    if (custom.isNullOrEmpty()) output.writeInt(0)
    else {
        output.writeInt(custom.size)
        var ordinal = 0; var part = 0
        while (true) {
            part = MessageFormatPatternCore.nextTopLevelArgStart(owner.msgPattern!!, part)
            if (part < 0) break
            if (custom.contains(part)) { output.writeInt(ordinal); output.writeObject(owner.formats.cachedFormatters!![part]) }
            ordinal++
        }
    }
    output.writeInt(0)
}
internal actual fun messageFormatRead(input: PluralObjectInputStream, owner: MessageFormat) {
    input.defaultReadObject()
    owner.ulocale = ULocale.forLanguageTag((input.readObject() as String?)!!)
    val mode = input.readObject() as MessagePattern.ApostropheMode?
    if (owner.msgPattern == null || mode != owner.msgPattern!!.getApostropheMode()) owner.msgPattern = MessagePattern(mode)
    val pattern = input.readObject() as String?
    if (pattern != null) owner.applyPattern(pattern)
    var count = input.readInt()
    while (count-- > 0) { val index = input.readInt(); val format = input.readObject() as Format?; owner.setFormat(index, format) }
    count = input.readInt()
    while (count-- > 0) { input.readInt(); input.readObject() }
}
