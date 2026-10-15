// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// Port of ICU4J 78.3 MessagePattern (Unicode, Inc. and IBM).
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUConfig
import io.github.heapy.icu4k.impl.PatternProps
import io.github.heapy.icu4k.util.Freezable
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmStatic

/** Parses ICU message, choice, plural and select patterns into indexed syntax parts. */
class MessagePattern : Freezable<MessagePattern>, MessagePatternCloneable {
    enum class ApostropheMode { DOUBLE_OPTIONAL, DOUBLE_REQUIRED }
    enum class ArgType {
        NONE, SIMPLE, CHOICE, PLURAL, SELECT, SELECTORDINAL;
        fun hasPluralStyle(): Boolean = this == PLURAL || this == SELECTORDINAL
    }

    private var aposMode: ApostropheMode?
    private var msg: String? = null
    private var parts = ArrayList<Part>()
    private var numericValues: ArrayList<Double>? = null
    private var hasArgNames = false
    private var hasArgNumbers = false
    private var needsAutoQuoting = false
    @Volatile private var frozen = false
    private val pattern: String get() = msg!!

    constructor() { aposMode = defaultAposMode }
    constructor(mode: ApostropheMode?) { aposMode = mode }
    constructor(pattern: String?) { aposMode = defaultAposMode; parse(pattern) }

