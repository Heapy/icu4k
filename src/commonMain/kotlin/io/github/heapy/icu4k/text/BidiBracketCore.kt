// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.LEFT_TO_RIGHT
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.RIGHT_TO_LEFT
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.OTHER_NEUTRAL
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.SEGMENT_SEPARATOR
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.WHITE_SPACE_NEUTRAL
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.RIGHT_TO_LEFT_ARABIC
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.EUROPEAN_NUMBER
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.ARABIC_NUMBER
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.DIR_NON_SPACING_MARK
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.POP_DIRECTIONAL_ISOLATE
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.LEFT_TO_RIGHT_ISOLATE
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.RIGHT_TO_LEFT_ISOLATE
import io.github.heapy.icu4k.lang.UCharacterDirection.Companion.FIRST_STRONG_ISOLATE
import io.github.heapy.icu4k.lang.UProperty

/** Mutable N0 bracket state used while the paragraph resolver assigns explicit levels. */
internal class BidiBracketCore(private val input: Input) {
    internal interface Input {
        val text: CharArray
        val dirProps: ByteArray
        val levels: ByteArray
        val reorderingMode: Int
        fun getParaLevelAt(index: Int): Byte
    }
    internal class Opening {
        var position = 0
        var match = 0
        var contextPos = 0
        var flags: Short = 0
        var contextDir: Byte = 0
    }
    internal class IsoRun {
        var contextPos = 0
        var start: Short = 0
        var limit: Short = 0
        var level: Byte = 0
        var lastStrong: Byte = 0
        var lastBase: Byte = 0
        var contextDir: Byte = 0
    }
    var openings = arrayOfNulls<Opening>(20)
        private set
    var isoRunLast = 0
        private set
    val isoRuns = arrayOfNulls<IsoRun>(127)
    var isNumbersSpecial = false
        private set

