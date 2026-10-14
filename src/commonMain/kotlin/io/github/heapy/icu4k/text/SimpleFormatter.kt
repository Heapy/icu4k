// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.SimpleFormatterImpl

/** Immutable ICU formatter for quoted text and numbered string arguments. */
class SimpleFormatter private constructor(private val compiledPattern: String) {
    fun getArgumentLimit(): Int = SimpleFormatterImpl.getArgumentLimit(compiledPattern)
    fun format(vararg values: CharSequence?): String = SimpleFormatterImpl.formatAndAppendValues(compiledPattern, StringBuilder(), null, values).toString()
    fun formatAndAppend(appendTo: StringBuilder, offsets: IntArray?, vararg values: CharSequence?): StringBuilder =
        SimpleFormatterImpl.formatAndAppendValues(compiledPattern, appendTo, offsets, values)
    fun formatAndReplace(result: StringBuilder, offsets: IntArray?, vararg values: CharSequence?): StringBuilder =
        SimpleFormatterImpl.formatAndReplaceValues(compiledPattern, result, offsets, values)
    fun formatAndAppendValues(appendTo: StringBuilder, offsets: IntArray?, values: Array<out CharSequence?>?): StringBuilder =
        SimpleFormatterImpl.formatAndAppendValues(compiledPattern, appendTo, offsets, values)
    fun formatAndReplaceValues(result: StringBuilder, offsets: IntArray?, values: Array<out CharSequence?>?): StringBuilder =
        SimpleFormatterImpl.formatAndReplaceValues(compiledPattern, result, offsets, values)
    override fun toString(): String = SimpleFormatterImpl.formatAndAppendValues(compiledPattern, StringBuilder(), null, Array(getArgumentLimit()) { "{$it}" }).toString()
    fun getTextWithNoArguments(): String = SimpleFormatterImpl.getTextWithNoArguments(compiledPattern)
    companion object {
        fun compile(pattern: CharSequence?): SimpleFormatter = compileMinMaxArguments(pattern, 0, Int.MAX_VALUE)
        fun compileMinMaxArguments(pattern: CharSequence?, min: Int, max: Int): SimpleFormatter =
            SimpleFormatter(SimpleFormatterImpl.compileToStringMinMaxArguments(pattern, StringBuilder(), min, max))
    }
}
