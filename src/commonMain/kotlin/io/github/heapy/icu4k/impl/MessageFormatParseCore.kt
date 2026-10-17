// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.MessagePattern
import io.github.heapy.icu4k.text.MessagePattern.ArgType
import io.github.heapy.icu4k.text.MessagePattern.Part
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.ParseException

/** Complete MessageFormat parsing traversal. Cached Format objects remain owned by the facade. */
internal class MessageFormatParseCore(private val owner: Owner) {
    fun interface Parser { fun parseObject(source: String?, position: ParsePosition?): Any? }
    interface Owner {
        val messagePattern: MessagePattern?
        val cachedParsers: Map<Int, Parser?>?
    }

    fun parse(source: String?, position: ParsePosition?): Array<Any?>? {
        val pattern = owner.messagePattern!!
        if (pattern.hasNamedArguments()) throw IllegalArgumentException("This method is not available in MessageFormat objects that use named argument.")
        var max = -1
        var index = 0
        while (true) {
            index = MessageFormatPatternCore.nextTopLevelArgStart(pattern, index)
            if (index < 0) break
            val number = pattern.getPart(index + 1).value
            if (number > max) max = number
        }
        val result = arrayOfNulls<Any>(max + 1)
        val backup = position!!.index
        parseInto(0, source, position, result, null)
        return if (position.index == backup) null else result
    }

    fun parseToMap(source: String?, position: ParsePosition?): MutableMap<String, Any?>? {
        val result = HashMap<String, Any?>()
        val backup = position!!.index
        parseInto(0, source, position, null, result)
        return if (position.index == backup) null else result
    }

    fun parse(source: String?): Array<Any?>? {
        val pos = ParsePosition(0)
        val result = parse(source, pos)
        if (pos.index == 0) throw ParseException("MessageFormat parse error!", pos.errorIndex)
        return result
    }

    fun parseToMap(source: String?): MutableMap<String, Any?> {
        val pos = ParsePosition(0)
        val result = HashMap<String, Any?>()
        parseInto(0, source, pos, null, result)
        if (pos.index == 0) throw ParseException("MessageFormat parse error!", pos.errorIndex)
        return result
    }

    fun parseObject(source: String?, position: ParsePosition?): Any? =
        if (!owner.messagePattern!!.hasNamedArguments()) parse(source, position) else parseToMap(source, position)

    fun parseInto(msgStart: Int, source: String?, position: ParsePosition?, args: Array<Any?>?, argsMap: MutableMap<String, Any?>?) {
        if (source == null) return
        val pattern = owner.messagePattern!!
        val text = pattern.getPatternString()!!
        var previous = pattern.getPart(msgStart).getLimit()
        var sourceOffset = position!!.index
        val temp = ParsePosition(0)
        var i = msgStart + 1
        while (true) {
            var part = pattern.getPart(i)
            val type = part.type
            val length = part.index - previous
            if (length == 0 || text.regionMatches(previous, source, sourceOffset, length)) {
                sourceOffset += length
                previous += length
            } else { position.errorIndex = sourceOffset; return }
            if (type == Part.Type.MSG_LIMIT) { position.index = sourceOffset; return }
            if (type == Part.Type.SKIP_SYNTAX || type == Part.Type.INSERT_CHAR) {
                previous = part.getLimit()
                i++
                continue
            }
            val limit = pattern.getLimitPartIndex(i)
            val argType = part.getArgType()
            part = pattern.getPart(++i)
            val argNumber: Int
            val key: String?
            val argId: Any
            if (args != null) {
                argNumber = part.value
                key = null
                argId = argNumber
            } else {
                argNumber = 0
                key = if (part.type == Part.Type.ARG_NAME) pattern.getSubstring(part) else part.value.toString()
                argId = key
            }
            i++
            val parsers = owner.cachedParsers
            val parser = parsers?.get(i - 2)
            var haveResult = false
            var result: Any? = null
            when {
                parser != null -> {
                    temp.index = sourceOffset
                    result = parser.parseObject(source, temp)
                    if (temp.index == sourceOffset) { position.errorIndex = sourceOffset; return }
                    haveResult = true
                    sourceOffset = temp.index
                }
                argType == ArgType.NONE || parsers?.containsKey(i - 2) == true -> {
                    val literal = MessageFormatPatternCore.literalUntilNextArgument(pattern, limit)
                    val next = if (literal.isNotEmpty()) source.indexOf(literal, sourceOffset) else source.length
                    if (next < 0) { position.errorIndex = sourceOffset; return }
                    if (sourceOffset < 0 || next > source.length || sourceOffset > next) throw IndexOutOfBoundsException()
                    val value = source.substring(sourceOffset, next)
                    if (value != "{$argId}") { haveResult = true; result = value }
                    sourceOffset = next
                }
                argType == ArgType.CHOICE -> {
                    temp.index = sourceOffset
                    val value = MessageFormatPatternCore.parseChoiceArgument(pattern, i, source, temp)
                    if (temp.index == sourceOffset) { position.errorIndex = sourceOffset; return }
                    result = value
                    haveResult = true
                    sourceOffset = temp.index
                }
                argType.hasPluralStyle() || argType == ArgType.SELECT -> throw UnsupportedOperationException("Parsing of plural/select/selectordinal argument is not supported.")
                else -> throw IllegalStateException("unexpected argType $argType")
            }
            if (haveResult) {
                if (args != null) {
                    if (argNumber !in args.indices) throw IndexOutOfBoundsException()
                    args[argNumber] = result
                } else if (argsMap != null) argsMap[key!!] = result
            }
            previous = pattern.getPart(limit).getLimit()
            i = limit + 1
        }
    }
}