    // Explicit checks also retain source bounds failures on JS and Native.
    private fun run(index: Int = isoRunLast): IsoRun {
        if (index !in isoRuns.indices) throw IndexOutOfBoundsException()
        return isoRuns[index]!!
    }
    private fun opening(index: Int): Opening {
        if (index !in openings.indices) throw IndexOutOfBoundsException()
        return openings[index]!!
    }
    fun init() {
        isoRunLast = 0
        val run = IsoRun()
        isoRuns[0] = run
        run.start = 0
        run.limit = 0
        run.level = input.getParaLevelAt(0)
        val direction = (input.getParaLevelAt(0).toInt() and 1).toByte()
        run.lastStrong = direction
        run.lastBase = direction
        run.contextDir = direction
        run.contextPos = 0
        openings = arrayOfNulls(20)
        isNumbersSpecial = input.reorderingMode == 1 || input.reorderingMode == 6
    }
    fun processB(level: Byte) {
        isoRunLast = 0
        val run = run(0)
        run.limit = 0
        run.level = level
        val direction = (level.toInt() and 1).toByte()
        run.lastStrong = direction
        run.lastBase = direction
        run.contextDir = direction
        run.contextPos = 0
    }
    fun processBoundary(lastCcPos: Int, contextLevel: Byte, embeddingLevel: Byte) {
        val run = run()
        if (((1 shl bidiByteAt(input.dirProps, lastCcPos).toInt()) and MASK_ISO) != 0) return
        var context = contextLevel
        if (noOverride(embeddingLevel) > noOverride(context)) context = embeddingLevel
        run.limit = run.start
        run.level = embeddingLevel
        val direction = (context.toInt() and 1).toByte()
        run.lastStrong = direction
        run.lastBase = direction
        run.contextDir = direction
        run.contextPos = lastCcPos
    }
    fun processLriRli(level: Byte) {
        var run = run()
        run.lastBase = OTHER_NEUTRAL.toByte()
        val lastLimit = run.limit
        ++isoRunLast
        if (isoRunLast !in isoRuns.indices) throw IndexOutOfBoundsException()
        run = isoRuns[isoRunLast] ?: IsoRun().also { isoRuns[isoRunLast] = it }
        run.start = lastLimit
        run.limit = lastLimit
        run.level = level
        val direction = (level.toInt() and 1).toByte()
        run.lastStrong = direction
        run.lastBase = direction
        run.contextDir = direction
        run.contextPos = 0
    }
    fun processPdi() {
        --isoRunLast
        run().lastBase = OTHER_NEUTRAL.toByte()
    }
    private fun addOpening(match: Char, position: Int) {
        val run = run()
        if (run.limit >= openings.size) {
            val saved = openings
            openings = arrayOfNulls(saved.size * 2)
            saved.copyInto(openings)
        }
        val index = run.limit.toInt()
        if (index !in openings.indices) throw IndexOutOfBoundsException()
        val opening = openings[index] ?: Opening().also { openings[index] = it }
        opening.position = position
        opening.match = match.code
        opening.contextDir = run.contextDir
        opening.contextPos = run.contextPos
        opening.flags = 0
        run.limit = (run.limit + 1).toShort()
    }
    private fun fixN0c(openingIndex: Int, newPropPosition: Int, newProp: Byte) {
        val run = run()
        for (k in openingIndex + 1 until run.limit.toInt()) {
            val opening = opening(k)
            if (opening.match >= 0) continue
            if (newPropPosition < opening.contextPos) break
            if (newPropPosition >= opening.position) continue
            if (newProp == opening.contextDir) break
            val openingPosition = opening.position
            input.dirProps[openingPosition] = newProp
            val closingPosition = -opening.match
            input.dirProps[closingPosition] = newProp
            opening.match = 0
            fixN0c(k, openingPosition, newProp)
            fixN0c(k, closingPosition, newProp)
        }
    }
    private fun processClosing(openIdx: Int, position: Int): Byte {
        val run = run()
        val opening = opening(openIdx)
        val direction = (run.level.toInt() and 1).toByte()
        var stable = true
        val newProp: Byte
        if ((direction.toInt() == 0 && (opening.flags.toInt() and FOUND_L) > 0) ||
            (direction.toInt() == 1 && (opening.flags.toInt() and FOUND_R) > 0)) {
            newProp = direction
        } else if ((opening.flags.toInt() and (FOUND_L or FOUND_R)) != 0) {
            stable = openIdx == run.start.toInt()
            newProp = if (direction != opening.contextDir) opening.contextDir else direction
        } else {
            run.limit = openIdx.toShort()
            return OTHER_NEUTRAL.toByte()
        }
        input.dirProps[opening.position] = newProp
        input.dirProps[position] = newProp
        fixN0c(openIdx, opening.position, newProp)
        if (stable) {
            run.limit = openIdx.toShort()
            while (run.limit > run.start && opening(run.limit - 1).position == opening.position) {
                run.limit = (run.limit - 1).toShort()
            }
        } else {
            opening.match = -position
            var k = openIdx - 1
            while (k >= run.start && opening(k).position == opening.position) {
                opening(k--).match = 0
            }
            for (i in openIdx + 1 until run.limit.toInt()) {
                val next = opening(i)
                if (next.position >= position) break
                if (next.match > 0) next.match = 0
            }
        }
        return newProp
    }
    fun processChar(position: Int) {
        val run = run()
        val dirProp = bidiByteAt(input.dirProps, position)
        var newProp: Byte
        if (dirProp.toInt() == OTHER_NEUTRAL) {
            var c = bidiCharAt(input.text, position)
            for (idx in run.limit - 1 downTo run.start.toInt()) {
                if (opening(idx).match != c.code) continue
                newProp = processClosing(idx, position)
                if (newProp.toInt() == OTHER_NEUTRAL) {
                    c = '\u0000'
                    break
                }
                run.lastBase = OTHER_NEUTRAL.toByte()
                run.contextDir = newProp
                run.contextPos = position
                val level = bidiByteAt(input.levels, position)
                if ((level.toInt() and LEVEL_OVERRIDE) != 0) {
                    newProp = (level.toInt() and 1).toByte()
                    run.lastStrong = newProp
                    val flag = (1 shl newProp.toInt()).toShort()
                    for (i in run.start.toInt() until idx) {
                        val previous = opening(i)
                        previous.flags = (previous.flags.toInt() or flag.toInt()).toShort()
                    }
                    input.levels[position] = (level.toInt() and LEVEL_OVERRIDE.inv()).toByte()
                }
                val openingPosition = opening(idx).position
                input.levels[openingPosition] = (bidiByteAt(input.levels, openingPosition).toInt() and LEVEL_OVERRIDE.inv()).toByte()
                return
            }
            val match = if (c.code != 0) UCharacter.getBidiPairedBracket(c.code).toChar() else '\u0000'
            if (match != c && UCharacter.getIntPropertyValue(c.code, UProperty.BIDI_PAIRED_BRACKET_TYPE) == UCharacter.BidiPairedBracketType.OPEN) {
                if (match.code == 0x232a) addOpening('\u3009', position)
                else if (match.code == 0x3009) addOpening('\u232a', position)
                addOpening(match, position)
            }
        }
        val level = bidiByteAt(input.levels, position)
        if ((level.toInt() and LEVEL_OVERRIDE) != 0) {
            newProp = (level.toInt() and 1).toByte()
            if (dirProp.toInt() != SEGMENT_SEPARATOR && dirProp.toInt() != WHITE_SPACE_NEUTRAL && dirProp.toInt() != OTHER_NEUTRAL) {
                input.dirProps[position] = newProp
            }
            run.lastBase = newProp
            run.lastStrong = newProp
            run.contextDir = newProp
            run.contextPos = position
        } else if (dirProp <= RIGHT_TO_LEFT || dirProp.toInt() == RIGHT_TO_LEFT_ARABIC) {
            newProp = fromStrong(dirProp)
            run.lastBase = dirProp
            run.lastStrong = dirProp
            run.contextDir = newProp
            run.contextPos = position
        } else if (dirProp.toInt() == EUROPEAN_NUMBER) {
            run.lastBase = EUROPEAN_NUMBER.toByte()
            if (run.lastStrong.toInt() == LEFT_TO_RIGHT) {
                newProp = LEFT_TO_RIGHT.toByte()
                if (!isNumbersSpecial) input.dirProps[position] = ENL.toByte()
                run.contextDir = LEFT_TO_RIGHT.toByte()
                run.contextPos = position
            } else {
                newProp = RIGHT_TO_LEFT.toByte()
                input.dirProps[position] = (if (run.lastStrong.toInt() == RIGHT_TO_LEFT_ARABIC) ARABIC_NUMBER else ENR).toByte()
                run.contextDir = RIGHT_TO_LEFT.toByte()
                run.contextPos = position
            }
        } else if (dirProp.toInt() == ARABIC_NUMBER) {
            newProp = RIGHT_TO_LEFT.toByte()
            run.lastBase = ARABIC_NUMBER.toByte()
            run.contextDir = RIGHT_TO_LEFT.toByte()
            run.contextPos = position
        } else if (dirProp.toInt() == DIR_NON_SPACING_MARK) {
            newProp = run.lastBase
            if (newProp.toInt() == OTHER_NEUTRAL) input.dirProps[position] = newProp
        } else {
            newProp = dirProp
            run.lastBase = dirProp
        }
        if (newProp <= RIGHT_TO_LEFT || newProp.toInt() == RIGHT_TO_LEFT_ARABIC) {
            val flag = (1 shl fromStrong(newProp).toInt()).toShort()
            for (i in run.start.toInt() until run.limit.toInt()) {
                val opening = opening(i)
                if (position > opening.position) opening.flags = (opening.flags.toInt() or flag.toInt()).toShort()
            }
        }
    }
    private fun fromStrong(strong: Byte): Byte = if (strong.toInt() == LEFT_TO_RIGHT) LEFT_TO_RIGHT.toByte() else RIGHT_TO_LEFT.toByte()
    private fun noOverride(level: Byte): Byte = (level.toInt() and LEVEL_OVERRIDE.inv()).toByte()
    private companion object {
        const val LEVEL_OVERRIDE = -128
        const val ENL = POP_DIRECTIONAL_ISOLATE + 1
        const val ENR = ENL + 1
        const val FOUND_L = 1 shl LEFT_TO_RIGHT
        const val FOUND_R = 1 shl RIGHT_TO_LEFT
        val MASK_ISO = (1 shl LEFT_TO_RIGHT_ISOLATE.toInt()) or (1 shl RIGHT_TO_LEFT_ISOLATE.toInt()) or
            (1 shl FIRST_STRONG_ISOLATE.toInt()) or (1 shl POP_DIRECTIONAL_ISOLATE.toInt())
    }
}
