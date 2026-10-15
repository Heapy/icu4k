// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.number.DecimalQuantityCore.Operand

/** Internal constraint layer; public rule lists and sample parsing are separate. */
internal interface PluralCondition {
    enum class SampleType { INTEGER, DECIMAL }
    fun evaluate(number: (Operand) -> Double): Boolean
    fun isLimited(type: SampleType?): Boolean
}

internal class PluralConditionParseException(message: String, val errorOffset: Int = -1) : Exception(message)

internal object PluralConditionParser {
    private val or = Regex("[\\t-\\r ]*or[\\t-\\r ]*")
    private val and = Regex("[\\t-\\r ]*and[\\t-\\r ]*")
    // Pattern.split(input, 0) discards trailing empty fields, except for empty input.
    private fun split(regex: Regex, input: String): List<String> =
        if (input.isEmpty()) listOf("") else regex.split(input).dropLastWhile { it.isEmpty() }

    private fun tokens(input: String): Array<String> {
        val result = mutableListOf<String>()
        val word = StringBuilder()
        fun flush() { if (word.isNotEmpty()) { result.add(word.toString()); word.clear() } }
        for (c in input) when (c) {
            '\t', '\n', '\u000c', '\r', ' ' -> flush()
            '!', '%', ',', '.', '=' -> { flush(); result.add(c.toString()) }
            else -> word.append(c)
        }
        flush()
        return result.toTypedArray()
    }

    fun parse(description: String): PluralCondition? {
        var result: PluralCondition? = null
        for (orPart in split(or, description)) {
            var conjunction: PluralCondition? = null
            for (part in split(and, orPart)) {
                val parsed = parseRelation(part.trim { it <= ' ' })
                conjunction = if (conjunction == null) parsed else Binary(conjunction, parsed, true)
            }
            result = if (result == null) conjunction else Binary(result, conjunction, false)
        }
        return result
    }

    private fun parseRelation(condition: String): PluralCondition {
        val tokens = tokens(condition)
        var x = 0
        fun unexpected(token: String): Nothing = throw PluralConditionParseException("unexpected token '$token' in '$condition'")
        fun next(): String = if (x < tokens.size) tokens[x++] else throw PluralConditionParseException("missing token at end of '$condition'")
        var t = tokens[x++]
        val operand = try { Operand.valueOf(t) } catch (_: Exception) { unexpected(t) }
        if (x == tokens.size) return Always
        var mod = 0
        var inRange = true
        var integersOnly = true
        t = tokens[x++]
        if (t == "mod" || t == "%") { mod = DecimalNumberParsing.int(tokens[x++]); t = next() }
        if (t == "not") {
            inRange = !inRange; t = next()
            if (t == "=") unexpected(t)
        } else if (t == "!") {
            inRange = !inRange; t = next()
            if (t != "=") unexpected(t)
        }
        val compatibility = t == "is"
        if (t == "is" || t == "in" || t == "=") {
            if (compatibility && !inRange) unexpected(t)
            t = next()
        } else if (t == "within") {
            integersOnly = false; t = next()
        } else unexpected(t)
        if (t == "not") {
            if (!compatibility && !inRange) unexpected(t)
            inRange = !inRange; t = next()
        }
        var lowBound = Long.MAX_VALUE.toDouble()
        var highBound = Long.MIN_VALUE.toDouble()
        val values = mutableListOf<Long>()
        while (true) {
            val low = DecimalNumberParsing.long(t)
            var high = low
            if (x < tokens.size) {
                t = next()
                if (t == ".") {
                    t = next()
                    if (t != ".") unexpected(t)
                    high = DecimalNumberParsing.long(next())
                    if (x < tokens.size) { t = next(); if (t != ",") unexpected(t) }
                } else if (t != ",") unexpected(t)
            }
            if (low > high) unexpected("$low~$high")
            if (mod != 0 && high >= mod) unexpected("$high>mod=$mod")
            values.add(low); values.add(high)
            lowBound = minOf(lowBound, low.toDouble())
            highBound = maxOf(highBound, high.toDouble())
            if (x >= tokens.size) break
            t = next()
        }
        if (t == ",") unexpected(t)
        if (lowBound != highBound && compatibility && !inRange) unexpected("is not <range>")
        return Range(mod, inRange, operand, integersOnly, lowBound, highBound,
            if (values.size == 2) null else values.toLongArray())
    }

    private object Always : PluralCondition {
        override fun evaluate(number: (Operand) -> Double) = true
        override fun isLimited(type: PluralCondition.SampleType?) = false
        override fun toString() = ""
    }

    private class Binary(val a: PluralCondition, val b: PluralCondition?, val conjunction: Boolean) : PluralCondition {
        override fun evaluate(number: (Operand) -> Double) =
            if (conjunction) a.evaluate(number) && b!!.evaluate(number) else a.evaluate(number) || b!!.evaluate(number)
        override fun isLimited(type: PluralCondition.SampleType?) =
            if (conjunction) a.isLimited(type) || b!!.isLimited(type) else a.isLimited(type) && b!!.isLimited(type)
        override fun toString() = a.toString() + (if (conjunction) " and " else " or ") + b!!.toString()
    }

    private class Range(val mod: Int, val inRange: Boolean, val operand: Operand, val integersOnly: Boolean,
                        val lower: Double, val upper: Double, val ranges: LongArray?) : PluralCondition {
        override fun evaluate(number: (Operand) -> Double): Boolean {
            var n = number(operand)
            if ((integersOnly && n - n.toLong().toDouble() != 0.0) || (operand == Operand.j && number(Operand.v) != 0.0)) return !inRange
            if (mod != 0) n %= mod
            var test = n >= lower && n <= upper
            if (test && ranges != null) {
                test = false
                var i = 0
                while (!test && i < ranges.size) { test = n >= ranges[i].toDouble() && n <= ranges[i + 1].toDouble(); i += 2 }
            }
            return inRange == test
        }
        override fun isLimited(type: PluralCondition.SampleType?): Boolean {
            val hasDecimals = operand in arrayOf(Operand.v, Operand.w, Operand.f, Operand.t) && inRange != (lower == upper && lower == 0.0)
            return when (type) {
                PluralCondition.SampleType.INTEGER -> hasDecimals || (operand in arrayOf(Operand.n, Operand.i, Operand.j) && mod == 0 && inRange)
                null -> throw NullPointerException()
                PluralCondition.SampleType.DECIMAL -> (!hasDecimals || operand == Operand.n || operand == Operand.j) && (integersOnly || lower == upper) && mod == 0 && inRange
            }
        }
        override fun toString(): String = buildString {
            append(operand)
            if (mod != 0) append(" % $mod")
            append(if (lower == upper || integersOnly) { if (inRange) " = " else " != " } else { if (inRange) " within " else " not within " })
            fun range(low: Double, high: Double) {
                fun format(n: Double) = if (n == n.toLong().toDouble()) n.toLong().toString() else CommonDoubleToDecimal.format(n)
                append(format(low))
                if (low != high) { append(".."); append(format(high)) }
            }
            if (ranges == null) range(lower, upper) else for (i in ranges.indices step 2) {
                if (i != 0) append(',')
                range(ranges[i].toDouble(), ranges[i + 1].toDouble())
            }
        }
    }
}
