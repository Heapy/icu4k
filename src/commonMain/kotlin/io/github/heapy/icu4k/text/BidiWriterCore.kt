// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UCharacterDirection
import io.github.heapy.icu4k.text.BidiReorderingCore.DO_MIRRORING
import io.github.heapy.icu4k.text.BidiReorderingCore.INSERT_LRM_FOR_NUMERIC
import io.github.heapy.icu4k.text.BidiReorderingCore.KEEP_BASE_COMBINING
import io.github.heapy.icu4k.text.BidiReorderingCore.LRM_AFTER
import io.github.heapy.icu4k.text.BidiReorderingCore.LRM_BEFORE
import io.github.heapy.icu4k.text.BidiReorderingCore.OPTION_INSERT_MARKS
import io.github.heapy.icu4k.text.BidiReorderingCore.OPTION_REMOVE_CONTROLS
import io.github.heapy.icu4k.text.BidiReorderingCore.OUTPUT_REVERSE
import io.github.heapy.icu4k.text.BidiReorderingCore.REMOVE_BIDI_CONTROLS
import io.github.heapy.icu4k.text.BidiReorderingCore.RLM_AFTER
import io.github.heapy.icu4k.text.BidiReorderingCore.RLM_BEFORE

/** Resolved paragraph access. Callbacks preserve source run/classifier dispatch order. */
internal interface BidiWriterInput {
    val text: CharArray
    val length: Int
    val reorderingOptions: Int
    val reorderingMode: Int
    val dirProps: ByteArray?
    fun countRuns(): Int
    fun getVisualRun(index: Int): BidiRun
    fun getInsertRemove(index: Int): Int
    fun isInverse(): Boolean
    fun testDirPropFlagAt(flag: Int, index: Int): Boolean
}

