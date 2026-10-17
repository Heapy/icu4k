// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

/** JDK apostrophe handling for the untyped string-argument patterns in CLDR zone resources.
 * This internal resource compiler is not a general MessageFormat implementation.
 */
internal class TimeZoneNamePatternCore(pattern: String?) {
    private val parts = mutableListOf<Any>()
    init {
        val input = pattern!!
        val literal = StringBuilder()
        var quoted = false
        var i = 0
        while (i < input.length) {
            val c = input[i++]
            when {
                c == '\'' -> if (i < input.length && input[i] == '\'') { literal.append(c); i++ } else quoted = !quoted
                c == '{' && !quoted -> {
                    if (literal.isNotEmpty()) { parts.add(literal.toString()); literal.setLength(0) }
                    val start = i
                    while (i < input.length && input[i] != '}') i++
                    if (i == input.length) throw IllegalArgumentException("Unmatched braces in the pattern.")
                    val indexText = input.substring(start, i++)
                    val index = indexText.toIntOrNull() ?: throw IllegalArgumentException("can't parse argument number: $indexText")
                    if (index < 0) throw IllegalArgumentException("negative argument number: $index")
                    parts.add(index)
                }
                else -> literal.append(c)
            }
        }
        if (literal.isNotEmpty()) parts.add(literal.toString())
    }
    fun format(args: Array<out String?>): String = buildString {
        for (part in parts) when (part) {
            is String -> append(part)
            is Int -> if (part < args.size) append(args[part]) else append('{').append(part).append('}')
        }
    }
}
