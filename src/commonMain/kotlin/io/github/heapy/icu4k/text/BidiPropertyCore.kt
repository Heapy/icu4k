// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacterDirection as D
import io.github.heapy.icu4k.impl.UBiDiProps

/** Paragraph P2/P3, FSI direction and UTF-16 property preparation for the resolver. */
internal class BidiPropertyCore(private val input: Input) {
    internal interface Input : BidiExplicitCore.Input {
        val originalLength: Int
        val reorderingOptions: Int
        val prologue: String?
        override var length: Int
        override var paraCount: Int
        override var paraLevel: Byte
        override var paraLimits: IntArray
        override var paraLevels: ByteArray
        var lastArabicPos: Int
        var controlCount: Int
        fun getCustomizedClass(c: Int): Int
    }
    private fun putParaLevel(index: Int, value: Byte) {
        val levels = input.paraLevels
        bidiByteAt(levels, index)
        levels[index] = value
    }
    private fun checkParaCount() {
        val count = input.paraCount
        if (count <= input.paraLevels.size) return
        val oldLength = input.paraLevels.size
        val oldLimits = input.paraLimits
        val oldLevels = input.paraLevels
        input.paraLimits = IntArray(count * 2)
        input.paraLevels = ByteArray(count * 2)
        oldLimits.copyInto(input.paraLimits, 0, 0, oldLength)
        oldLevels.copyInto(input.paraLevels, 0, 0, oldLength)
    }
    private fun firstStrongInPrologue(): Byte {
        var result = D.OTHER_NEUTRAL.toByte()
        var i = 0
        while (i < input.prologue!!.length) {
            val c = codePointAtForward(input.prologue!!, i)
            i += UTF16.getCharCount(c)
            val property = input.getCustomizedClass(c).toByte()
            if (result.toInt() == D.OTHER_NEUTRAL) {
                if (property.toInt() == D.LEFT_TO_RIGHT || property.toInt() == D.RIGHT_TO_LEFT || property.toInt() == D.RIGHT_TO_LEFT_ARABIC) result = property
            } else if (property.toInt() == D.BLOCK_SEPARATOR) result = D.OTHER_NEUTRAL.toByte()
        }
        return result
    }
    fun scan() {
        input.flags = 0
        val isDefaultLevel = (input.paraLevel.toInt() and 126) == 126
        val isDefaultLevelInverse = isDefaultLevel && (input.reorderingMode == 5 || input.reorderingMode == 6)
        input.lastArabicPos = -1
        var controlCount = 0
        val removeControls = (input.reorderingOptions and 2) != 0
        var lastStrong = D.OTHER_NEUTRAL.toByte()
        val isolateStartStack = IntArray(126)
        val previousStateStack = ByteArray(126)
        var stackLast = -1
        if ((input.reorderingOptions and 4) != 0) input.length = 0
        val defaultParaLevel = (input.paraLevel.toInt() and 1).toByte()
        var state: Int
        if (isDefaultLevel) {
            putParaLevel(0, defaultParaLevel)
            lastStrong = defaultParaLevel
            val property = if (input.prologue != null) firstStrongInPrologue() else D.OTHER_NEUTRAL.toByte()
            if (property.toInt() != D.OTHER_NEUTRAL) {
                putParaLevel(0, if (property.toInt() == D.LEFT_TO_RIGHT) 0 else 1)
                state = NOT_SEEKING_STRONG
            } else state = SEEKING_STRONG_FOR_PARA
        } else {
            putParaLevel(0, input.paraLevel)
            state = NOT_SEEKING_STRONG
        }
        var i = 0
        while (i < input.originalLength) {
            val i0 = i
            val c = UTF16.charAt(input.text, 0, input.originalLength, i)
            i += UTF16.getCharCount(c)
            var i1 = i - 1
            val property = input.getCustomizedClass(c).toByte()
            input.flags = input.flags or flag(property)
            input.dirProps[i1] = property
            if (i1 > i0) {
                input.flags = input.flags or flag(D.BOUNDARY_NEUTRAL.toByte())
                do { input.dirProps[--i1] = D.BOUNDARY_NEUTRAL.toByte() } while (i1 > i0)
            }
            if (removeControls && BidiReorderingCore.isBidiControlChar(c)) ++controlCount
            if (property.toInt() == D.LEFT_TO_RIGHT) {
                if (state == SEEKING_STRONG_FOR_PARA) {
                    putParaLevel(input.paraCount - 1, 0)
                    state = NOT_SEEKING_STRONG
                } else if (state == SEEKING_STRONG_FOR_FSI) {
                    if (stackLast <= 125) input.flags = input.flags or flag(D.LEFT_TO_RIGHT_ISOLATE)
                    state = LOOKING_FOR_PDI
                }
                lastStrong = D.LEFT_TO_RIGHT.toByte()
                continue
            }
            if (property.toInt() == D.RIGHT_TO_LEFT || property.toInt() == D.RIGHT_TO_LEFT_ARABIC) {
                if (state == SEEKING_STRONG_FOR_PARA) {
                    putParaLevel(input.paraCount - 1, 1)
                    state = NOT_SEEKING_STRONG
                } else if (state == SEEKING_STRONG_FOR_FSI) {
                    if (stackLast <= 125) {
                        input.dirProps[isolateStartStack[stackLast]] = D.RIGHT_TO_LEFT_ISOLATE
                        input.flags = input.flags or flag(D.RIGHT_TO_LEFT_ISOLATE)
                    }
                    state = LOOKING_FOR_PDI
                }
                lastStrong = D.RIGHT_TO_LEFT.toByte()
                if (property.toInt() == D.RIGHT_TO_LEFT_ARABIC) input.lastArabicPos = i - 1
                continue
            }
            if (property >= D.FIRST_STRONG_ISOLATE && property <= D.RIGHT_TO_LEFT_ISOLATE) {
                ++stackLast
                if (stackLast <= 125) {
                    isolateStartStack[stackLast] = i - 1
                    previousStateStack[stackLast] = state.toByte()
                }
                if (property == D.FIRST_STRONG_ISOLATE) {
                    input.dirProps[i - 1] = D.LEFT_TO_RIGHT_ISOLATE
                    state = SEEKING_STRONG_FOR_FSI
                } else state = LOOKING_FOR_PDI
                continue
            }
            if (property == D.POP_DIRECTIONAL_ISOLATE) {
                if (state == SEEKING_STRONG_FOR_FSI && stackLast <= 125) input.flags = input.flags or flag(D.LEFT_TO_RIGHT_ISOLATE)
                if (stackLast >= 0) {
                    if (stackLast <= 125) state = previousStateStack[stackLast].toInt()
                    --stackLast
                }
                continue
            }
            if (property.toInt() == D.BLOCK_SEPARATOR) {
                if (i < input.originalLength && c == '\r'.code && bidiCharAt(input.text, i) == '\n') continue
                input.paraLimits[input.paraCount - 1] = i
                if (isDefaultLevelInverse && lastStrong.toInt() == D.RIGHT_TO_LEFT) putParaLevel(input.paraCount - 1, 1)
                if ((input.reorderingOptions and 4) != 0) {
                    input.length = i
                    input.controlCount = controlCount
                }
                if (i < input.originalLength) {
                    ++input.paraCount
                    checkParaCount()
                    if (isDefaultLevel) {
                        putParaLevel(input.paraCount - 1, defaultParaLevel)
                        state = SEEKING_STRONG_FOR_PARA
                        lastStrong = defaultParaLevel
                    } else {
                        putParaLevel(input.paraCount - 1, input.paraLevel)
                        state = NOT_SEEKING_STRONG
                    }
                    stackLast = -1
                }
                continue
            }
        }
        if (stackLast > 125) {
            stackLast = 125
            state = SEEKING_STRONG_FOR_FSI
        }
        while (stackLast >= 0) {
            if (state == SEEKING_STRONG_FOR_FSI) {
                input.flags = input.flags or flag(D.LEFT_TO_RIGHT_ISOLATE)
                break
            }
            state = previousStateStack[stackLast].toInt()
            --stackLast
        }
        if ((input.reorderingOptions and 4) != 0) {
            if (input.length < input.originalLength) --input.paraCount
        } else {
            input.paraLimits[input.paraCount - 1] = input.originalLength
            input.controlCount = controlCount
        }
        if (isDefaultLevelInverse && lastStrong.toInt() == D.RIGHT_TO_LEFT) putParaLevel(input.paraCount - 1, 1)
        if (isDefaultLevel) input.paraLevel = bidiByteAt(input.paraLevels, 0)
        for (paragraph in 0 until input.paraCount) {
            input.flags = input.flags or flag((input.paraLevels[paragraph].toInt() and 1).toByte())
        }
        if (input.orderParagraphsLTR && (input.flags and flag(D.BLOCK_SEPARATOR.toByte())) != 0) input.flags = input.flags or flag(D.LEFT_TO_RIGHT.toByte())
    }
    companion object {
        fun customizedClass(c: Int, classifier: BidiClassifier?): Int {
            var direction = classifier?.classify(c) ?: D.CHAR_DIRECTION_COUNT
            if (direction == D.CHAR_DIRECTION_COUNT) direction = UBiDiProps.INSTANCE.getClass(c)
            return if (direction >= D.CHAR_DIRECTION_COUNT) D.OTHER_NEUTRAL else direction
        }
        fun paraLevelAt(input: BidiExplicitCore.Input, index: Int): Byte {
            if (input.defaultParaLevel.toInt() == 0 || index < input.paraLimits[0]) return input.paraLevel
            var paragraph = 1
            while (paragraph < input.paraCount) {
                if (index < input.paraLimits[paragraph]) break
                ++paragraph
            }
            if (paragraph >= input.paraCount) paragraph = input.paraCount - 1
            return bidiByteAt(input.paraLevels, paragraph)
        }
        private const val NOT_SEEKING_STRONG = 0
        private const val SEEKING_STRONG_FOR_PARA = 1
        private const val SEEKING_STRONG_FOR_FSI = 2
        private const val LOOKING_FOR_PDI = 3
        private fun flag(property: Byte) = 1 shl property.toInt()
        private fun codePointAtForward(text: String, index: Int): Int {
            val first = text[index]
            if (UTF16.isLeadSurrogate(first.code) && index + 1 < text.length) {
                val second = text[index + 1]
                if (UTF16.isTrailSurrogate(second.code)) return ((first.code - 0xd800) shl 10) + second.code - 0xdc00 + 0x10000
            }
            return first.code
        }
    }
}
