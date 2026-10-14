// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Immutable locale-aware list formatter. */
class ListFormatter private constructor(two: String, private val start: String, private val middle: String, end: String, private val locale: ULocale?) {
    typealias Field = ListFormatField
    typealias SpanField = ListSpanField
    enum class Type { AND, OR, UNITS }
    enum class Width { WIDE, SHORT, NARROW }
    private val handler = createPatternHandler(two, end)
    @Deprecated("This API is ICU internal only.")
    constructor(two: String, start: String, middle: String, end: String) : this(compile(two), compile(start), compile(middle), compile(end), null)

    class FormattedList internal constructor(private val string: FormattedStringBuilder) : FormattedValue {
        override val length: Int get() = string.length
        override fun get(index: Int): Char = string[index]
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = string.subString(startIndex, endIndex)
        override fun toString(): String = string.toString()
        override fun <A : Appendable> appendTo(appendable: A): A = Utility.appendTo(string, appendable)
        override fun nextPosition(position: ConstrainedFieldPosition): Boolean = FormattedValueStringBuilderImpl.nextPosition(string, position, null)
        override fun toCharacterIterator(): FormatAttributedCharacterIterator = FormattedValueStringBuilderImpl.toCharacterIterator(string, null)
    }
    fun format(vararg items: Any?): String = format(items.asList())
    fun format(items: Collection<Any?>): String = formatImpl(items, false).toString()
    fun formatToValue(vararg items: Any?): FormattedList = formatToValue(items.asList())
    fun formatToValue(items: Collection<Any?>): FormattedList = formatImpl(items, true).toValue()
    internal fun formatImpl(items: Collection<Any?>, needsFields: Boolean): FormattedListBuilder {
        val iterator = items.iterator()
        val count = items.size
        when (count) {
            0 -> return FormattedListBuilder("", needsFields)
            1 -> return FormattedListBuilder(iterator.next(), needsFields)
            2 -> {
                val first = iterator.next()
                val second = iterator.next()
                return FormattedListBuilder(first, needsFields).append(handler.two(second.toString()), second, 1)
            }
        }
        val builder = FormattedListBuilder(iterator.next(), needsFields)
        builder.append(start, iterator.next(), 1)
        for (index in 2 until count - 1) builder.append(middle, iterator.next(), index)
        val last = iterator.next()
        return builder.append(handler.end(last.toString()), last, count - 1)
    }
    fun getPatternForNumItems(count: Int): String {
        if (count <= 0) throw IllegalArgumentException("count must be > 0")
        return format((0 until count).map { listPatternIndex(it) })
    }
    @Deprecated("This API is ICU internal only.")
    fun getLocale(): ULocale? = locale
    private interface PatternHandler { fun two(text: String): String; fun end(text: String): String }
    private class Handler(private val two: String, private val end: String, private val predicate: ((String) -> Boolean)? = null, private val thenTwo: String = two, private val thenEnd: String = end) : PatternHandler {
        override fun two(text: String): String = if (predicate?.invoke(text) == true) thenTwo else two
        override fun end(text: String): String = if (predicate?.invoke(text) == true) thenEnd else end
    }
    private fun createPatternHandler(two: String, end: String): PatternHandler {
        when (locale?.getLanguage()) {
            "es" -> {
                if (two == compiledY || end == compiledY) return Handler(two, end, ::changeToE, if (two == compiledY) compiledE else two, if (end == compiledY) compiledE else end)
                if (two == compiledO || end == compiledO) return Handler(two, end, ::changeToU, if (two == compiledO) compiledU else two, if (end == compiledO) compiledU else end)
            }
            "he", "iw" -> if (two == compiledVav || end == compiledVav) return Handler(two, end, ::changeToVavDash, if (two == compiledVav) compiledVavDash else two, if (end == compiledVav) compiledVavDash else end)
        }
        return Handler(two, end)
    }
    internal class FormattedListBuilder(start: Any?, var needsFields: Boolean) {
        private val string = FormattedStringBuilder()
        init { string.setAppendableField(ListFormatField.LITERAL); appendElement(start, 0) }
        fun append(compiledPattern: String, next: Any?, position: Int): FormattedListBuilder {
            string.setAppendIndex(0)
            var state = 0L
            while (true) {
                state = SimpleFormatterImpl.IterInternal.step(state, compiledPattern, string)
                if (state == SimpleFormatterImpl.IterInternal.DONE) break
                if (SimpleFormatterImpl.IterInternal.getArgIndex(state) == 0) string.setAppendIndex(string.length) else appendElement(next, position)
            }
            return this
        }
        private fun appendElement(element: Any?, position: Int) {
            val text = element!!.toString()
            if (needsFields) {
                val field = FormattedValueStringBuilderImpl.SpanFieldPlaceholder()
                field.spanField = ListSpanField.LIST_SPAN; field.normalField = ListFormatField.ELEMENT
                field.value = position; field.start = -1; field.length = text.length
                string.append(text, field)
            } else string.append(text, null)
        }
        fun appendTo(appendable: Appendable) { Utility.appendTo(string, appendable) }
        fun getOffset(index: Int): Int = FormattedValueStringBuilderImpl.findSpan(string, index)
        override fun toString(): String = string.toString()
        fun toValue(): FormattedList = FormattedList(string)
    }
    companion object {
        private fun compile(pattern: String): String = SimpleFormatterImpl.compileToStringMinMaxArguments(pattern, StringBuilder(), 2, 2)
        private val compiledY = compile("{0} y {1}")
        private val compiledE = compile("{0} e {1}")
        private val compiledO = compile("{0} o {1}")
        private val compiledU = compile("{0} u {1}")
        private val compiledVav = compile("{0} ו{1}")
        private val compiledVavDash = compile("{0} ו-{1}")
        private fun asciiLower(c: Char): Char = if (c in 'A'..'Z') c + 32 else c
        // Java CASE_INSENSITIVE here is ASCII-only; dot excludes its five line terminators.
        private fun hasLineTerminator(text: String): Boolean = text.any { it == '\n' || it == '\r' || it == '\u0085' || it == '\u2028' || it == '\u2029' }
        private fun changeToE(text: String): Boolean {
            if (text.isNotEmpty() && asciiLower(text[0]) == 'i') return !hasLineTerminator(text.substring(1))
            if (text.length < 2 || asciiLower(text[0]) != 'h' || asciiLower(text[1]) != 'i') return false
            return text.length == 2 || asciiLower(text[2]) !in "ae" && !hasLineTerminator(text.substring(3))
        }
        private fun changeToU(text: String): Boolean {
            if (hasLineTerminator(text)) return false
            return text == "11" || text.isNotEmpty() && (asciiLower(text[0]) == 'o' || text[0] == '8') || text.length >= 2 && asciiLower(text[0]) == 'h' && asciiLower(text[1]) == 'o'
        }
        private fun changeToVavDash(text: String): Boolean = text.isNotEmpty() && text[0] !in '\u0590'..'\u05ff' && !hasLineTerminator(text.substring(1))
        private val cache = SimpleCache<String, ListFormatter>()
        fun getInstance(): ListFormatter = getInstance(ULocale.getDefault(ULocale.Category.FORMAT))
        fun getInstance(locale: ULocale?): ListFormatter = getInstance(locale, Type.AND, Width.WIDE)
        fun getInstance(locale: ULocale?, type: Type?, width: Width?): ListFormatter {
            val style = typeWidthToStyleString(type, width)
            val key = "${locale!!}:$style"
            return cache.get(key) ?: load(locale, style).also { cache.put(key, it) }
        }
        internal fun typeWidthToStyleString(type: Type?, width: Width?): String {
            val prefix = when (type!!) { Type.AND -> "standard"; Type.OR -> "or"; Type.UNITS -> "unit" }
            return prefix + when (width!!) { Width.WIDE -> ""; Width.SHORT -> "-short"; Width.NARROW -> "-narrow" }
        }
        private fun load(locale: ULocale, style: String): ListFormatter {
            val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
            fun pattern(part: String): String = compile(bundle.getWithFallback("listPattern/$style/$part").getString())
            return ListFormatter(pattern("2"), pattern("start"), pattern("middle"), pattern("end"), locale)
        }
    }
}
internal expect fun listPatternIndex(index: Int): String