    fun parse(pattern: String?): MessagePattern {
        preParse(pattern)
        parseMessage(0, 0, 0, ArgType.NONE)
        return this
    }
    fun parseChoiceStyle(pattern: String?): MessagePattern {
        preParse(pattern)
        parseChoiceStyle(0, 0)
        return this
    }
    fun parsePluralStyle(pattern: String?): MessagePattern {
        preParse(pattern)
        parsePluralOrSelectStyle(ArgType.PLURAL, 0, 0)
        return this
    }
    fun parseSelectStyle(pattern: String?): MessagePattern {
        preParse(pattern)
        parsePluralOrSelectStyle(ArgType.SELECT, 0, 0)
        return this
    }
    fun clear() {
        if (isFrozen()) throw UnsupportedOperationException("Attempt to clear() a frozen MessagePattern instance.")
        msg = null
        hasArgNames = false
        hasArgNumbers = false
        needsAutoQuoting = false
        parts.clear()
        numericValues?.clear()
    }
    fun clearPatternAndSetApostropheMode(mode: ApostropheMode?) { clear(); aposMode = mode }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessagePattern) return false
        return aposMode!!.equals(other.aposMode) && msg == other.msg && parts == other.parts
    }
    override fun hashCode(): Int = (aposMode!!.hashCode() * 37 + (msg?.hashCode() ?: 0)) * 37 + parts.hashCode()
    fun getApostropheMode(): ApostropheMode? = aposMode
    internal fun jdkAposMode(): Boolean = aposMode == ApostropheMode.DOUBLE_REQUIRED
    fun getPatternString(): String? = msg
    fun hasNamedArguments(): Boolean = hasArgNames
    fun hasNumberedArguments(): Boolean = hasArgNumbers
    // Java's Object.toString permits a null return; preserve it for an unparsed instance.
    override fun toString(): String = sourceNullable(msg)
    fun autoQuoteApostropheDeep(): String? {
        if (!needsAutoQuoting) return msg
        var modified: StringBuilder? = null
        for (i in parts.lastIndex downTo 0) {
            val part = parts[i]
            if (part.type == Part.Type.INSERT_CHAR) {
                if (modified == null) modified = StringBuilder(pattern.length + 10).append(pattern)
                modified.insert(part.index, part.value.toChar())
            }
        }
        return modified?.toString() ?: msg
    }
    fun countParts(): Int = parts.size
    fun getPart(i: Int): Part = parts[i]
    fun getPartType(i: Int): Part.Type = parts[i].type
    fun getPatternIndex(partIndex: Int): Int = parts[partIndex].index
    fun getSubstring(part: Part?): String {
        val index = part!!.index
        return pattern.substring(index, index + part.length)
    }
    fun partSubstringMatches(part: Part?, s: String?): Boolean =
        part!!.length == s!!.length && pattern.regionMatches(part.index, s, 0, part.length)
    fun getNumericValue(part: Part?): Double = when (part!!.type) {
        Part.Type.ARG_INT -> part.value.toDouble()
        Part.Type.ARG_DOUBLE -> numericValues!![part.value]
        else -> NO_NUMERIC_VALUE
    }
    fun getPluralOffset(pluralStart: Int): Double {
        val part = parts[pluralStart]
        return if (part.type.hasNumericValue()) getNumericValue(part) else 0.0
    }
    fun getLimitPartIndex(start: Int): Int = maxOf(parts[start].limitPartIndex, start)

    class Part internal constructor(
        internal val type: Type, internal val index: Int, length: Int, value: Int,
    ) {
        internal val length = length.toChar().code
        internal var value = value.toShort().toInt()
        internal var limitPartIndex = 0
        enum class Type {
            MSG_START, MSG_LIMIT, SKIP_SYNTAX, INSERT_CHAR, REPLACE_NUMBER,
            ARG_START, ARG_LIMIT, ARG_NUMBER, ARG_NAME, ARG_TYPE, ARG_STYLE,
            ARG_SELECTOR, ARG_INT, ARG_DOUBLE;
            fun hasNumericValue(): Boolean = this == ARG_INT || this == ARG_DOUBLE
        }
        fun getType(): Type = type
        fun getIndex(): Int = index
        fun getLength(): Int = length
        fun getLimit(): Int = index + length
        fun getValue(): Int = value
        fun getArgType(): ArgType = if (type == Type.ARG_START || type == Type.ARG_LIMIT) ArgType.entries[value] else ArgType.NONE
        override fun toString(): String = "${type.name}(${if (type == Type.ARG_START || type == Type.ARG_LIMIT) getArgType().name else value.toString()})@$index"
        override fun equals(other: Any?): Boolean = this === other || other is Part &&
            type == other.type && index == other.index && length == other.length && value == other.value && limitPartIndex == other.limitPartIndex
        override fun hashCode(): Int = ((type.hashCode() * 37 + index) * 37 + length) * 37 + value
        internal companion object {
            const val MAX_LENGTH = 0xffff
            const val MAX_VALUE = 32767
        }
    }

    override fun clone(): MessagePattern = if (isFrozen()) this else cloneAsThawed()
    override fun cloneAsThawed(): MessagePattern = MessagePattern(aposMode).also {
        it.msg = msg
        it.parts = ArrayList(parts) // The source shares the parts, including after a failed parse.
        it.numericValues = numericValues?.let(::ArrayList)
        it.hasArgNames = hasArgNames
        it.hasArgNumbers = hasArgNumbers
        it.needsAutoQuoting = needsAutoQuoting
    }
    override fun freeze(): MessagePattern { frozen = true; return this }
    override fun isFrozen(): Boolean = frozen
    private fun preParse(pattern: String?) {
        if (isFrozen()) throw UnsupportedOperationException("Attempt to parse(${prefix(pattern, 0)}) on frozen MessagePattern instance.")
        msg = pattern
        hasArgNames = false
        hasArgNumbers = false
        needsAutoQuoting = false
        parts.clear()
        numericValues?.clear()
    }

    private fun parseMessage(initialIndex: Int, msgStartLength: Int, nestingLevel: Int, parentType: ArgType): Int {
        if (nestingLevel > Part.MAX_VALUE) throw IndexOutOfBoundsException()
        val msgStart = parts.size
        addPart(Part.Type.MSG_START, initialIndex, msgStartLength, nestingLevel)
        var index = initialIndex + msgStartLength
        while (index < pattern.length) {
            var c = pattern[index++]
            if (c == '\'') {
                if (index == pattern.length) {
                    addPart(Part.Type.INSERT_CHAR, index, 0, '\''.code)
                    needsAutoQuoting = true
                } else {
                    c = pattern[index]
                    if (c == '\'') {
                        addPart(Part.Type.SKIP_SYNTAX, index++, 1, 0)
                    } else if (aposMode == ApostropheMode.DOUBLE_REQUIRED || c == '{' || c == '}' ||
                        (parentType == ArgType.CHOICE && c == '|') || (parentType.hasPluralStyle() && c == '#')) {
                        addPart(Part.Type.SKIP_SYNTAX, index - 1, 1, 0)
                        while (true) {
                            index = pattern.indexOf('\'', index + 1)
                            if (index >= 0) {
                                if (index + 1 < pattern.length && pattern[index + 1] == '\'') {
                                    addPart(Part.Type.SKIP_SYNTAX, ++index, 1, 0)
                                } else {
                                    addPart(Part.Type.SKIP_SYNTAX, index++, 1, 0)
                                    break
                                }
                            } else {
                                index = pattern.length
                                addPart(Part.Type.INSERT_CHAR, index, 0, '\''.code)
                                needsAutoQuoting = true
                                break
                            }
                        }
                    } else {
                        addPart(Part.Type.INSERT_CHAR, index, 0, '\''.code)
                        needsAutoQuoting = true
                    }
                }
            } else if (parentType.hasPluralStyle() && c == '#') {
                addPart(Part.Type.REPLACE_NUMBER, index - 1, 1, 0)
            } else if (c == '{') {
                index = parseArg(index - 1, 1, nestingLevel)
            } else if ((nestingLevel > 0 && c == '}') || (parentType == ArgType.CHOICE && c == '|')) {
                val limitLength = if (parentType == ArgType.CHOICE && c == '}') 0 else 1
                addLimitPart(msgStart, Part.Type.MSG_LIMIT, index - 1, limitLength, nestingLevel)
                return if (parentType == ArgType.CHOICE) index - 1 else index
            }
        }
        if (nestingLevel > 0 && !inTopLevelChoiceMessage(nestingLevel, parentType)) {
            throw IllegalArgumentException("Unmatched '{' braces in message ${prefix()}")
        }
        addLimitPart(msgStart, Part.Type.MSG_LIMIT, index, 0, nestingLevel)
        return index
    }

    private fun parseArg(initialIndex: Int, argStartLength: Int, nestingLevel: Int): Int {
        val argStart = parts.size
        var argType = ArgType.NONE
        addPart(Part.Type.ARG_START, initialIndex, argStartLength, argType.ordinal)
        var index = skipWhiteSpace(initialIndex + argStartLength)
        val nameIndex = index
        if (index == pattern.length) throw IllegalArgumentException("Unmatched '{' braces in message ${prefix()}")
        index = skipIdentifier(index)
        val number = parseArgNumber(pattern, nameIndex, index)
        if (number >= 0) {
            val length = index - nameIndex
            if (length > Part.MAX_LENGTH || number > Part.MAX_VALUE) throw IndexOutOfBoundsException("Argument number too large: ${prefix(nameIndex)}")
            hasArgNumbers = true
            addPart(Part.Type.ARG_NUMBER, nameIndex, length, number)
        } else if (number == ARG_NAME_NOT_NUMBER) {
            val length = index - nameIndex
            if (length > Part.MAX_LENGTH) throw IndexOutOfBoundsException("Argument name too long: ${prefix(nameIndex)}")
            hasArgNames = true
            addPart(Part.Type.ARG_NAME, nameIndex, length, 0)
        } else {
            throw IllegalArgumentException("Bad argument syntax: ${prefix(nameIndex)}")
        }
        index = skipWhiteSpace(index)
        if (index == pattern.length) throw IllegalArgumentException("Unmatched '{' braces in message ${prefix()}")
        var c = pattern[index]
        if (c == '}') {
            // No type or style.
        } else if (c != ',') {
            throw IllegalArgumentException("Bad argument syntax: ${prefix(nameIndex)}")
        } else {
            index = skipWhiteSpace(index + 1)
            val typeIndex = index
            while (index < pattern.length && isArgTypeChar(pattern[index])) ++index
            val length = index - typeIndex
            index = skipWhiteSpace(index)
            if (index == pattern.length) throw IllegalArgumentException("Unmatched '{' braces in message ${prefix()}")
            c = pattern[index]
            if (length == 0 || (c != ',' && c != '}')) throw IllegalArgumentException("Bad argument syntax: ${prefix(nameIndex)}")
            if (length > Part.MAX_LENGTH) throw IndexOutOfBoundsException("Argument type name too long: ${prefix(nameIndex)}")
            argType = ArgType.SIMPLE
            if (length == 6) {
                argType = when {
                    typeMatches(typeIndex, "choice") -> ArgType.CHOICE
                    typeMatches(typeIndex, "plural") -> ArgType.PLURAL
                    typeMatches(typeIndex, "select") -> ArgType.SELECT
                    else -> ArgType.SIMPLE
                }
            } else if (length == 13 && typeMatches(typeIndex, "selectordinal")) argType = ArgType.SELECTORDINAL
            parts[argStart].value = argType.ordinal.toShort().toInt()
            if (argType == ArgType.SIMPLE) addPart(Part.Type.ARG_TYPE, typeIndex, length, 0)
            if (c == '}') {
                if (argType != ArgType.SIMPLE) throw IllegalArgumentException("No style field for complex argument: ${prefix(nameIndex)}")
            } else {
                ++index
                index = when (argType) {
                    ArgType.SIMPLE -> parseSimpleStyle(index)
                    ArgType.CHOICE -> parseChoiceStyle(index, nestingLevel)
                    else -> parsePluralOrSelectStyle(argType, index, nestingLevel)
                }
            }
        }
        addLimitPart(argStart, Part.Type.ARG_LIMIT, index, 1, argType.ordinal)
        return index + 1
    }

    private fun parseSimpleStyle(initialIndex: Int): Int {
        val start = initialIndex
        var index = initialIndex
        var nestedBraces = 0
        while (index < pattern.length) {
            when (pattern[index++]) {
                '\'' -> {
                    index = pattern.indexOf('\'', index)
                    if (index < 0) throw IllegalArgumentException("Quoted literal argument style text reaches to the end of the message: ${prefix(start)}")
                    ++index
                }
                '{' -> ++nestedBraces
                '}' -> if (nestedBraces > 0) --nestedBraces else {
                    val length = --index - start
                    if (length > Part.MAX_LENGTH) throw IndexOutOfBoundsException("Argument style text too long: ${prefix(start)}")
                    addPart(Part.Type.ARG_STYLE, start, length, 0)
                    return index
                }
            }
        }
        throw IllegalArgumentException("Unmatched '{' braces in message ${prefix()}")
    }

    private fun parseChoiceStyle(initialIndex: Int, nestingLevel: Int): Int {
        val start = initialIndex
        var index = skipWhiteSpace(initialIndex)
        if (index == pattern.length || pattern[index] == '}') throw IllegalArgumentException("Missing choice argument pattern in ${prefix()}")
        while (true) {
            val numberIndex = index
            index = skipDouble(index)
            val length = index - numberIndex
            if (length == 0) throw IllegalArgumentException("Bad choice pattern syntax: ${prefix(start)}")
            if (length > Part.MAX_LENGTH) throw IndexOutOfBoundsException("Choice number too long: ${prefix(numberIndex)}")
            parseDouble(numberIndex, index, true)
            index = skipWhiteSpace(index)
            if (index == pattern.length) throw IllegalArgumentException("Bad choice pattern syntax: ${prefix(start)}")
            val c = pattern[index]
            if (c != '#' && c != '<' && c != '≤') throw IllegalArgumentException("Expected choice separator (#<≤) instead of '$c' in choice pattern ${prefix(start)}")
            addPart(Part.Type.ARG_SELECTOR, index, 1, 0)
            index = parseMessage(index + 1, 0, nestingLevel + 1, ArgType.CHOICE)
            if (index == pattern.length) return index
            if (pattern[index] == '}') {
                if (!inMessageFormatPattern(nestingLevel)) throw IllegalArgumentException("Bad choice pattern syntax: ${prefix(start)}")
                return index
            }
            index = skipWhiteSpace(index + 1)
        }
    }

    private fun parsePluralOrSelectStyle(argType: ArgType, initialIndex: Int, nestingLevel: Int): Int {
        val start = initialIndex
        var index = initialIndex
        var isEmpty = true
        var hasOther = false
        while (true) {
            index = skipWhiteSpace(index)
            val eos = index == pattern.length
            if (eos || pattern[index] == '}') {
                if (eos == inMessageFormatPattern(nestingLevel)) throw IllegalArgumentException("Bad ${argType.name.lowercase()} pattern syntax: ${prefix(start)}")
                if (!hasOther) throw IllegalArgumentException("Missing 'other' keyword in ${argType.name.lowercase()} pattern in ${prefix()}")
                return index
            }
            val selectorIndex = index
            if (argType.hasPluralStyle() && pattern[selectorIndex] == '=') {
                index = skipDouble(index + 1)
                val length = index - selectorIndex
                if (length == 1) throw IllegalArgumentException("Bad ${argType.name.lowercase()} pattern syntax: ${prefix(start)}")
                if (length > Part.MAX_LENGTH) throw IndexOutOfBoundsException("Argument selector too long: ${prefix(selectorIndex)}")
                addPart(Part.Type.ARG_SELECTOR, selectorIndex, length, 0)
                parseDouble(selectorIndex + 1, index, false)
            } else {
                index = skipIdentifier(index)
                val length = index - selectorIndex
                if (length == 0) throw IllegalArgumentException("Bad ${argType.name.lowercase()} pattern syntax: ${prefix(start)}")
                if (argType.hasPluralStyle() && length == 6 && index < pattern.length && pattern.regionMatches(selectorIndex, "offset:", 0, 7)) {
                    if (!isEmpty) throw IllegalArgumentException("Plural argument 'offset:' (if present) must precede key-message pairs: ${prefix(start)}")
                    val valueIndex = skipWhiteSpace(index + 1)
                    index = skipDouble(valueIndex)
                    if (index == valueIndex) throw IllegalArgumentException("Missing value for plural 'offset:' ${prefix(start)}")
                    if (index - valueIndex > Part.MAX_LENGTH) throw IndexOutOfBoundsException("Plural offset value too long: ${prefix(valueIndex)}")
                    parseDouble(valueIndex, index, false)
                    isEmpty = false
                    continue
                } else {
                    if (length > Part.MAX_LENGTH) throw IndexOutOfBoundsException("Argument selector too long: ${prefix(selectorIndex)}")
                    addPart(Part.Type.ARG_SELECTOR, selectorIndex, length, 0)
                    // Deliberately uses selector length: ICU accepts prefixes of "other" here.
                    if (pattern.regionMatches(selectorIndex, "other", 0, length)) hasOther = true
                }
            }
            index = skipWhiteSpace(index)
            if (index == pattern.length || pattern[index] != '{') throw IllegalArgumentException("No message fragment after ${argType.name.lowercase()} selector: ${prefix(selectorIndex)}")
            index = parseMessage(index, 1, nestingLevel + 1, argType)
            isEmpty = false
        }
    }

    private fun parseDouble(start: Int, limit: Int, allowInfinity: Boolean) {
        var value = 0
        var isNegative = 0
        var index = start
        var c = pattern[index++]
        if (c == '-' || c == '+') {
            if (c == '-') isNegative = 1
            if (index == limit) throw NumberFormatException("Bad syntax for numeric value: ${pattern.substring(start, limit)}")
            c = pattern[index++]
        }
        if (c == '∞') {
            if (allowInfinity && index == limit) {
                addArgDoublePart(if (isNegative != 0) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY, start, limit - start)
                return
            }
            throw NumberFormatException("Bad syntax for numeric value: ${pattern.substring(start, limit)}")
        }
        while (c in '0'..'9') {
            value = value * 10 + (c - '0')
            if (value > Part.MAX_VALUE + isNegative) break
            if (index == limit) {
                addPart(Part.Type.ARG_INT, start, limit - start, if (isNegative != 0) -value else value)
                return
            }
            c = pattern[index++]
        }
        val text = pattern.substring(start, limit)
        var scan = if (text[0] == '+' || text[0] == '-') 1 else 0
        var point = false
        while (scan < text.length && (text[scan] in '0'..'9' || text[scan] == '.')) {
            if (text[scan++] == '.') {
                if (point) throw NumberFormatException("multiple points")
                point = true
            }
        }
        val numericValue = text.toDoubleOrNull() ?: run {
            // Current Java floating-point diagnostics abbreviate very long malformed values.
            val diagnostic = if (text.length > 1000) text.take(497) + " ... " + text.takeLast(498) else text
            throw NumberFormatException("For input string: \"$diagnostic\"")
        }
        addArgDoublePart(numericValue, start, limit - start)
    }
    private fun skipWhiteSpace(index: Int): Int = PatternProps.skipWhiteSpace(pattern, index)
    private fun skipIdentifier(index: Int): Int = PatternProps.skipIdentifier(pattern, index)
    private fun skipDouble(initialIndex: Int): Int {
        var index = initialIndex
        while (index < pattern.length) {
            val c = pattern[index]
            if ((c < '0' && c !in "+-.") || (c > '9' && c != 'e' && c != 'E' && c != '∞')) break
            ++index
        }
        return index
    }
    private fun typeMatches(index: Int, type: String): Boolean {
        // Only ASCII case folding, as in ICU's explicit per-character comparisons.
        for (i in type.indices) {
            val c = pattern[index + i]
            if (c != type[i] && c != type[i] - ('a' - 'A')) return false
        }
        return true
    }
    private fun inMessageFormatPattern(nestingLevel: Int): Boolean = nestingLevel > 0 || parts[0].type == Part.Type.MSG_START
    private fun inTopLevelChoiceMessage(nestingLevel: Int, parentType: ArgType): Boolean = nestingLevel == 1 && parentType == ArgType.CHOICE && parts[0].type != Part.Type.MSG_START
    private fun addPart(type: Part.Type, index: Int, length: Int, value: Int) { parts.add(Part(type, index, length, value)) }
    private fun addLimitPart(start: Int, type: Part.Type, index: Int, length: Int, value: Int) {
        parts[start].limitPartIndex = parts.size
        addPart(type, index, length, value)
    }
    private fun addArgDoublePart(numericValue: Double, start: Int, length: Int) {
        val numericIndex: Int
        if (numericValues == null) { numericValues = ArrayList(); numericIndex = 0 } else {
            numericIndex = numericValues!!.size
            if (numericIndex > Part.MAX_VALUE) throw IndexOutOfBoundsException("Too many numeric values")
        }
        numericValues!!.add(numericValue)
        addPart(Part.Type.ARG_DOUBLE, start, length, numericIndex)
    }
    private fun prefix(start: Int = 0): String = prefix(msg, start)

    companion object {
        const val ARG_NAME_NOT_NUMBER = -1
        const val ARG_NAME_NOT_VALID = -2
        const val NO_NUMERIC_VALUE = -123456789.0
        private const val MAX_PREFIX_LENGTH = 24
        private val defaultAposMode = ApostropheMode.valueOf(ICUConfig.get("com.ibm.icu.text.MessagePattern.ApostropheMode", "DOUBLE_OPTIONAL")!!)
        @JvmStatic fun validateArgumentName(name: String?): Int {
            if (!PatternProps.isIdentifier(name!!)) return ARG_NAME_NOT_VALID
            return parseArgNumber(name, 0, name.length)
        }
        private fun parseArgNumber(s: CharSequence, initialStart: Int, limit: Int): Int {
            var start = initialStart
            if (start >= limit) return ARG_NAME_NOT_VALID
            var number: Int
            var badNumber: Boolean
            var c = s[start++]
            if (c == '0') {
                if (start == limit) return 0
                number = 0
                badNumber = true
            } else if (c in '1'..'9') {
                number = c - '0'
                badNumber = false
            } else return ARG_NAME_NOT_NUMBER
            while (start < limit) {
                c = s[start++]
                if (c in '0'..'9') {
                    if (number >= Int.MAX_VALUE / 10) badNumber = true else number = number * 10 + (c - '0')
                } else return ARG_NAME_NOT_NUMBER
            }
            return if (badNumber) ARG_NAME_NOT_VALID else number
        }
        internal fun appendReducedApostrophes(s: String, initialStart: Int, limit: Int, sb: StringBuilder) {
            var start = initialStart
            var doubleApos = -1
            while (true) {
                val i = s.indexOf('\'', start)
                if (i < 0 || i >= limit) { sb.append(s, start, limit); break }
                if (i == doubleApos) {
                    sb.append('\''); ++start; doubleApos = -1
                } else {
                    sb.append(s, start, i)
                    start = i + 1
                    doubleApos = start
                }
            }
        }
        private fun isArgTypeChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'
        private fun prefix(s: String?, start: Int): String {
            val result = StringBuilder(MAX_PREFIX_LENGTH + 20)
            if (start == 0) result.append('"') else result.append("[at pattern index ").append(start).append("] \"")
            val substringLength = s!!.length - start
            if (substringLength <= MAX_PREFIX_LENGTH) result.append(if (start == 0) s else s.substring(start)) else {
                var limit = start + MAX_PREFIX_LENGTH - 4
                if (s[limit - 1].isHighSurrogate()) --limit
                result.append(s, start, limit).append(" ...")
            }
            return result.append('"').toString()
        }
        @Suppress("UNCHECKED_CAST")
        private fun <T> sourceNullable(value: T?): T = value as T
    }
}
