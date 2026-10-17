// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacterDirection as D

/** X1–X8 and explicit embedding validation, before implicit-level resolution. */
internal class BidiExplicitCore(private val input: Input) {
    internal interface Input : BidiBracketCore.Input {
        val length: Int
        val paraCount: Int
        val paraLimits: IntArray
        val paraLevels: ByteArray
        val paraLevel: Byte
        val defaultParaLevel: Byte
        val orderParagraphsLTR: Boolean
        var flags: Int
        var isolateCount: Int
    }
    fun directionFromFlags(): Byte {
        val flags = input.flags
        return if (!((flags and MASK_RTL) != 0 || ((flags and flag(D.ARABIC_NUMBER)) != 0 && (flags and MASK_POSSIBLE_N) != 0))) 0
        else if ((flags and MASK_LTR) == 0) 1 else 2
    }
    fun resolve(): Byte {
        var level = input.getParaLevelAt(0)
        input.isolateCount = 0
        val direction = directionFromFlags()
        if (direction.toInt() != 2) return direction
        if (input.reorderingMode > 1) {
            for (paragraph in 0 until input.paraCount) {
                val start = if (paragraph == 0) 0 else input.paraLimits[paragraph - 1]
                val limit = input.paraLimits[paragraph]
                level = input.paraLevels[paragraph]
                for (i in start until limit) input.levels[i] = level
            }
            return direction
        }
        if ((input.flags and (MASK_EXPLICIT or MASK_ISO)) == 0) {
            val brackets = BidiBracketCore(input)
            brackets.init()
            for (paragraph in 0 until input.paraCount) {
                val start = if (paragraph == 0) 0 else input.paraLimits[paragraph - 1]
                val limit = input.paraLimits[paragraph]
                level = input.paraLevels[paragraph]
                for (i in start until limit) {
                    input.levels[i] = level
                    val property = bidiByteAt(input.dirProps, i).toInt()
                    if (property == D.BOUNDARY_NEUTRAL) continue
                    if (property == D.BLOCK_SEPARATOR) {
                        if (i + 1 < input.length) {
                            if (bidiCharAt(input.text, i) == '\r' && bidiCharAt(input.text, i + 1) == '\n') continue
                            brackets.processB(level)
                        }
                        continue
                    }
                    brackets.processChar(i)
                }
            }
            return direction
        }
        var embeddingLevel = level
        var previousLevel = level
        var lastCcPos = 0
        val stack = ShortArray(127)
        var stackLast = 0
        var overflowIsolateCount = 0
        var overflowEmbeddingCount = 0
        var validIsolateCount = 0
        val brackets = BidiBracketCore(input)
        brackets.init()
        stack[0] = level.toShort()
        input.flags = 0
        for (i in 0 until input.length) {
            val property = bidiByteAt(input.dirProps, i).toInt()
            when (property) {
                D.LEFT_TO_RIGHT_EMBEDDING, D.RIGHT_TO_LEFT_EMBEDDING, D.LEFT_TO_RIGHT_OVERRIDE, D.RIGHT_TO_LEFT_OVERRIDE -> {
                    input.flags = input.flags or flag(D.BOUNDARY_NEUTRAL)
                    input.levels[i] = previousLevel
                    val newLevel = if (property == D.LEFT_TO_RIGHT_EMBEDDING || property == D.LEFT_TO_RIGHT_OVERRIDE)
                        ((embeddingLevel + 2) and (OVERRIDE or 1).inv()).toByte()
                    else ((noOverride(embeddingLevel) + 1) or 1).toByte()
                    if (newLevel <= 125 && overflowIsolateCount == 0 && overflowEmbeddingCount == 0) {
                        lastCcPos = i
                        embeddingLevel = newLevel
                        if (property == D.LEFT_TO_RIGHT_OVERRIDE || property == D.RIGHT_TO_LEFT_OVERRIDE) embeddingLevel = (embeddingLevel.toInt() or OVERRIDE).toByte()
                        stack[++stackLast] = embeddingLevel.toShort()
                    } else if (overflowIsolateCount == 0) ++overflowEmbeddingCount
                }
                D.POP_DIRECTIONAL_FORMAT -> {
                    input.flags = input.flags or flag(D.BOUNDARY_NEUTRAL)
                    input.levels[i] = previousLevel
                    if (overflowIsolateCount > 0) continue
                    if (overflowEmbeddingCount > 0) { --overflowEmbeddingCount; continue }
                    if (stackLast > 0 && stack[stackLast] < ISOLATE) {
                        lastCcPos = i
                        embeddingLevel = stack[--stackLast].toByte()
                    }
                }
                D.LEFT_TO_RIGHT_ISOLATE.toInt(), D.RIGHT_TO_LEFT_ISOLATE.toInt() -> {
                    input.flags = input.flags or flag(D.OTHER_NEUTRAL) or flagLR(embeddingLevel)
                    input.levels[i] = noOverride(embeddingLevel)
                    if (noOverride(embeddingLevel) != noOverride(previousLevel)) {
                        brackets.processBoundary(lastCcPos, previousLevel, embeddingLevel)
                        input.flags = input.flags or MULTI_RUNS
                    }
                    previousLevel = embeddingLevel
                    val newLevel = if (property == D.LEFT_TO_RIGHT_ISOLATE.toInt()) ((embeddingLevel + 2) and (OVERRIDE or 1).inv()).toByte()
                        else ((noOverride(embeddingLevel) + 1) or 1).toByte()
                    if (newLevel <= 125 && overflowIsolateCount == 0 && overflowEmbeddingCount == 0) {
                        input.flags = input.flags or flag(property)
                        lastCcPos = i
                        ++validIsolateCount
                        if (validIsolateCount > input.isolateCount) input.isolateCount = validIsolateCount
                        embeddingLevel = newLevel
                        stack[++stackLast] = (embeddingLevel + ISOLATE).toShort()
                        brackets.processLriRli(embeddingLevel)
                    } else {
                        input.dirProps[i] = D.WHITE_SPACE_NEUTRAL.toByte()
                        ++overflowIsolateCount
                    }
                }
                D.POP_DIRECTIONAL_ISOLATE.toInt() -> {
                    if (noOverride(embeddingLevel) != noOverride(previousLevel)) {
                        brackets.processBoundary(lastCcPos, previousLevel, embeddingLevel)
                        input.flags = input.flags or MULTI_RUNS
                    }
                    if (overflowIsolateCount > 0) {
                        --overflowIsolateCount
                        input.dirProps[i] = D.WHITE_SPACE_NEUTRAL.toByte()
                    } else if (validIsolateCount > 0) {
                        input.flags = input.flags or flag(D.POP_DIRECTIONAL_ISOLATE.toInt())
                        lastCcPos = i
                        overflowEmbeddingCount = 0
                        while (stack[stackLast] < ISOLATE) --stackLast
                        --stackLast
                        --validIsolateCount
                        brackets.processPdi()
                    } else input.dirProps[i] = D.WHITE_SPACE_NEUTRAL.toByte()
                    embeddingLevel = (stack[stackLast].toInt() and ISOLATE.inv()).toByte()
                    input.flags = input.flags or flag(D.OTHER_NEUTRAL) or flagLR(embeddingLevel)
                    previousLevel = embeddingLevel
                    input.levels[i] = noOverride(embeddingLevel)
                }
                D.BLOCK_SEPARATOR -> {
                    input.flags = input.flags or flag(D.BLOCK_SEPARATOR)
                    input.levels[i] = input.getParaLevelAt(i)
                    if (i + 1 < input.length) {
                        if (bidiCharAt(input.text, i) == '\r' && bidiCharAt(input.text, i + 1) == '\n') continue
                        overflowEmbeddingCount = 0
                        overflowIsolateCount = 0
                        validIsolateCount = 0
                        stackLast = 0
                        embeddingLevel = input.getParaLevelAt(i + 1)
                        previousLevel = embeddingLevel
                        stack[0] = embeddingLevel.toShort()
                        brackets.processB(embeddingLevel)
                    }
                }
                D.BOUNDARY_NEUTRAL -> {
                    input.levels[i] = previousLevel
                    input.flags = input.flags or flag(D.BOUNDARY_NEUTRAL)
                }
                else -> {
                    if (noOverride(embeddingLevel) != noOverride(previousLevel)) {
                        brackets.processBoundary(lastCcPos, previousLevel, embeddingLevel)
                        input.flags = input.flags or MULTI_RUNS
                        input.flags = input.flags or if ((embeddingLevel.toInt() and OVERRIDE) != 0) flagO(embeddingLevel) else flagE(embeddingLevel)
                    }
                    previousLevel = embeddingLevel
                    input.levels[i] = embeddingLevel
                    brackets.processChar(i)
                    input.flags = input.flags or flag(bidiByteAt(input.dirProps, i).toInt())
                }
            }
        }
        if ((input.flags and MASK_EMBEDDING) != 0) input.flags = input.flags or flagLR(input.paraLevel)
        if (input.orderParagraphsLTR && (input.flags and flag(D.BLOCK_SEPARATOR)) != 0) input.flags = input.flags or flag(D.LEFT_TO_RIGHT)
        return directionFromFlags()
    }
    fun check(): Byte {
        var isolateCount = 0
        input.flags = 0
        input.isolateCount = 0
        var currentParaIndex = 0
        var currentParaLimit = input.paraLimits[0]
        var currentParaLevel = input.paraLevel
        for (i in 0 until input.length) {
            var level = bidiByteAt(input.levels, i)
            val property = bidiByteAt(input.dirProps, i).toInt()
            if (property == D.LEFT_TO_RIGHT_ISOLATE.toInt() || property == D.RIGHT_TO_LEFT_ISOLATE.toInt()) {
                ++isolateCount
                if (isolateCount > input.isolateCount) input.isolateCount = isolateCount
            } else if (property == D.POP_DIRECTIONAL_ISOLATE.toInt()) --isolateCount
            else if (property == D.BLOCK_SEPARATOR) isolateCount = 0
            if (input.defaultParaLevel.toInt() != 0 && i == currentParaLimit && currentParaIndex + 1 < input.paraCount) {
                currentParaLevel = input.paraLevels[++currentParaIndex]
                currentParaLimit = input.paraLimits[currentParaIndex]
            }
            val overrideFlag = level.toInt() and OVERRIDE
            level = noOverride(level)
            if (level < currentParaLevel || 125 < level) {
                if (level.toInt() == 0) {
                    if (property != D.BLOCK_SEPARATOR) {
                        level = currentParaLevel
                        input.levels[i] = (level.toInt() or overrideFlag).toByte()
                    }
                } else throw IllegalArgumentException("level $level out of bounds at $i")
            }
            input.flags = input.flags or if (overrideFlag != 0) flagO(level) else flagE(level) or flag(property)
        }
        if ((input.flags and MASK_EMBEDDING) != 0) input.flags = input.flags or flagLR(input.paraLevel)
        return directionFromFlags()
    }
    private companion object {
        const val OVERRIDE = -128
        const val ISOLATE = 0x100
        val MULTI_RUNS = 1 shl 31
        fun flag(property: Int) = 1 shl property
        fun flagLR(level: Byte) = flag(if ((level.toInt() and 1) == 0) D.LEFT_TO_RIGHT else D.RIGHT_TO_LEFT)
        fun flagE(level: Byte) = flag(if ((level.toInt() and 1) == 0) D.LEFT_TO_RIGHT_EMBEDDING else D.RIGHT_TO_LEFT_EMBEDDING)
        fun flagO(level: Byte) = flag(if ((level.toInt() and 1) == 0) D.LEFT_TO_RIGHT_OVERRIDE else D.RIGHT_TO_LEFT_OVERRIDE)
        fun noOverride(level: Byte) = (level.toInt() and OVERRIDE.inv()).toByte()
        val MASK_LTR = flag(D.LEFT_TO_RIGHT) or flag(D.EUROPEAN_NUMBER) or flag(23) or flag(24) or flag(D.ARABIC_NUMBER) or flag(D.LEFT_TO_RIGHT_EMBEDDING) or flag(D.LEFT_TO_RIGHT_OVERRIDE) or flag(D.LEFT_TO_RIGHT_ISOLATE.toInt())
        val MASK_RTL = flag(D.RIGHT_TO_LEFT) or flag(D.RIGHT_TO_LEFT_ARABIC) or flag(D.RIGHT_TO_LEFT_EMBEDDING) or flag(D.RIGHT_TO_LEFT_OVERRIDE) or flag(D.RIGHT_TO_LEFT_ISOLATE.toInt())
        val MASK_EXPLICIT = flag(D.LEFT_TO_RIGHT_EMBEDDING) or flag(D.LEFT_TO_RIGHT_OVERRIDE) or flag(D.RIGHT_TO_LEFT_EMBEDDING) or flag(D.RIGHT_TO_LEFT_OVERRIDE) or flag(D.POP_DIRECTIONAL_FORMAT)
        val MASK_ISO = flag(D.LEFT_TO_RIGHT_ISOLATE.toInt()) or flag(D.RIGHT_TO_LEFT_ISOLATE.toInt()) or flag(D.FIRST_STRONG_ISOLATE.toInt()) or flag(D.POP_DIRECTIONAL_ISOLATE.toInt())
        val MASK_WS = flag(D.BLOCK_SEPARATOR) or flag(D.SEGMENT_SEPARATOR) or flag(D.WHITE_SPACE_NEUTRAL) or flag(D.BOUNDARY_NEUTRAL) or MASK_EXPLICIT or MASK_ISO
        val MASK_POSSIBLE_N = flag(D.OTHER_NEUTRAL) or flag(D.COMMON_NUMBER_SEPARATOR) or flag(D.EUROPEAN_NUMBER_SEPARATOR) or flag(D.EUROPEAN_NUMBER_TERMINATOR) or MASK_WS
        val MASK_EMBEDDING = flag(D.DIR_NON_SPACING_MARK) or MASK_POSSIBLE_N
    }
}
