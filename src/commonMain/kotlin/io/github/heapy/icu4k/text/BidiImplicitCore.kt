// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacterDirection as D
import io.github.heapy.icu4k.text.BidiImplicitTables.IMPTABLEVELS_RES
import io.github.heapy.icu4k.text.BidiImplicitTables.IMPTABPROPS_RES
import io.github.heapy.icu4k.text.BidiImplicitTables._AN
import io.github.heapy.icu4k.text.BidiImplicitTables._L
import io.github.heapy.icu4k.text.BidiImplicitTables._R
import io.github.heapy.icu4k.text.BidiImplicitTables._EN
import io.github.heapy.icu4k.text.BidiImplicitTables._ON
import io.github.heapy.icu4k.text.BidiImplicitTables._S

/** Implicit property/level state machines, insertion points and L1 adjustment. */
internal class BidiImplicitCore(private val input: Input) {
    internal interface Input : BidiPropertyCore.Input {
        val impTabPair: BidiImplicitTables.Pair?
        val epilogue: String?
        val insertPoints: InsertPoints
        var isolates: Array<Isolate?>?
        val trailingWSStart: Int
    }
    internal class Point(var pos: Int = 0, var flag: Int = 0)
    internal class InsertPoints {
        var size = 0
        var confirmed = 0
        var points = arrayOfNulls<Point>(0)
    }
    internal class Isolate {
        var startON = 0
        var start1 = 0
        var stateImp: Short = 0
        var state: Short = 0
    }
    internal class LevState {
        var impTab: Array<ByteArray>? = null
        var impAct: ShortArray? = null
        var startON = 0
        var startL2EN = 0
        var lastStrongRTL = 0
        var runStart = 0
        var state: Short = 0
        var runLevel: Byte = 0
    }
    private fun prop(index: Int) = bidiByteAt(input.dirProps, index).toInt()
    private fun level(index: Int) = bidiByteAt(input.levels, index)
    private fun putLevel(index: Int, value: Byte) {
        val levels = input.levels
        bidiByteAt(levels, index)
        levels[index] = value
    }
    fun prepareIsolates() {
        if (input.isolateCount > 0 && (input.isolates == null || input.isolates!!.size < input.isolateCount)) {
            input.isolates = arrayOfNulls(input.isolateCount + 3)
        }
        input.isolateCount = -1
    }
    fun addPoint(pos: Int, flag: Int) {
        val point = Point()
        val points = input.insertPoints
        var len = points.points.size
        if (len == 0) { points.points = arrayOfNulls(10); len = 10 }
        if (points.size >= len) {
            val saved = points.points
            points.points = arrayOfNulls(len * 2)
            saved.copyInto(points.points, 0, 0, len)
        }
        point.pos = pos; point.flag = flag
        bidiArrayAt(points.points, points.size)
        points.points[points.size] = point
        ++points.size
    }
    fun setLevelsOutsideIsolates(start: Int, limit: Int, level: Byte) {
        var isolateCount = 0
        for (k in start until limit) {
            val property = prop(k)
            if (property == D.POP_DIRECTIONAL_ISOLATE.toInt()) --isolateCount
            if (isolateCount == 0) putLevel(k, level)
            if (property == D.LEFT_TO_RIGHT_ISOLATE.toInt() || property == D.RIGHT_TO_LEFT_ISOLATE.toInt()) ++isolateCount
        }
    }
    fun processPropertySeq(state: LevState, property: Short, initialStart: Int, limit: Int) {
        var start = initialStart
        val table = state.impTab
        val actions = state.impAct
        val oldState = state.state
        val cell = bidiByteAt(bidiArrayAt(table, oldState.toInt()), property.toInt())
        state.state = (cell.toInt() and 0xf).toShort()
        val action = bidiShortAt(actions, cell.toInt() shr 4).toInt()
        val addLevel = bidiByteAt(bidiArrayAt(table, state.state.toInt()), IMPTABLEVELS_RES)
        val points = input.insertPoints
        when (action) {
            0 -> Unit
            1 -> state.startON = start
            2 -> start = state.startON
            3 -> setLevelsOutsideIsolates(state.startON, start, (state.runLevel + 1).toByte())
            4 -> setLevelsOutsideIsolates(state.startON, start, (state.runLevel + 2).toByte())
            5 -> {
                if (state.startL2EN >= 0) addPoint(state.startL2EN, 1)
                state.startL2EN = -1
                if (points.points.isEmpty() || points.size <= points.confirmed) {
                    state.lastStrongRTL = -1
                    val previousLevel = bidiByteAt(bidiArrayAt(table, oldState.toInt()), IMPTABLEVELS_RES)
                    if ((previousLevel.toInt() and 1) != 0 && state.startON > 0) start = state.startON
                    if (property.toInt() == _S) { addPoint(initialStart, 1); points.confirmed = points.size }
                } else {
                    for (k in state.lastStrongRTL + 1 until initialStart) putLevel(k, ((level(k) - 2) and 1.inv()).toByte())
                    points.confirmed = points.size
                    state.lastStrongRTL = -1
                    if (property.toInt() == _S) { addPoint(initialStart, 1); points.confirmed = points.size }
                }
            }
            6 -> {
                if (points.points.isNotEmpty()) points.size = points.confirmed
                state.startON = -1; state.startL2EN = -1; state.lastStrongRTL = limit - 1
            }
            7 -> {
                if (property.toInt() == _AN && prop(initialStart) == D.ARABIC_NUMBER && input.reorderingMode != 6) {
                    if (state.startL2EN == -1) state.lastStrongRTL = limit - 1
                    else {
                        if (state.startL2EN >= 0) { addPoint(state.startL2EN, 1); state.startL2EN = -2 }
                        addPoint(initialStart, 1)
                    }
                } else if (state.startL2EN == -1) state.startL2EN = initialStart
            }
            8 -> { state.lastStrongRTL = limit - 1; state.startON = -1 }
            9 -> {
                var k = initialStart - 1
                while (k >= 0 && (level(k).toInt() and 1) == 0) --k
                if (k >= 0) { addPoint(k, 4); points.confirmed = points.size }
                state.startON = initialStart
            }
            10 -> { addPoint(initialStart, 1); addPoint(initialStart, 2) }
            11 -> {
                points.size = points.confirmed
                if (property.toInt() == _S) { addPoint(initialStart, 4); points.confirmed = points.size }
            }
            12 -> {
                val newLevel = (state.runLevel + addLevel).toByte()
                for (k in state.startON until initialStart) if (level(k) < newLevel) putLevel(k, newLevel)
                points.confirmed = points.size
                state.startON = initialStart
            }
            13 -> {
                val runLevel = state.runLevel
                var k = initialStart - 1
                while (k >= state.startON) {
                    if (level(k).toInt() == runLevel + 3) {
                        while (level(k).toInt() == runLevel + 3) { val index = k--; putLevel(index, (level(index) - 2).toByte()) }
                        while (level(k) == runLevel) --k
                    }
                    if (level(k).toInt() == runLevel + 2) { putLevel(k, runLevel); --k; continue }
                    putLevel(k, (runLevel + 1).toByte())
                    --k
                }
            }
            14 -> {
                val newLevel = (state.runLevel + 1).toByte()
                for (k in initialStart - 1 downTo state.startON) if (level(k) > newLevel) putLevel(k, (level(k) - 2).toByte())
            }
            else -> throw IllegalStateException("Internal ICU error in processPropertySeq")
        }
        if (addLevel.toInt() != 0 || start < initialStart) {
            val newLevel = (state.runLevel + addLevel).toByte()
            if (start >= state.runStart) for (k in start until limit) putLevel(k, newLevel)
            else setLevelsOutsideIsolates(start, limit, newLevel)
        }
    }
    private fun lastStrongInPrologue(): Byte {
        var i = input.prologue!!.length
        while (i > 0) {
            val c = codePointBefore(input.prologue!!, i)
            i -= UTF16.getCharCount(c)
            when (input.getCustomizedClass(c).toByte().toInt()) {
                D.LEFT_TO_RIGHT -> return _L.toByte()
                D.RIGHT_TO_LEFT, D.RIGHT_TO_LEFT_ARABIC -> return _R.toByte()
                D.BLOCK_SEPARATOR -> return _ON.toByte()
            }
        }
        return _ON.toByte()
    }
    private fun firstStrongInEpilogue(): Byte {
        var i = 0
        while (i < input.epilogue!!.length) {
            val c = codePointAt(input.epilogue!!, i)
            i += UTF16.getCharCount(c)
            when (input.getCustomizedClass(c).toByte().toInt()) {
                D.LEFT_TO_RIGHT -> return _L.toByte()
                D.RIGHT_TO_LEFT, D.RIGHT_TO_LEFT_ARABIC -> return _R.toByte()
                D.EUROPEAN_NUMBER -> return _EN.toByte()
                D.ARABIC_NUMBER -> return _AN.toByte()
            }
        }
        return _ON.toByte()
    }
    fun resolve(start: Int, limit: Int, initialSor: Short, initialEor: Short) {
        var sor = initialSor
        var eor = initialEor
        val state = LevState()
        val inverseRTL = start < input.lastArabicPos && (input.getParaLevelAt(start).toInt() and 1) > 0 && (input.reorderingMode == 5 || input.reorderingMode == 6)
        var nextStrongProp = D.RIGHT_TO_LEFT
        var nextStrongPos = -1
        state.startL2EN = -1; state.lastStrongRTL = -1; state.runStart = start; state.runLevel = level(start)
        state.impTab = bidiArrayAt(input.impTabPair!!.imptab, state.runLevel.toInt() and 1)
        state.impAct = bidiArrayAt(input.impTabPair!!.impact, state.runLevel.toInt() and 1)
        if (start == 0 && input.prologue != null) {
            val lastStrong = lastStrongInPrologue()
            if (lastStrong.toInt() != _ON) sor = lastStrong.toShort()
        }
        var start1: Int
        var stateImp: Short
        if (prop(start) == D.POP_DIRECTIONAL_ISOLATE.toInt()) {
            state.startON = bidiArrayAt(input.isolates, input.isolateCount)!!.startON
            start1 = bidiArrayAt(input.isolates, input.isolateCount)!!.start1
            stateImp = bidiArrayAt(input.isolates, input.isolateCount)!!.stateImp
            state.state = bidiArrayAt(input.isolates, input.isolateCount)!!.state
            --input.isolateCount
        } else {
            state.startON = -1; start1 = start
            stateImp = if (prop(start) == D.DIR_NON_SPACING_MARK) (1 + sor).toShort() else 0
            state.state = 0
            processPropertySeq(state, sor, start, start)
        }
        var start2 = start
        var i = start
        while (i <= limit) {
            val group: Short
            if (i >= limit) {
                var k = limit - 1
                while (k > start && (flag(prop(k)) and MASK_BN_EXPLICIT) != 0) --k
                val property = prop(k)
                if (property == D.LEFT_TO_RIGHT_ISOLATE.toInt() || property == D.RIGHT_TO_LEFT_ISOLATE.toInt()) break
                group = eor
            } else {
                var property = prop(i)
                if (property == D.BLOCK_SEPARATOR) input.isolateCount = -1
                if (inverseRTL) {
                    if (property == D.RIGHT_TO_LEFT_ARABIC) property = D.RIGHT_TO_LEFT
                    else if (property == D.EUROPEAN_NUMBER) {
                        if (nextStrongPos <= i) {
                            nextStrongProp = D.RIGHT_TO_LEFT; nextStrongPos = limit
                            for (j in i + 1 until limit) {
                                val next = prop(j)
                                if (next == D.LEFT_TO_RIGHT || next == D.RIGHT_TO_LEFT || next == D.RIGHT_TO_LEFT_ARABIC) {
                                    nextStrongProp = next; nextStrongPos = j; break
                                }
                            }
                        }
                        if (nextStrongProp == D.RIGHT_TO_LEFT_ARABIC) property = D.ARABIC_NUMBER
                    }
                }
                group = bidiShortAt(BidiImplicitTables.groupProp, property)
            }
            val oldState = stateImp
            val cell = bidiShortAt(bidiArrayAt(BidiImplicitTables.impTabProps, oldState.toInt()), group.toInt())
            stateImp = (cell.toInt() and 0x1f).toShort()
            var action = cell.toInt() shr 5
            if (i == limit && action == 0) action = 1
            if (action != 0) {
                val result = bidiShortAt(bidiArrayAt(BidiImplicitTables.impTabProps, oldState.toInt()), IMPTABPROPS_RES)
                when (action) {
                    1 -> { processPropertySeq(state, result, start1, i); start1 = i }
                    2 -> start2 = i
                    3 -> { processPropertySeq(state, result, start1, start2); processPropertySeq(state, _ON.toShort(), start2, i); start1 = i }
                    4 -> { processPropertySeq(state, result, start1, start2); start1 = start2; start2 = i }
                    else -> throw IllegalStateException("Internal ICU error in resolveImplicitLevels")
                }
            }
            ++i
        }
        if (limit == input.length && input.epilogue != null) {
            val firstStrong = firstStrongInEpilogue()
            if (firstStrong.toInt() != _ON) eor = firstStrong.toShort()
        }
        i = limit - 1
        while (i > start && (flag(prop(i)) and MASK_BN_EXPLICIT) != 0) --i
        val property = prop(i)
        if ((property == D.LEFT_TO_RIGHT_ISOLATE.toInt() || property == D.RIGHT_TO_LEFT_ISOLATE.toInt()) && limit < input.length) {
            ++input.isolateCount
            if (bidiArrayAt(input.isolates, input.isolateCount) == null) input.isolates!![input.isolateCount] = Isolate()
            bidiArrayAt(input.isolates, input.isolateCount)!!.stateImp = stateImp
            bidiArrayAt(input.isolates, input.isolateCount)!!.state = state.state
            bidiArrayAt(input.isolates, input.isolateCount)!!.start1 = start1
            bidiArrayAt(input.isolates, input.isolateCount)!!.startON = state.startON
        } else processPropertySeq(state, eor, limit, limit)
    }
    /** Source setPara mixed-direction run scheduling, after prepareIsolates. */
    fun resolveRuns(embeddingLevelsSupplied: Boolean) {
        if (!embeddingLevelsSupplied && input.paraCount <= 1 && (input.flags and (1 shl 31)) == 0) {
            resolve(0, input.length, parity(input.getParaLevelAt(0)), parity(input.getParaLevelAt(input.length - 1)))
        } else {
            var limit = 0
            var currentLevel = input.getParaLevelAt(0)
            var nextLevel = level(0)
            var eor = if (currentLevel < nextLevel) parity(nextLevel) else parity(currentLevel)
            do {
                var start = limit
                currentLevel = nextLevel
                val sor = if (start > 0 && prop(start - 1) == D.BLOCK_SEPARATOR) parity(input.getParaLevelAt(start)) else eor
                do { ++limit } while (limit < input.length && (level(limit) == currentLevel || (flag(prop(limit)) and MASK_BN_EXPLICIT) != 0))
                nextLevel = if (limit < input.length) level(limit) else input.getParaLevelAt(input.length - 1)
                eor = if (noOverride(currentLevel) < noOverride(nextLevel)) parity(nextLevel) else parity(currentLevel)
                if ((currentLevel.toInt() and -128) == 0) resolve(start, limit, sor, eor)
                else do { putLevel(start, noOverride(level(start))); ++start } while (start < limit)
            } while (limit < input.length)
        }
        adjustWSLevels()
    }
    fun adjustWSLevels() {
        if ((input.flags and MASK_WS) == 0) return
        var i = input.trailingWSStart
        while (i > 0) {
            while (i > 0) {
                val flag = flag(prop(--i))
                if ((flag and MASK_WS) == 0) break
                putLevel(i, if (input.orderParagraphsLTR && (flag and flag(D.BLOCK_SEPARATOR)) != 0) 0 else input.getParaLevelAt(i))
            }
            while (i > 0) {
                val flag = flag(prop(--i))
                if ((flag and MASK_BN_EXPLICIT) != 0) putLevel(i, level(i + 1))
                else if (input.orderParagraphsLTR && (flag and flag(D.BLOCK_SEPARATOR)) != 0) { putLevel(i, 0); break }
                else if ((flag and MASK_B_S) != 0) { putLevel(i, input.getParaLevelAt(i)); break }
            }
        }
    }
    fun addDefaultInverseMarks() {
        if (input.defaultParaLevel <= 0 || (input.reorderingOptions and 1) == 0 || (input.reorderingMode != 5 && input.reorderingMode != 6)) return
        for (paragraph in 0 until input.paraCount) {
            var last = input.paraLimits[paragraph] - 1
            if (bidiByteAt(input.paraLevels, paragraph).toInt() == 0) continue
            val start = if (paragraph == 0) 0 else input.paraLimits[paragraph - 1]
            for (j in last downTo start) {
                val property = prop(j)
                if (property == D.LEFT_TO_RIGHT) {
                    if (j < last) while (prop(last) == D.BLOCK_SEPARATOR) --last
                    addPoint(last, 4)
                    break
                }
                if ((flag(property) and (flag(D.RIGHT_TO_LEFT) or flag(D.RIGHT_TO_LEFT_ARABIC))) != 0) break
            }
        }
    }
    private companion object {
        fun flag(property: Int) = 1 shl property
        fun parity(level: Byte) = (level.toInt() and 1).toShort()
        fun noOverride(level: Byte) = (level.toInt() and 127).toByte()
        val MASK_BN_EXPLICIT = flag(D.BOUNDARY_NEUTRAL) or flag(D.LEFT_TO_RIGHT_EMBEDDING) or flag(D.LEFT_TO_RIGHT_OVERRIDE) or flag(D.RIGHT_TO_LEFT_EMBEDDING) or flag(D.RIGHT_TO_LEFT_OVERRIDE) or flag(D.POP_DIRECTIONAL_FORMAT)
        val MASK_B_S = flag(D.BLOCK_SEPARATOR) or flag(D.SEGMENT_SEPARATOR)
        val MASK_WS = MASK_B_S or flag(D.WHITE_SPACE_NEUTRAL) or MASK_BN_EXPLICIT or flag(D.LEFT_TO_RIGHT_ISOLATE.toInt()) or flag(D.RIGHT_TO_LEFT_ISOLATE.toInt()) or flag(D.FIRST_STRONG_ISOLATE.toInt()) or flag(D.POP_DIRECTIONAL_ISOLATE.toInt())
        fun codePointAt(text: String, index: Int): Int {
            val first = bidiStringCharAt(text, index)
            if (UTF16.isLeadSurrogate(first.code) && index + 1 < text.length) {
                val second = bidiStringCharAt(text, index + 1)
                if (UTF16.isTrailSurrogate(second.code)) return ((first.code - 0xd800) shl 10) + second.code - 0xdc00 + 0x10000
            }
            return first.code
        }
        fun codePointBefore(text: String, index: Int): Int {
            val last = bidiStringCharAt(text, index - 1)
            if (UTF16.isTrailSurrogate(last.code) && index - 1 > 0) {
                val first = bidiStringCharAt(text, index - 2)
                if (UTF16.isLeadSurrogate(first.code)) return ((first.code - 0xd800) shl 10) + last.code - 0xdc00 + 0x10000
            }
            return last.code
        }
    }
}
internal expect fun bidiShortAt(data: ShortArray?, index: Int): Short
internal expect fun <T> bidiArrayAt(data: Array<T>?, index: Int): T
