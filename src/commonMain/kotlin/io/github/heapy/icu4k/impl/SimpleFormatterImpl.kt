// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ICUUncheckedIOException

/** ICU simple numbered-pattern compiler and UTF-16 literal-segment formatter. */
object SimpleFormatterImpl {
    private const val ARG_NUM_LIMIT = 0x100
    private const val MAX_SEGMENT_LENGTH = 0xffff - ARG_NUM_LIMIT
    private val common = arrayOf(
        "{0} {1}" to "\u0002\u0000\u0101 \u0001",
        "{0} ({1})" to "\u0002\u0000\u0102 (\u0001\u0101)",
        "{0}, {1}" to "\u0002\u0000\u0102, \u0001",
        "{0} – {1}" to "\u0002\u0000\u0103 – \u0001")
    fun compileToStringMinMaxArguments(pattern: CharSequence?, sb: StringBuilder?, min: Int, max: Int): String {
        if (min <= 2 && max >= 2) for ((raw, compiled) in common) if (raw.contentEquals(pattern)) return compiled
        val input = pattern!!
        val length = input.length
        val builder = sb!!
        builder.ensureCapacity(length)
        builder.setLength(1)
        var textLength = 0
        var maxArg = -1
        var inQuote = false
        var i = 0
        while (i < length) {
            var c = input[i++]
            if (c == '\'') {
                if (i < length && input[i].also { c = it } == '\'') i++
                else if (inQuote) { inQuote = false; continue }
                else if (c == '{' || c == '}') { i++; inQuote = true }
                else c = '\''
            } else if (!inQuote && c == '{') {
                if (textLength > 0) { builder[builder.length - textLength - 1] = (ARG_NUM_LIMIT + textLength).toChar(); textLength = 0 }
                var argument: Int
                val digit = if (i + 1 < length) input[i] - '0' else -1
                if (i + 1 < length && digit in 0..9 && input[i + 1] == '}') { argument = digit; i += 2 }
                else {
                    val start = i - 1
                    argument = -1
                    if (i < length && input[i++].also { c = it } in '1'..'9') {
                        argument = c - '0'
                        while (i < length && input[i++].also { c = it } in '0'..'9') {
                            argument = argument * 10 + (c - '0')
                            if (argument >= ARG_NUM_LIMIT) break
                        }
                    }
                    if (argument < 0 || c != '}') throw IllegalArgumentException("Argument syntax error in pattern \"$input\" at index $start: ${input.subSequence(start, i)}")
                }
                if (argument > maxArg) maxArg = argument
                builder.append(argument.toChar())
                continue
            }
            if (textLength == 0) builder.append('\uffff')
            builder.append(c)
            if (++textLength == MAX_SEGMENT_LENGTH) textLength = 0
        }
        if (textLength > 0) builder[builder.length - textLength - 1] = (ARG_NUM_LIMIT + textLength).toChar()
        val count = maxArg + 1
        if (count < min) throw IllegalArgumentException("Fewer than minimum $min arguments in pattern \"$input\"")
        if (count > max) throw IllegalArgumentException("More than maximum $max arguments in pattern \"$input\"")
        builder[0] = count.toChar()
        return builder.toString()
    }
    fun getArgumentLimit(compiledPattern: String): Int = compiledPattern[0].code
    fun formatCompiledPattern(compiledPattern: String, vararg values: CharSequence?): String =
        formatAndAppendValues(compiledPattern, StringBuilder(), null, values).toString()
    fun formatRawPattern(pattern: String, min: Int, max: Int, vararg values: CharSequence?): String {
        val sb = StringBuilder()
        val compiled = compileToStringMinMaxArguments(pattern, sb, min, max)
        sb.setLength(0)
        return formatAndAppendValues(compiled, sb, null, values).toString()
    }
    fun formatAndAppend(compiledPattern: String, appendTo: StringBuilder, offsets: IntArray?, vararg values: CharSequence?): StringBuilder =
        formatAndAppendValues(compiledPattern, appendTo, offsets, values)
    fun formatAndAppendValues(compiledPattern: String, appendTo: StringBuilder, offsets: IntArray?, values: Array<out CharSequence?>?): StringBuilder {
        if ((values?.size ?: 0) < getArgumentLimit(compiledPattern)) throw IllegalArgumentException("Too few values.")
        return format(compiledPattern, values, appendTo, null, true, offsets)
    }
    fun formatAndReplace(compiledPattern: String, result: StringBuilder, offsets: IntArray?, vararg values: CharSequence?): StringBuilder =
        formatAndReplaceValues(compiledPattern, result, offsets, values)
    fun formatAndReplaceValues(compiledPattern: String, result: StringBuilder, offsets: IntArray?, values: Array<out CharSequence?>?): StringBuilder {
        if ((values?.size ?: 0) < getArgumentLimit(compiledPattern)) throw IllegalArgumentException("Too few values.")
        var firstArg = -1
        var copy: String? = null
        if (getArgumentLimit(compiledPattern) > 0) {
            var i = 1
            while (i < compiledPattern.length) {
                val n = compiledPattern[i++].code
                if (n < ARG_NUM_LIMIT) {
                    if (formatterValueAt(values!!, n) === result) {
                        if (i == 2) firstArg = n else if (copy == null) copy = result.toString()
                    }
                } else i += n - ARG_NUM_LIMIT
            }
        }
        if (firstArg < 0) result.setLength(0)
        return format(compiledPattern, values, result, copy, false, offsets)
    }
    fun getTextWithNoArguments(compiledPattern: String): String {
        val sb = formatterLiteralBuilder(compiledPattern.length - 1 - getArgumentLimit(compiledPattern))
        var i = 1
        while (i < compiledPattern.length) {
            val length = compiledPattern[i++].code - ARG_NUM_LIMIT
            if (length > 0) { val end = i + length; sb.append(compiledPattern, i, end); i = end }
        }
        return sb.toString()
    }
    fun getLength(compiledPattern: String, codePoints: Boolean): Int {
        var result = 0
        var i = 1
        while (i < compiledPattern.length) {
            val length = compiledPattern[i++].code - ARG_NUM_LIMIT
            if (length > 0) {
                val end = i + length
                if (codePoints) {
                    var j = i
                    while (j < end) {
                        val c = compiledPattern[j++]
                        if (c.isHighSurrogate() && j < end && compiledPattern[j].isLowSurrogate()) j++
                        result++
                    }
                } else result += length
                i = end
            }
        }
        return result
    }
    fun getPrefixLength(compiledPattern: String): Int = when {
        compiledPattern.length == 1 -> 0
        compiledPattern[0].code == 0 -> compiledPattern.length - 2
        compiledPattern[1].code <= ARG_NUM_LIMIT -> 0
        else -> compiledPattern[1].code - ARG_NUM_LIMIT
    }
    class IterInternal {
        companion object {
            const val DONE = -1L
            fun step(state: Long, compiledPattern: CharSequence, output: Appendable): Long {
                var i = (state ushr 32).toInt() + 1
                while (i < compiledPattern.length && compiledPattern[i].code > ARG_NUM_LIMIT) {
                    val end = i + compiledPattern[i].code + 1 - ARG_NUM_LIMIT
                    appendFormatterLiteral(output, compiledPattern, i + 1, end)
                    i = end
                }
                if (i == compiledPattern.length) return DONE
                return (i.toLong() shl 32) or compiledPattern[i].code.toLong()
            }
            fun getArgIndex(state: Long): Int = state.toInt()
        }
    }
    private fun format(pattern: String, values: Array<out CharSequence?>?, result: StringBuilder, copy: String?, forbidResult: Boolean, offsets: IntArray?): StringBuilder {
        offsets?.fill(-1)
        val offsetsLength = offsets?.size ?: 0
        var i = 1
        while (i < pattern.length) {
            val n = pattern[i++].code
            if (n < ARG_NUM_LIMIT) {
                val value = formatterValueAt(values!!, n)
                if (value === result) {
                    if (forbidResult) throw IllegalArgumentException("Value must not be same object as result")
                    if (i == 2) { if (n < offsetsLength) offsets!![n] = 0 }
                    else { if (n < offsetsLength) offsets!![n] = result.length; result.append(copy) }
                } else { if (n < offsetsLength) offsets!![n] = result.length; result.append(value) }
            } else { val end = i + n - ARG_NUM_LIMIT; result.append(pattern, i, end); i = end }
        }
        return result
    }
}
internal expect fun formatterValueAt(values: Array<out CharSequence?>, index: Int): CharSequence?
internal expect fun appendFormatterLiteral(output: Appendable, pattern: CharSequence, start: Int, end: Int)

internal expect fun formatterLiteralBuilder(capacity: Int): StringBuilder
