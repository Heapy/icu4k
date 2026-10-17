// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.MessagePattern
import io.github.heapy.icu4k.text.MessagePattern.Part
import io.github.heapy.icu4k.text.MessagePattern.ArgType
import io.github.heapy.icu4k.text.ParsePosition

/** Shared traversal and choice matching from ICU4J MessageFormat. */
internal object MessageFormatPatternCore {
    fun nextTopLevelArgStart(pattern: MessagePattern, previous: Int): Int {
        var index = if (previous == 0) previous else pattern.getLimitPartIndex(previous)
        while (true) when (pattern.getPartType(++index)) {
            Part.Type.ARG_START -> return index
            Part.Type.MSG_LIMIT -> return -1
            else -> Unit
        }
    }

    fun literalUntilNextArgument(pattern: MessagePattern, from: Int): String {
        val result = StringBuilder()
        val text = pattern.getPatternString()!!
        var previous = pattern.getPart(from).getLimit()
        var i = from + 1
        while (true) {
            val part = pattern.getPart(i++)
            result.appendRange(text, previous, part.index)
            if (part.type == Part.Type.ARG_START || part.type == Part.Type.MSG_LIMIT) return result.toString()
            previous = part.getLimit()
        }
    }

    fun findChoiceSubMessage(pattern: MessagePattern, start: Int, number: Double): Int {
        val count = pattern.countParts()
        var index = start + 2
        while (true) {
            val message = index
            index = pattern.getLimitPartIndex(index) + 1
            if (index >= count) return message
            val part = pattern.getPart(index++)
            if (part.type == Part.Type.ARG_LIMIT) return message
            val boundary = pattern.getNumericValue(part)
            val selectorIndex = pattern.getPatternIndex(index++)
            val selector = pattern.getPatternString()!![selectorIndex]
            if (if (selector == '<') !(number > boundary) else !(number >= boundary)) return message
        }
    }

    fun parseChoiceArgument(pattern: MessagePattern, startPart: Int, source: String, pos: ParsePosition): Double {
        val start = pos.index
        var furthest = start
        var best = Double.NaN
        var index = startPart
        while (pattern.getPartType(index) != Part.Type.ARG_LIMIT) {
            val number = pattern.getNumericValue(pattern.getPart(index))
            index += 2
            val limit = pattern.getLimitPartIndex(index)
            val length = matchStringUntilLimitPart(pattern, index, limit, source, start)
            if (length >= 0) {
                val next = start + length
                if (next > furthest) {
                    furthest = next
                    best = number
                    if (furthest == source.length) break
                }
            }
            index = limit + 1
        }
        if (furthest == start) pos.errorIndex = start else pos.index = furthest
        return best
    }

    fun matchStringUntilLimitPart(pattern: MessagePattern, startPart: Int, limitPart: Int, source: String, sourceOffset: Int): Int {
        var length = 0
        val text = pattern.getPatternString()!!
        var previous = pattern.getPart(startPart).getLimit()
        var index = startPart
        while (true) {
            val part = pattern.getPart(++index)
            if (index == limitPart || part.type == Part.Type.SKIP_SYNTAX) {
                val chunk = part.index - previous
                // Source keeps sourceOffset fixed across quoted chunks.
                if (chunk != 0 && !source.regionMatches(sourceOffset, text, previous, chunk)) return -1
                length += chunk
                if (index == limitPart) return length
                previous = part.getLimit()
            }
        }
    }

    fun findOtherSubMessage(pattern: MessagePattern, start: Int): Int {
        val count = pattern.countParts()
        var index = start
        if (pattern.getPart(index).type.hasNumericValue()) index++
        do {
            val part = pattern.getPart(index++)
            if (part.type == Part.Type.ARG_LIMIT) break
            if (pattern.partSubstringMatches(part, "other")) return index
            if (pattern.getPartType(index).hasNumericValue()) index++
            index = pattern.getLimitPartIndex(index)
            index++
        } while (index < count)
        return 0
    }

    fun findFirstPluralNumberArg(pattern: MessagePattern, msgStart: Int, argName: String): Int {
        var i = msgStart + 1
        while (true) {
            val part = pattern.getPart(i)
            if (part.type == Part.Type.MSG_LIMIT) return 0
            if (part.type == Part.Type.REPLACE_NUMBER) return -1
            if (part.type == Part.Type.ARG_START) {
                if (argName.isNotEmpty() && (part.getArgType() == ArgType.NONE || part.getArgType() == ArgType.SIMPLE) && pattern.partSubstringMatches(pattern.getPart(i + 1), argName)) return i
                i = pattern.getLimitPartIndex(i)
            }
            i++
        }
    }
}