/** ICU's full visual writer; paragraph resolution is supplied by BidiWriterInput. */
internal object BidiWriterCore {
    private const val LRM_CHAR = '\u200E'
    private const val RLM_CHAR = '\u200F'
    private const val L = UCharacterDirection.LEFT_TO_RIGHT
    private val MASK_R_AL = (1 shl UCharacterDirection.RIGHT_TO_LEFT) or (1 shl UCharacterDirection.RIGHT_TO_LEFT_ARABIC)
    private fun isCombining(type: Int): Boolean = ((1 shl type) and
        ((1 shl UCharacter.NON_SPACING_MARK.toInt()) or (1 shl UCharacter.COMBINING_SPACING_MARK.toInt()) or (1 shl UCharacter.ENCLOSING_MARK.toInt()))) != 0
    private fun doWriteForward(source: String, options: Int): String {
        if (source.isEmpty() && (options and (REMOVE_BIDI_CONTROLS or DO_MIRRORING)) != 0) bidiStringCharAt(source, 0)
        when (options and (REMOVE_BIDI_CONTROLS or DO_MIRRORING)) {
            0 -> return source
            DO_MIRRORING -> {
                val dest = StringBuilder(source.length)
                var i = 0
                do {
                    val c = UTF16.charAt(source, i)
                    i += UTF16.getCharCount(c)
                    UTF16.append(dest, UCharacter.getMirror(c))
                } while (i < source.length)
                return dest.toString()
            }
            REMOVE_BIDI_CONTROLS -> {
                val dest = StringBuilder(source.length)
                var i = 0
                do { val c = source[i++]; if (!BidiReorderingCore.isBidiControlChar(c.code)) dest.append(c) } while (i < source.length)
                return dest.toString()
            }
            else -> {
                val dest = StringBuilder(source.length)
                var i = 0
                do {
                    val c = UTF16.charAt(source, i)
                    i += UTF16.getCharCount(c)
                    if (!BidiReorderingCore.isBidiControlChar(c)) UTF16.append(dest, UCharacter.getMirror(c))
                } while (i < source.length)
                return dest.toString()
            }
        }
    }
    private fun doWriteForward(text: CharArray, start: Int, limit: Int, options: Int): String =
        doWriteForward(text.concatToString(start, limit), options)
    fun reverse(source: String?, options: Int): String {
        if (source == null) throw IllegalArgumentException()
        return if (source.isEmpty()) "" else writeReverse(source, options)
    }
    private fun writeReverse(source: String, options: Int): String {
        if (source.isEmpty()) bidiStringCharAt(source, -1)
        val dest = StringBuilder(source.length)
        var length = source.length
        when (options and (REMOVE_BIDI_CONTROLS or DO_MIRRORING or KEEP_BASE_COMBINING)) {
            0 -> do {
                val end = length
                length -= UTF16.getCharCount(UTF16.charAt(source, length - 1))
                dest.append(source.substring(length, end))
            } while (length > 0)
            KEEP_BASE_COMBINING -> do {
                val end = length
                var c: Int
                do {
                    c = UTF16.charAt(source, length - 1)
                    length -= UTF16.getCharCount(c)
                } while (length > 0 && isCombining(UCharacter.getType(c)))
                dest.append(source.substring(length, end))
            } while (length > 0)
            else -> do {
                val end = length
                var c = UTF16.charAt(source, length - 1)
                length -= UTF16.getCharCount(c)
                if ((options and KEEP_BASE_COMBINING) != 0) {
                    while (length > 0 && isCombining(UCharacter.getType(c))) {
                        c = UTF16.charAt(source, length - 1)
                        length -= UTF16.getCharCount(c)
                    }
                }
                if ((options and REMOVE_BIDI_CONTROLS) != 0 && BidiReorderingCore.isBidiControlChar(c)) continue
                var start = length
                if ((options and DO_MIRRORING) != 0) {
                    c = UCharacter.getMirror(c)
                    UTF16.append(dest, c)
                    start += UTF16.getCharCount(c)
                }
                dest.append(source.substring(start, end))
            } while (length > 0)
        }
        return dest.toString()
    }
    private fun doWriteReverse(text: CharArray, start: Int, limit: Int, options: Int): String =
        writeReverse(text.concatToString(start, limit), options)
    private fun appendMark(dest: StringBuilder, flags: Int, lrm: Int, rlm: Int) {
        if ((flags and lrm) != 0) dest.append(LRM_CHAR) else if ((flags and rlm) != 0) dest.append(RLM_CHAR)
    }
    fun writeReordered(bidi: BidiWriterInput, originalOptions: Int): String {
        if (bidi.length == 0) return ""
        val text = bidi.text
        val runCount = bidi.countRuns()
        var options = originalOptions
        if ((bidi.reorderingOptions and OPTION_INSERT_MARKS) != 0) {
            options = (options or INSERT_LRM_FOR_NUMERIC) and REMOVE_BIDI_CONTROLS.inv()
        }
        if ((bidi.reorderingOptions and OPTION_REMOVE_CONTROLS) != 0) {
            options = (options or REMOVE_BIDI_CONTROLS) and INSERT_LRM_FOR_NUMERIC.inv()
        }
        if (bidi.reorderingMode != BidiReorderingCore.REORDER_INVERSE_NUMBERS_AS_L &&
            bidi.reorderingMode != BidiReorderingCore.REORDER_INVERSE_LIKE_DIRECT &&
            bidi.reorderingMode != BidiReorderingCore.REORDER_INVERSE_FOR_NUMBERS_SPECIAL &&
            bidi.reorderingMode != BidiReorderingCore.REORDER_RUNS_ONLY) options = options and INSERT_LRM_FOR_NUMERIC.inv()
        val dest = StringBuilder(if ((options and INSERT_LRM_FOR_NUMERIC) != 0) bidi.length * 2 else bidi.length)
        if ((options and OUTPUT_REVERSE) == 0) {
            if ((options and INSERT_LRM_FOR_NUMERIC) == 0) {
                for (index in 0 until runCount) {
                    val run = bidi.getVisualRun(index)
                    if (run.isEvenRun()) dest.append(doWriteForward(text, run.start, run.limit, options and DO_MIRRORING.inv()))
                    else dest.append(doWriteReverse(text, run.start, run.limit, options))
                }
            } else {
                val props = bidi.dirProps
                for (index in 0 until runCount) {
                    val run = bidi.getVisualRun(index)
                    var mark = bidi.getInsertRemove(index)
                    if (mark < 0) mark = 0
                    if (run.isEvenRun()) {
                        if (bidi.isInverse() && bidiByteAt(props, run.start).toInt() != L) mark = mark or LRM_BEFORE
                        appendMark(dest, mark, LRM_BEFORE, RLM_BEFORE)
                        dest.append(doWriteForward(text, run.start, run.limit, options and DO_MIRRORING.inv()))
                        if (bidi.isInverse() && bidiByteAt(props, run.limit - 1).toInt() != L) mark = mark or LRM_AFTER
                        appendMark(dest, mark, LRM_AFTER, RLM_AFTER)
                    } else {
                        if (bidi.isInverse() && !bidi.testDirPropFlagAt(MASK_R_AL, run.limit - 1)) mark = mark or RLM_BEFORE
                        appendMark(dest, mark, LRM_BEFORE, RLM_BEFORE)
                        dest.append(doWriteReverse(text, run.start, run.limit, options))
                        if (bidi.isInverse() && (MASK_R_AL and (1 shl bidiByteAt(props, run.start).toInt())) == 0) mark = mark or RLM_AFTER
                        appendMark(dest, mark, LRM_AFTER, RLM_AFTER)
                    }
                }
            }
        } else {
            if ((options and INSERT_LRM_FOR_NUMERIC) == 0) {
                for (index in runCount - 1 downTo 0) {
                    val run = bidi.getVisualRun(index)
                    if (run.isEvenRun()) dest.append(doWriteReverse(text, run.start, run.limit, options and DO_MIRRORING.inv()))
                    else dest.append(doWriteForward(text, run.start, run.limit, options))
                }
            } else {
                val props = bidi.dirProps
                for (index in runCount - 1 downTo 0) {
                    val run = bidi.getVisualRun(index)
                    if (run.isEvenRun()) {
                        if (bidiByteAt(props, run.limit - 1).toInt() != L) dest.append(LRM_CHAR)
                        dest.append(doWriteReverse(text, run.start, run.limit, options and DO_MIRRORING.inv()))
                        if (bidiByteAt(props, run.start).toInt() != L) dest.append(LRM_CHAR)
                    } else {
                        if ((MASK_R_AL and (1 shl bidiByteAt(props, run.start).toInt())) == 0) dest.append(RLM_CHAR)
                        dest.append(doWriteForward(text, run.start, run.limit, options))
                        if ((MASK_R_AL and (1 shl bidiByteAt(props, run.limit - 1).toInt())) == 0) dest.append(RLM_CHAR)
                    }
                }
            }
        }
        return dest.toString()
    }
}

internal expect fun bidiByteAt(data: ByteArray?, index: Int): Byte

internal expect fun bidiStringCharAt(text: String, index: Int): Char
