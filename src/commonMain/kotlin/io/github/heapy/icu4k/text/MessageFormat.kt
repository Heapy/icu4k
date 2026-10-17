// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.*

/** ICU message patterns with numbered/named arguments, choices, plurals and selects. */
open class MessageFormat : UFormat {
    @kotlin.jvm.Transient internal var ulocale: ULocale? = null
    @kotlin.jvm.Transient internal var msgPattern: MessagePattern? = null
    @kotlin.jvm.Transient internal var formats: MessageFormatCacheCore<Format> = newCache()
    @kotlin.jvm.Transient private var stockDate: DateFormat? = null
    @kotlin.jvm.Transient private var stockNumber: NumberFormat? = null
    @kotlin.jvm.Transient private var engine: MessageFormatFormatCore = newEngine()
    @kotlin.jvm.Transient private var parser: MessageFormatParseCore = newParser()

    constructor(pattern: String?) : this(pattern, ULocale.getDefault(ULocale.Category.FORMAT))
    constructor(pattern: String?, locale: ULocale?) : super() { ulocale = locale; applyPattern(pattern) }
    protected constructor(other: MessageFormat, @Suppress("UNUSED_PARAMETER") copyMarker: Unit) : super() {
        ulocale = other.ulocale
        other.copyLocaleTo(this)
    }
    private fun newCache(): MessageFormatCacheCore<Format> = MessageFormatCacheCore<Format>({ msgPattern }) { type, style ->
        MessageFormatStyleCore(object : MessageFormatStyleCore.Owner {
            override val locale get() = ulocale
            override fun dateStyle(date: Boolean, style: Int, locale: ULocale?): Any = SimpleDateFormat.normalDateStyle(date, style, locale)
            override fun dateSkeleton(skeleton: String, locale: ULocale?): Any = SimpleDateFormat(SimpleDateFormat.bestPattern(skeleton, locale), locale)
            override fun datePattern(pattern: String, locale: ULocale?): Any = SimpleDateFormat(pattern, locale)
        }).create(type, style) as Format?
    }
    private fun newEngine(): MessageFormatFormatCore = MessageFormatFormatCore(object : MessageFormatFormatCore.Owner {
        override val messagePattern get() = msgPattern
        override val locale get() = ulocale
        override val argumentField get() = Field.ARGUMENT
        override val assertionsEnabled get() = messageFormatAssertionsEnabled()
        override val cachedFormatters get() = formats.cachedFormatters?.mapValues { (_, format) -> format?.let(::messageFormatFormatter) }
        override fun stockNumberFormatter(): MessageFormatFormatCore.Formatter {
            if (stockNumber == null) stockNumber = NumberFormat.getInstance(ulocale)
            return messageFormatFormatter(stockNumber!!)
        }
        override fun stockDateFormatter(): MessageFormatFormatCore.Formatter {
            if (stockDate == null) stockDate = SimpleDateFormat.normalDateTimeStyle(DateFormat.SHORT, DateFormat.SHORT, ulocale)
            return messageFormatFormatter(stockDate!!)
        }
        override fun isDateLike(value: Any): Boolean = messageFormatDateLike(value)
        override fun child(pattern: String, mode: MessagePattern.ApostropheMode?): MessageFormatFormatCore {
            val child = MessageFormat(if (mode == null) pattern else "", ulocale)
            if (mode != null) child.applyPattern(pattern, mode)
            return child.engine
        }
    })
    private fun newParser(): MessageFormatParseCore = MessageFormatParseCore(object : MessageFormatParseCore.Owner {
        override val messagePattern get() = msgPattern
        override val cachedParsers get() = formats.cachedFormatters?.mapValues { (_, format) ->
            format?.let { value -> MessageFormatParseCore.Parser { text, position -> messageFormatParseFormat(value, text, position) } }
        }
    })
    open fun setLocale(locale: ULocale?) {
        val pattern = toPattern()
        ulocale = locale
        stockDate = null; stockNumber = null
        engine.resetPluralProviders()
        applyPattern(pattern)
    }
    open fun getLocale(): MessageFormatLocale = messageFormatLocale(ulocale)
    open fun getULocale(): ULocale? = ulocale
    open fun applyPattern(pattern: String?) {
        try {
            if (msgPattern == null) msgPattern = MessagePattern(pattern) else msgPattern!!.parse(pattern)
            formats.cacheExplicitFormats()
        } catch (e: RuntimeException) {
            msgPattern?.clear(); formats.clear()
            throw e
        }
    }
    open fun applyPattern(pattern: String?, mode: MessagePattern.ApostropheMode?) {
        if (msgPattern == null) msgPattern = MessagePattern(mode)
        else if (mode != msgPattern!!.getApostropheMode()) msgPattern!!.clearPatternAndSetApostropheMode(mode)
        applyPattern(pattern)
    }
    open fun getApostropheMode(): MessagePattern.ApostropheMode? {
        if (msgPattern == null) msgPattern = MessagePattern()
        return msgPattern!!.getApostropheMode()
    }
    open fun toPattern(): String {
        if (formats.customFormatArgStarts != null) throw IllegalStateException("toPattern() is not supported after custom Format objects have been set via setFormat() or similar APIs")
        return msgPattern?.getPatternString() ?: ""
    }
    open fun setFormatsByArgumentIndex(values: Array<out Format?>?) = formats.setFormatsByArgumentIndex(values?.asList())
    open fun setFormatsByArgumentName(values: Map<String, Format?>?) = formats.setFormatsByArgumentName(values)
    open fun setFormats(values: Array<out Format?>?) = formats.setFormats(values?.asList())
    open fun setFormatByArgumentIndex(index: Int, format: Format?) = formats.setFormatByArgumentIndex(index, format)
    open fun setFormatByArgumentName(name: String?, format: Format?) = formats.setFormatByArgumentName(name, format)
    open fun setFormat(index: Int, format: Format?) = formats.setFormat(index, format)
    open fun getFormatsByArgumentIndex(): Array<Format?> = formats.formatsByArgumentIndex().toTypedArray()
    open fun getFormats(): Array<Format?> = formats.formats().toTypedArray()
    open fun getArgumentNames(): MutableSet<String> = formats.argumentNames()
    open fun getFormatByArgumentName(name: String?): Format? = formats.formatByArgumentName(name)
    open fun usesNamedArguments(): Boolean = msgPattern!!.hasNamedArguments()
    fun format(arguments: Array<out Any?>?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        val dest = MessageFormatFormatCore.Destination(output!!)
        engine.format(arguments, null, dest, position)
        return output
    }
    fun format(arguments: Map<String, Any?>?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        val dest = MessageFormatFormatCore.Destination(output!!)
        engine.format(null, arguments, dest, position)
        return output
    }
    final override fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        engine.format(input, MessageFormatFormatCore.Destination(output!!), position)
        return output
    }
    override fun formatToCharacterIterator(input: Any?): FormatAttributedCharacterIterator = engine.iterator(input)
    open fun parse(source: String?, position: ParsePosition?): Array<Any?>? = parser.parse(source, position)
    open fun parseToMap(source: String?, position: ParsePosition?): MutableMap<String, Any?>? = parser.parseToMap(source, position)
    open fun parse(source: String?): Array<Any?>? {
        val position = ParsePosition(0)
        val result = parse(source, position)
        if (position.index == 0) throw ParseException("MessageFormat parse error!", position.errorIndex)
        return result
    }
    open fun parseToMap(source: String?): MutableMap<String, Any?> = parser.parseToMap(source)
    override fun parseObject(source: String?, position: ParsePosition?): Any? = if (!msgPattern!!.hasNamedArguments()) parse(source, position) else parseToMap(source, position)
    public override fun clone(): MessageFormat {
        val copy = cloneHost() as? MessageFormat ?: cloneNative()
        copy.formats = copy.newCache().also { it.copyStateFrom(formats) }
        copy.msgPattern = msgPattern?.clone()
        copy.stockDate = stockDate?.clone(); copy.stockNumber = stockNumber?.clone()
        copy.engine = copy.newEngine(); copy.parser = copy.newParser()
        return copy
    }
    protected open fun cloneNative(): MessageFormat = MessageFormat(this, Unit)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as MessageFormat
        return ulocale == other.ulocale && msgPattern == other.msgPattern && formats.cachedFormatters == other.formats.cachedFormatters && formats.customFormatArgStarts == other.formats.customFormatArgStarts
    }
    override fun hashCode(): Int = msgPattern!!.getPatternString()!!.hashCode()
    open class Field protected constructor(name: String?) : FormatField(name) {
        protected override fun readResolve(): Any {
            if (this::class != Field::class) invalidMessageFormatField("A subclass of MessageFormat.Field must implement readResolve.")
            if (getName()!!.equals(ARGUMENT.getName())) return ARGUMENT
            invalidMessageFormatField("Unknown attribute name.")
        }
        companion object {
            private const val serialVersionUID = 7510380454602616157L
            @kotlin.jvm.JvmField val ARGUMENT = Field("message argument field")
        }
    }
    private fun writeObject(output: PluralObjectOutputStream) = messageFormatWrite(output, this)
    private fun readObject(input: PluralObjectInputStream) {
        ulocale = null; msgPattern = null; stockDate = null; stockNumber = null
        formats = newCache(); engine = newEngine(); parser = newParser()
        messageFormatRead(input, this)
    }
    companion object {
        private const val serialVersionUID = 7136212545847378652L
        @kotlin.jvm.JvmStatic fun format(pattern: String?, vararg arguments: Any?): String = MessageFormat(pattern).format(arguments, StringBuilder(), null).toString()
        @kotlin.jvm.JvmStatic fun format(pattern: String?, arguments: Map<String, Any?>?): String = MessageFormat(pattern).format(arguments, StringBuilder(), null).toString()
        @kotlin.jvm.JvmStatic fun autoQuoteApostrophe(pattern: String?): String {
            val text = pattern!!
            val output = StringBuilder(text.length * 2)
            var state = 0; var braces = 0
            for (ch in text) {
                when (state) {
                    0 -> when (ch) { '\'' -> state = 1; '{' -> { state = 3; braces++ } }
                    1 -> when (ch) { '\'' -> state = 0; '{', '}' -> state = 2; else -> { output.append('\''); state = 0 } }
                    2 -> if (ch == '\'') state = 0
                    3 -> when (ch) { '{' -> braces++; '}' -> { if (--braces == 0) state = 0 } }
                }
                output.append(ch)
            }
            if (state == 1 || state == 2) output.append('\'')
            return output.toString()
        }
    }
}
/** JVM Locale identity; portable callers normally use getULocale(). */
expect class MessageFormatLocale
internal expect fun messageFormatLocale(locale: ULocale?): MessageFormatLocale
internal expect fun messageFormatFormatter(format: Format): MessageFormatFormatCore.Formatter
internal expect fun messageFormatParseFormat(format: Format, source: String?, position: ParsePosition?): Any?
internal expect fun messageFormatDateLike(value: Any): Boolean
internal expect fun messageFormatAssertionsEnabled(): Boolean
internal expect fun invalidMessageFormatField(message: String): Nothing
internal expect fun messageFormatWrite(output: PluralObjectOutputStream, owner: MessageFormat)
internal expect fun messageFormatRead(input: PluralObjectInputStream, owner: MessageFormat)
