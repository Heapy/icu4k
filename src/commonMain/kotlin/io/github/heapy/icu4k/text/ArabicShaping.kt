// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2012, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.UBiDiProps
import io.github.heapy.icu4k.lang.UCharacterDirection

/** Character-based Arabic shaping for legacy text formats and displays. */
class ArabicShaping(private val options: Int) {
    private val isLogical: Boolean
    private val spacesRelativeToTextBeginEnd: Boolean
    private val tailChar: Char

    init {
        if ((options and DIGITS_MASK) > 0x80) throw IllegalArgumentException("bad DIGITS options")
        isLogical = (options and TEXT_DIRECTION_MASK) == TEXT_DIRECTION_LOGICAL
        spacesRelativeToTextBeginEnd = (options and SPACES_RELATIVE_TO_TEXT_MASK) == SPACES_RELATIVE_TO_TEXT_BEGIN_END
        tailChar = if ((options and SHAPE_TAIL_TYPE_MASK) == SHAPE_TAIL_NEW_UNICODE) NEW_TAIL_CHAR else OLD_TAIL_CHAR
    }

    @Throws(ArabicShapingException::class)
    fun shape(source: CharArray?, sourceStart: Int, sourceLength: Int, dest: CharArray?, destStart: Int, destSize: Int): Int {
        if (source == null) throw IllegalArgumentException("source can not be null")
        if (sourceStart < 0 || sourceLength < 0 || sourceStart + sourceLength > source.size)
            throw IllegalArgumentException("bad source start ($sourceStart) or length ($sourceLength) for buffer of length ${source.size}")
        if (dest == null && destSize != 0) throw IllegalArgumentException("null dest requires destSize == 0")
        if (destSize != 0 && (destStart < 0 || destSize < 0 || destStart + destSize > dest!!.size))
            throw IllegalArgumentException("bad dest start ($destStart) or size ($destSize) for buffer of length ${dest!!.size}")
        val tashkeel = options and TASHKEEL_MASK
        if (tashkeel != 0 && tashkeel != TASHKEEL_BEGIN && tashkeel != TASHKEEL_END &&
            tashkeel != TASHKEEL_RESIZE && tashkeel != TASHKEEL_REPLACE_BY_TATWEEL)
            throw IllegalArgumentException("Wrong Tashkeel argument")
        val lamalef = options and LAMALEF_MASK
        if (lamalef != 0 && lamalef != LAMALEF_BEGIN && lamalef != LAMALEF_END &&
            lamalef != LAMALEF_RESIZE && lamalef != LAMALEF_AUTO && lamalef != LAMALEF_NEAR)
            throw IllegalArgumentException("Wrong Lam Alef argument")
        if (tashkeel != 0 && (options and LETTERS_MASK) == LETTERS_UNSHAPE)
            throw IllegalArgumentException("Tashkeel replacement should not be enabled in deshaping mode ")
        return internalShape(source, sourceStart, sourceLength, dest, destStart, destSize)
    }

    @Throws(ArabicShapingException::class)
    fun shape(source: CharArray?, start: Int, length: Int) {
        if ((options and LAMALEF_MASK) == LAMALEF_RESIZE)
            throw ArabicShapingException("Cannot shape in place with length option resize.")
        shape(source, start, length, source, start, length)
    }

    @Throws(ArabicShapingException::class)
    fun shape(text: String?): String {
        val source = text!!.toCharArray()
        val dest = if ((options and LAMALEF_MASK) == LAMALEF_RESIZE && (options and LETTERS_MASK) == LETTERS_UNSHAPE)
            CharArray(source.size * 2) else source
        return dest.concatToString(0, shape(source, 0, source.size, dest, 0, dest.size))
    }

    override fun equals(other: Any?): Boolean = other is ArabicShaping && options == other.options
    override fun hashCode(): Int = options
    override fun toString(): String = buildString {
        append(super.toString()).append('[')
        append(when (options and LAMALEF_MASK) {
            LAMALEF_RESIZE -> "LamAlef resize"
            LAMALEF_NEAR -> "LamAlef spaces at near"
            LAMALEF_BEGIN -> "LamAlef spaces at begin"
            LAMALEF_END -> "LamAlef spaces at end"
            LAMALEF_AUTO -> "lamAlef auto"
            else -> ""
        })
        append(if ((options and TEXT_DIRECTION_MASK) == TEXT_DIRECTION_LOGICAL) ", logical" else ", visual")
        append(when (options and LETTERS_MASK) {
            LETTERS_NOOP -> ", no letter shaping"
            LETTERS_SHAPE -> ", shape letters"
            LETTERS_SHAPE_TASHKEEL_ISOLATED -> ", shape letters tashkeel isolated"
            else -> ", unshape letters"
        })
        if ((options and SEEN_MASK) == SEEN_TWOCELL_NEAR) append(", Seen at near")
        if ((options and YEHHAMZA_MASK) == YEHHAMZA_TWOCELL_NEAR) append(", Yeh Hamza at near")
        append(when (options and TASHKEEL_MASK) {
            TASHKEEL_BEGIN -> ", Tashkeel at begin"
            TASHKEEL_END -> ", Tashkeel at end"
            TASHKEEL_REPLACE_BY_TATWEEL -> ", Tashkeel replace with tatweel"
            TASHKEEL_RESIZE -> ", Tashkeel resize"
            else -> ""
        })
        append(when (options and DIGITS_MASK) {
            DIGITS_NOOP -> ", no digit shaping"
            DIGITS_EN2AN -> ", shape digits to AN"
            DIGITS_AN2EN -> ", shape digits to EN"
            DIGITS_EN2AN_INIT_LR -> ", shape digits to AN contextually: default EN"
            else -> ", shape digits to AN contextually: default AL"
        })
        append(if ((options and DIGIT_TYPE_MASK) == DIGIT_TYPE_AN) ", standard Arabic-Indic digits" else ", extended Arabic-Indic digits")
        append(']')
    }

    private fun shapeToArabicDigitsWithContext(dest: CharArray, start: Int, length: Int, digitBase: Char, initialAL: Boolean) {
        var lastStrongWasAL = initialAL
        val delta = digitBase - '0'
        var i = start + length
        while (--i >= start) {
            val ch = dest[i]
            when (UBiDiProps.INSTANCE.getClass(ch.code)) {
                UCharacterDirection.LEFT_TO_RIGHT, UCharacterDirection.RIGHT_TO_LEFT -> lastStrongWasAL = false
                UCharacterDirection.RIGHT_TO_LEFT_ARABIC -> lastStrongWasAL = true
                UCharacterDirection.EUROPEAN_NUMBER -> if (lastStrongWasAL && ch <= '9') dest[i] = ch + delta
            }
        }
    }

    private fun invertBuffer(buffer: CharArray, start: Int, length: Int) {
        var i = start
        var j = start + length - 1
        while (i < j) { val temp = buffer[i]; buffer[i++] = buffer[j]; buffer[j--] = temp }
    }
    private fun changeLamAlef(ch: Char): Char = when (ch) {
        '\u0622' -> '\u065C'
        '\u0623' -> '\u065D'
        '\u0625' -> '\u065E'
        '\u0627' -> '\u065F'
        else -> '\u0000'
    }
    private fun specialChar(ch: Char): Int = when {
        (ch > '\u0621' && ch < '\u0626') || ch == '\u0627' ||
            (ch > '\u062E' && ch < '\u0633') || (ch > '\u0647' && ch < '\u064A') || ch == '\u0629' -> 1
        ch in '\u064B'..'\u0652' -> 2
        ch in '\u0653'..'\u0655' || ch == '\u0670' || ch in '\uFE70'..'\uFE7F' -> 3
        else -> 0
    }
    private fun getLink(ch: Char): Int = when (ch) {
        in '\u0622'..'\u06D3' -> araLink[ch - '\u0622']
        '\u200D' -> 3
        in '\u206D'..'\u206F' -> 4
        in '\uFE70'..'\uFEFC' -> presLink[ch - '\uFE70']
        else -> 0
    }
    private fun countSpacesLeft(dest: CharArray, start: Int, count: Int): Int {
        for (i in start until start + count) if (dest[i] != SPACE_CHAR) return i - start
        return count
    }
    private fun countSpacesRight(dest: CharArray, start: Int, count: Int): Int {
        var i = start + count
        while (--i >= start) if (dest[i] != SPACE_CHAR) return start + count - 1 - i
        return count
    }
    private fun isTashkeelChar(ch: Char) = ch in '\u064B'..'\u0652'
    private fun isSeenTailFamilyChar(ch: Char) = if (ch in '\uFEB1'..'\uFEBE') tailFamilyIsolatedFinal[ch - '\uFEB1'] else 0
    private fun isSeenFamilyChar(ch: Char) = if (ch in '\u0633'..'\u0636') 1 else 0
    private fun isTailChar(ch: Char) = ch == OLD_TAIL_CHAR || ch == NEW_TAIL_CHAR
    private fun isAlefMaksouraChar(ch: Char) = ch == '\uFEEF' || ch == '\uFEF0' || ch == '\u0649'
    private fun isYehHamzaChar(ch: Char) = ch == '\uFE89' || ch == '\uFE8A'
    private fun isTashkeelCharFE(ch: Char) = ch != '\uFE75' && ch in '\uFE70'..'\uFE7F'
    private fun isTashkeelOnTatweelChar(ch: Char): Int = when {
        ch in '\uFE70'..'\uFE7F' && ch != NEW_TAIL_CHAR && ch != '\uFE75' && ch != SHADDA_TATWEEL_CHAR -> tashkeelMedial[ch - '\uFE70']
        ch in '\uFCF2'..'\uFCF4' || ch == SHADDA_TATWEEL_CHAR -> 2
        else -> 0
    }
    private fun isIsolatedTashkeelChar(ch: Char): Int = when {
        ch in '\uFE70'..'\uFE7F' && ch != NEW_TAIL_CHAR && ch != '\uFE75' -> 1 - tashkeelMedial[ch - '\uFE70']
        ch in '\uFC5E'..'\uFC63' -> 1
        else -> 0
    }
    private fun isAlefChar(ch: Char) = ch == '\u0622' || ch == '\u0623' || ch == '\u0625' || ch == '\u0627'
    private fun isLamAlefChar(ch: Char) = ch in '\uFEF5'..'\uFEFC'
    private fun isNormalizedLamAlefChar(ch: Char) = ch in '\u065C'..'\u065F'

    private fun calculateSize(source: CharArray, sourceStart: Int, sourceLength: Int): Int {
        var size = sourceLength
        when (options and LETTERS_MASK) {
            LETTERS_SHAPE, LETTERS_SHAPE_TASHKEEL_ISOLATED -> if (isLogical) {
                for (i in sourceStart until sourceStart + sourceLength - 1)
                    if ((source[i] == LAM_CHAR && isAlefChar(source[i + 1])) || isTashkeelCharFE(source[i])) --size
            } else {
                for (i in sourceStart + 1 until sourceStart + sourceLength)
                    if ((source[i] == LAM_CHAR && isAlefChar(source[i - 1])) || isTashkeelCharFE(source[i])) --size
            }
            LETTERS_UNSHAPE -> for (i in sourceStart until sourceStart + sourceLength) if (isLamAlefChar(source[i])) ++size
        }
        return size
    }
    private fun countSpaceSub(dest: CharArray, length: Int, subChar: Char): Int {
        var count = 0
        for (i in 0 until length) if (dest[i] == subChar) ++count
        return count
    }
    private fun shiftArray(dest: CharArray, start: Int, end: Int, subChar: Char) {
        var w = end
        var r = end
        while (--r >= start) {
            val ch = dest[r]
            if (ch != subChar) { --w; if (w != r) dest[w] = ch }
        }
    }
    private fun flipArray(dest: CharArray, start: Int, end: Int, writeStart: Int): Int {
        var w = writeStart
        if (w > start) {
            var r = w
            w = start
            while (r < end) dest[w++] = dest[r++]
        } else w = end
        return w
    }
    private fun handleTashkeelWithTatweel(dest: CharArray, sourceLength: Int): Int {
        for (i in 0 until sourceLength) {
            if (isTashkeelOnTatweelChar(dest[i]) == 1) dest[i] = TATWEEL_CHAR
            else if (isTashkeelOnTatweelChar(dest[i]) == 2) dest[i] = SHADDA_TATWEEL_CHAR
            else if (isIsolatedTashkeelChar(dest[i]) == 1 && dest[i] != SHADDA_CHAR) dest[i] = SPACE_CHAR
        }
        return sourceLength
    }

    private fun handleGeneratedSpaces(dest: CharArray, start: Int, initialLength: Int): Int {
        var length = initialLength
        var lamalef = options and LAMALEF_MASK
        var tashkeel = options and TASHKEEL_MASK
        if (!isLogical && !spacesRelativeToTextBeginEnd) {
            lamalef = when (lamalef) { LAMALEF_BEGIN -> LAMALEF_END; LAMALEF_END -> LAMALEF_BEGIN; else -> lamalef }
            tashkeel = when (tashkeel) { TASHKEEL_BEGIN -> TASHKEEL_END; TASHKEEL_END -> TASHKEEL_BEGIN; else -> tashkeel }
        }
        if (lamalef == LAMALEF_NEAR) {
            for (i in start until start + length) if (dest[i] == LAMALEF_SPACE_SUB) dest[i] = SPACE_CHAR
        } else {
            val end = start + length
            var wL = countSpaceSub(dest, length, LAMALEF_SPACE_SUB)
            var wT = countSpaceSub(dest, length, TASHKEEL_SPACE_SUB)
            if (lamalef == LAMALEF_END) {
                shiftArray(dest, start, end, LAMALEF_SPACE_SUB)
                while (wL > start) dest[--wL] = SPACE_CHAR
            }
            if (tashkeel == TASHKEEL_END) {
                shiftArray(dest, start, end, TASHKEEL_SPACE_SUB)
                while (wT > start) dest[--wT] = SPACE_CHAR
            }
            if (lamalef == LAMALEF_RESIZE) {
                shiftArray(dest, start, end, LAMALEF_SPACE_SUB)
                wL = flipArray(dest, start, end, wL)
                length = wL - start
            }
            if (tashkeel == TASHKEEL_RESIZE) {
                shiftArray(dest, start, end, TASHKEEL_SPACE_SUB)
                wT = flipArray(dest, start, end, wT)
                length = wT - start
            }
            if (lamalef == LAMALEF_BEGIN || lamalef == LAMALEF_AUTO) {
                shiftArray(dest, start, end, LAMALEF_SPACE_SUB)
                wL = flipArray(dest, start, end, wL)
                while (wL < end) dest[wL++] = SPACE_CHAR
            }
            if (tashkeel == TASHKEEL_BEGIN) {
                shiftArray(dest, start, end, TASHKEEL_SPACE_SUB)
                wT = flipArray(dest, start, end, wT)
                while (wT < end) dest[wT++] = SPACE_CHAR
            }
        }
        return length
    }

    private fun expandCompositCharAtBegin(dest: CharArray, start: Int, length: Int, lacount: Int): Boolean {
        if (lacount > countSpacesRight(dest, start, length)) return true
        var r = start + length - lacount
        var w = start + length
        while (--r >= start) {
            val ch = dest[r]
            if (isNormalizedLamAlefChar(ch)) {
                dest[--w] = LAM_CHAR
                dest[--w] = convertNormalizedLamAlef[ch - '\u065C']
            } else dest[--w] = ch
        }
        return false
    }
    private fun expandCompositCharAtEnd(dest: CharArray, start: Int, length: Int, lacount: Int): Boolean {
        if (lacount > countSpacesLeft(dest, start, length)) return true
        var r = start + lacount
        var w = start
        val end = start + length
        while (r < end) {
            val ch = dest[r++]
            if (isNormalizedLamAlefChar(ch)) {
                dest[w++] = convertNormalizedLamAlef[ch - '\u065C']
                dest[w++] = LAM_CHAR
            } else dest[w++] = ch
        }
        return false
    }
    private fun expandCompositCharAtNear(dest: CharArray, start: Int, length: Int, yehHamza: Int, seenTail: Int, lamalef: Int): Boolean {
        if (isNormalizedLamAlefChar(dest[start])) return true
        var i = start + length
        while (--i >= start) {
            val ch = dest[i]
            if (lamalef == 1 && isNormalizedLamAlefChar(ch)) {
                if (i > start && dest[i - 1] == SPACE_CHAR) {
                    dest[i] = LAM_CHAR
                    dest[--i] = convertNormalizedLamAlef[ch - '\u065C']
                } else return true
            } else if (seenTail == 1 && isSeenTailFamilyChar(ch) == 1) {
                if (i > start && dest[i - 1] == SPACE_CHAR) dest[i - 1] = tailChar else return true
            } else if (yehHamza == 1 && isYehHamzaChar(ch)) {
                if (i > start && dest[i - 1] == SPACE_CHAR) {
                    dest[i] = yehHamzaToYeh[ch - YEH_HAMZAFE_CHAR]
                    dest[i - 1] = HAMZAFE_CHAR
                } else return true
            }
        }
        return false
    }
    private fun expandCompositChar(dest: CharArray, start: Int, initialLength: Int, lacount: Int, shapingMode: Int): Int {
        var length = initialLength
        var lamalef = options and LAMALEF_MASK
        if (!isLogical && !spacesRelativeToTextBeginEnd)
            lamalef = when (lamalef) { LAMALEF_BEGIN -> LAMALEF_END; LAMALEF_END -> LAMALEF_BEGIN; else -> lamalef }
        if (shapingMode == DESHAPE_MODE) {
            when (lamalef) {
                LAMALEF_AUTO -> {
                    var failed = if (isLogical) expandCompositCharAtEnd(dest, start, length, lacount)
                        else expandCompositCharAtBegin(dest, start, length, lacount)
                    if (failed) failed = if (isLogical) expandCompositCharAtBegin(dest, start, length, lacount)
                        else expandCompositCharAtEnd(dest, start, length, lacount)
                    if (failed) failed = expandCompositCharAtNear(dest, start, length, 0, 0, 1)
                    if (failed) throw ArabicShapingException("No spacefor lamalef")
                }
                LAMALEF_END -> if (expandCompositCharAtEnd(dest, start, length, lacount)) throw ArabicShapingException("No spacefor lamalef")
                LAMALEF_BEGIN -> if (expandCompositCharAtBegin(dest, start, length, lacount)) throw ArabicShapingException("No spacefor lamalef")
                LAMALEF_NEAR -> if (expandCompositCharAtNear(dest, start, length, 0, 0, 1)) throw ArabicShapingException("No spacefor lamalef")
                LAMALEF_RESIZE -> {
                    var r = start + length
                    var w = r + lacount
                    while (--r >= start) {
                        val ch = dest[r]
                        if (isNormalizedLamAlefChar(ch)) {
                            dest[--w] = LAM_CHAR
                            dest[--w] = convertNormalizedLamAlef[ch - '\u065C']
                        } else dest[--w] = ch
                    }
                    length += lacount
                }
            }
        } else {
            if ((options and SEEN_MASK) == SEEN_TWOCELL_NEAR && expandCompositCharAtNear(dest, start, length, 0, 1, 0))
                throw ArabicShapingException("No space for Seen tail expansion")
            if ((options and YEHHAMZA_MASK) == YEHHAMZA_TWOCELL_NEAR && expandCompositCharAtNear(dest, start, length, 1, 0, 0))
                throw ArabicShapingException("No space for YehHamza expansion")
        }
        return length
    }

    private fun normalize(dest: CharArray, start: Int, length: Int): Int {
        var lacount = 0
        for (i in start until start + length) {
            val ch = dest[i]
            if (ch in '\uFE70'..'\uFEFC') {
                if (isLamAlefChar(ch)) ++lacount
                dest[i] = convertFEto06[ch - '\uFE70'].toChar()
            }
        }
        return lacount
    }
    private fun deshapeNormalize(dest: CharArray, start: Int, length: Int): Int {
        var lacount = 0
        val yehHamza = (options and YEHHAMZA_MASK) == YEHHAMZA_TWOCELL_NEAR
        val seen = (options and SEEN_MASK) == SEEN_TWOCELL_NEAR
        for (i in start until start + length) {
            val ch = dest[i]
            if (yehHamza && (ch == HAMZA06_CHAR || ch == HAMZAFE_CHAR) && i < length - 1 && isAlefMaksouraChar(dest[i + 1])) {
                dest[i] = SPACE_CHAR
                dest[i + 1] = YEH_HAMZA_CHAR
            } else if (seen && isTailChar(ch) && i < length - 1 && isSeenTailFamilyChar(dest[i + 1]) == 1) {
                dest[i] = SPACE_CHAR
            } else if (ch in '\uFE70'..'\uFEFC') {
                if (isLamAlefChar(ch)) ++lacount
                dest[i] = convertFEto06[ch - '\uFE70'].toChar()
            }
        }
        return lacount
    }

    private fun shapeUnicode(dest: CharArray, start: Int, length: Int, tashkeelFlag: Int): Int {
        val lamalefCount = normalize(dest, start, length)
        var lamalefFound = false
        var seenFound = false
        var yehhamzaFound = false
        var tashkeelFound = false
        var i = start + length - 1
        var currLink = getLink(dest[i])
        var nextLink = 0
        var prevLink = 0
        var lastLink = 0
        var lastPos = i
        var nx = -2
        while (i >= 0) {
            if ((currLink and 0xFF00) != 0 || isTashkeelChar(dest[i])) {
                var nw = i - 1
                nx = -2
                while (nx < 0) {
                    if (nw == -1) {
                        nextLink = 0
                        nx = Int.MAX_VALUE
                    } else {
                        nextLink = getLink(dest[nw])
                        if ((nextLink and IRRELEVANT) == 0) nx = nw else --nw
                    }
                }
                if ((currLink and ALEFTYPE) > 0 && (lastLink and LAMTYPE) > 0) {
                    lamalefFound = true
                    val wLamalef = changeLamAlef(dest[i])
                    if (wLamalef != '\u0000') {
                        dest[i] = '\uFFFF'
                        dest[lastPos] = wLamalef
                        i = lastPos
                    }
                    lastLink = prevLink
                    currLink = getLink(wLamalef)
                }
                if ((i > 0 && dest[i - 1] == SPACE_CHAR) || i == 0) {
                    if (isSeenFamilyChar(dest[i]) == 1) seenFound = true
                    else if (dest[i] == YEH_HAMZA_CHAR) yehhamzaFound = true
                }
                val flag = specialChar(dest[i])
                var shape = shapeTable[nextLink and LINK_MASK][lastLink and LINK_MASK][currLink and LINK_MASK]
                if (flag == 1) shape = shape and 1
                else if (flag == 2) {
                    shape = if (tashkeelFlag == 0 && (lastLink and LINKL) != 0 && (nextLink and LINKR) != 0 &&
                        dest[i] != '\u064C' && dest[i] != '\u064D' &&
                        !((nextLink and ALEFTYPE) == ALEFTYPE && (lastLink and LAMTYPE) == LAMTYPE)) 1
                    else if (tashkeelFlag == 2 && dest[i] == SHADDA06_CHAR) 1 else 0
                }
                if (flag == 2) {
                    if (tashkeelFlag == 2 && dest[i] != SHADDA06_CHAR) {
                        dest[i] = TASHKEEL_SPACE_SUB
                        tashkeelFound = true
                    } else dest[i] = ('\uFE70'.code + irrelevantPos[dest[i] - '\u064B'] + shape).toChar()
                } else dest[i] = ('\uFE70'.code + (currLink shr 8) + shape).toChar()
            }
            if ((currLink and IRRELEVANT) == 0) {
                prevLink = lastLink
                lastLink = currLink
                lastPos = i
            }
            --i
            if (i == nx) { currLink = nextLink; nx = -2 }
            else if (i != -1) currLink = getLink(dest[i])
        }
        var size = length
        if (lamalefFound || tashkeelFound) size = handleGeneratedSpaces(dest, start, length)
        if (seenFound || yehhamzaFound) size = expandCompositChar(dest, start, size, lamalefCount, SHAPE_MODE)
        return size
    }
    private fun deShapeUnicode(dest: CharArray, start: Int, length: Int): Int {
        val count = deshapeNormalize(dest, start, length)
        return if (count != 0) expandCompositChar(dest, start, length, count, DESHAPE_MODE) else length
    }
    private fun internalShape(source: CharArray, sourceStart: Int, sourceLength: Int, dest: CharArray?, destStart: Int, destSize: Int): Int {
        if (sourceLength == 0) return 0
        if (destSize == 0) return if ((options and LETTERS_MASK) != LETTERS_NOOP && (options and LAMALEF_MASK) == LAMALEF_RESIZE)
            calculateSize(source, sourceStart, sourceLength) else sourceLength
        val temp = CharArray(sourceLength * 2)
        source.copyInto(temp, 0, sourceStart, sourceStart + sourceLength)
        if (isLogical) invertBuffer(temp, 0, sourceLength)
        var outputSize = sourceLength
        when (options and LETTERS_MASK) {
            LETTERS_SHAPE_TASHKEEL_ISOLATED -> outputSize = shapeUnicode(temp, 0, sourceLength, 1)
            LETTERS_SHAPE -> {
                outputSize = if ((options and TASHKEEL_MASK) != 0 && (options and TASHKEEL_MASK) != TASHKEEL_REPLACE_BY_TATWEEL)
                    shapeUnicode(temp, 0, sourceLength, 2) else {
                    val size = shapeUnicode(temp, 0, sourceLength, 0)
                    if ((options and TASHKEEL_MASK) == TASHKEEL_REPLACE_BY_TATWEEL) handleTashkeelWithTatweel(temp, sourceLength) else size
                }
            }
            LETTERS_UNSHAPE -> outputSize = deShapeUnicode(temp, 0, sourceLength)
        }
        if (outputSize > destSize) throw ArabicShapingException("not enough room for result data")
        if ((options and DIGITS_MASK) != DIGITS_NOOP) {
            val digitBase = if ((options and DIGIT_TYPE_MASK) == DIGIT_TYPE_AN) '\u0660' else '\u06F0'
            when (options and DIGITS_MASK) {
                DIGITS_EN2AN -> {
                    val delta = digitBase - '0'
                    for (i in 0 until outputSize) if (temp[i] in '0'..'9') temp[i] = temp[i] + delta
                }
                DIGITS_AN2EN -> {
                    val top = digitBase + 9
                    val delta = '0' - digitBase
                    for (i in 0 until outputSize) if (temp[i] in digitBase..top) temp[i] = temp[i] + delta
                }
                DIGITS_EN2AN_INIT_LR -> shapeToArabicDigitsWithContext(temp, 0, outputSize, digitBase, false)
                DIGITS_EN2AN_INIT_AL -> shapeToArabicDigitsWithContext(temp, 0, outputSize, digitBase, true)
            }
        }
        if (isLogical) invertBuffer(temp, 0, outputSize)
        temp.copyInto(dest!!, destStart, 0, outputSize)
        return outputSize
    }
    companion object {
        const val SEEN_TWOCELL_NEAR = 0x200000
        const val SEEN_MASK = 0x700000
        const val YEHHAMZA_TWOCELL_NEAR = 0x1000000
        const val YEHHAMZA_MASK = 0x3800000
        const val TASHKEEL_BEGIN = 0x40000
        const val TASHKEEL_END = 0x60000
        const val TASHKEEL_RESIZE = 0x80000
        const val TASHKEEL_REPLACE_BY_TATWEEL = 0xC0000
        const val TASHKEEL_MASK = 0xE0000
        const val SPACES_RELATIVE_TO_TEXT_BEGIN_END = 0x4000000
        const val SPACES_RELATIVE_TO_TEXT_MASK = 0x4000000
        const val SHAPE_TAIL_NEW_UNICODE = 0x8000000
        const val SHAPE_TAIL_TYPE_MASK = 0x8000000
        const val LENGTH_GROW_SHRINK = 0
        const val LAMALEF_RESIZE = 0
        const val LENGTH_FIXED_SPACES_NEAR = 1
        const val LAMALEF_NEAR = 1
        const val LENGTH_FIXED_SPACES_AT_END = 2
        const val LAMALEF_END = 2
        const val LENGTH_FIXED_SPACES_AT_BEGINNING = 3
        const val LAMALEF_BEGIN = 3
        const val LAMALEF_AUTO = 0x10000
        const val LENGTH_MASK = 0x10003
        const val LAMALEF_MASK = 0x10003
        const val TEXT_DIRECTION_LOGICAL = 0
        const val TEXT_DIRECTION_VISUAL_RTL = 0
        const val TEXT_DIRECTION_VISUAL_LTR = 4
        const val TEXT_DIRECTION_MASK = 4
        const val LETTERS_NOOP = 0
        const val LETTERS_SHAPE = 8
        const val LETTERS_UNSHAPE = 0x10
        const val LETTERS_SHAPE_TASHKEEL_ISOLATED = 0x18
        const val LETTERS_MASK = 0x18
        const val DIGITS_NOOP = 0
        const val DIGITS_EN2AN = 0x20
        const val DIGITS_AN2EN = 0x40
        const val DIGITS_EN2AN_INIT_LR = 0x60
        const val DIGITS_EN2AN_INIT_AL = 0x80
        const val DIGITS_MASK = 0xe0
        const val DIGIT_TYPE_AN = 0
        const val DIGIT_TYPE_AN_EXTENDED = 0x100
        const val DIGIT_TYPE_MASK = 0x0100
        private const val HAMZAFE_CHAR: Char = '\ufe80'
        private const val HAMZA06_CHAR: Char = '\u0621'
        private const val YEH_HAMZA_CHAR: Char = '\u0626'
        private const val YEH_HAMZAFE_CHAR: Char = '\uFE89'
        private const val LAMALEF_SPACE_SUB: Char = '\uffff'
        private const val TASHKEEL_SPACE_SUB: Char = '\ufffe'
        private const val LAM_CHAR: Char = '\u0644'
        private const val SPACE_CHAR: Char = '\u0020'
        private const val SHADDA_CHAR: Char = '\uFE7C'
        private const val SHADDA06_CHAR: Char = '\u0651'
        private const val TATWEEL_CHAR: Char = '\u0640'
        private const val SHADDA_TATWEEL_CHAR: Char = '\uFE7D'
        private const val NEW_TAIL_CHAR: Char = '\uFE73'
        private const val OLD_TAIL_CHAR: Char = '\u200B'
        private const val SHAPE_MODE = 0
        private const val DESHAPE_MODE = 1
        private const val IRRELEVANT = 4
        private const val LAMTYPE = 16
        private const val ALEFTYPE = 32
        private const val LINKR = 1
        private const val LINKL = 2
        private const val LINK_MASK = 3
        private val irrelevantPos = intArrayOf(
            0x0, 0x2, 0x4, 0x6, 0x8, 0xA, 0xC, 0xE,
        )
        private val tailFamilyIsolatedFinal = intArrayOf(
            1, 1, 0, 0, 1, 1, 0, 0, 1, 1,
            0, 0, 1, 1,
        )
        private val tashkeelMedial = intArrayOf(
            0, 1, 0, 0, 0, 0, 0, 1, 0, 1,
            0, 1, 0, 1, 0, 1,
        )
        private val yehHamzaToYeh = charArrayOf(
            0xFEEF.toChar(), 0xFEF0.toChar(),
        )
        private val convertNormalizedLamAlef = charArrayOf(
            '\u0622', '\u0623', '\u0625', '\u0627',
        )
        private val araLink = intArrayOf(
            1           + 32 + 256 * 0x11, 1           + 32 + 256 * 0x13, 1                + 256 * 0x15, 1           + 32 + 256 * 0x17, 1 + 2            + 256 * 0x19, 1           + 32 + 256 * 0x1D, 1 + 2            + 256 * 0x1F, 1                + 256 * 0x23, 1 + 2            + 256 * 0x25, 1 + 2            + 256 * 0x29,
            1 + 2            + 256 * 0x2D, 1 + 2            + 256 * 0x31, 1 + 2            + 256 * 0x35, 1                + 256 * 0x39, 1                + 256 * 0x3B, 1                + 256 * 0x3D, 1                + 256 * 0x3F, 1 + 2            + 256 * 0x41, 1 + 2            + 256 * 0x45, 1 + 2            + 256 * 0x49,
            1 + 2            + 256 * 0x4D, 1 + 2            + 256 * 0x51, 1 + 2            + 256 * 0x55, 1 + 2            + 256 * 0x59, 1 + 2            + 256 * 0x5D, 0, 0, 0, 0, 0,
            1 + 2, 1 + 2            + 256 * 0x61, 1 + 2            + 256 * 0x65, 1 + 2            + 256 * 0x69, 1 + 2       + 16 + 256 * 0x6D, 1 + 2            + 256 * 0x71, 1 + 2            + 256 * 0x75, 1 + 2            + 256 * 0x79, 1                + 256 * 0x7D, 1                + 256 * 0x7F,
            1 + 2            + 256 * 0x81, 4, 4, 4, 4, 4, 4, 4, 4, 4,
            4, 4, 0, 0, 0, 0, 0, 0, 1                + 256 * 0x85, 1                + 256 * 0x87,
            1                + 256 * 0x89, 1                + 256 * 0x8B, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 4, 0,
            1           + 32, 1           + 32, 0, 1           + 32, 1, 1, 1+2, 1+2, 1+2, 1+2,
            1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2,
            1+2, 1+2, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2,
            1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2,
            1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2,
            1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1+2, 1, 1+2,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1+2, 1, 1+2, 1+2, 1+2, 1+2, 1, 1,
        )
        private val presLink = intArrayOf(
            1 + 2, 1 + 2, 1 + 2, 0, 1+ 2, 0, 1+ 2, 1 + 2, 1+ 2, 1 + 2,
            1+2, 1 + 2, 1+ 2, 1 + 2, 1+2, 1 + 2, 0, 0 + 32, 1 + 32, 0 + 32,
            1 + 32, 0, 1, 0 + 32, 1 + 32, 0, 2, 1 + 2, 1, 0 + 32,
            1 + 32, 0, 2, 1 + 2, 1, 0, 1, 0, 2, 1 + 2,
            1, 0, 2, 1 + 2, 1, 0, 2, 1 + 2, 1, 0,
            2, 1 + 2, 1, 0, 2, 1 + 2, 1, 0, 1, 0,
            1, 0, 1, 0, 1, 0, 2, 1+2, 1, 0,
            2, 1+2, 1, 0, 2, 1+2, 1, 0, 2, 1+2,
            1, 0, 2, 1+2, 1, 0, 2, 1+2, 1, 0,
            2, 1+2, 1, 0, 2, 1+2, 1, 0, 2, 1+2,
            1, 0, 2, 1+2, 1, 0, 2, 1+2, 1, 0 + 16,
            2 + 16, 1 + 2 +16, 1 + 16, 0, 2, 1+2, 1, 0, 2, 1+2,
            1, 0, 2, 1+2, 1, 0, 1, 0, 1, 0,
            2, 1+2, 1, 0, 1, 0, 1, 0, 1, 0,
            1,
        )
        private val convertFEto06 = intArrayOf(
            0x64B, 0x64B, 0x64C, 0x64C, 0x64D, 0x64D, 0x64E, 0x64E, 0x64F, 0x64F,
            0x650, 0x650, 0x651, 0x651, 0x652, 0x652, 0x621, 0x622, 0x622, 0x623,
            0x623, 0x624, 0x624, 0x625, 0x625, 0x626, 0x626, 0x626, 0x626, 0x627,
            0x627, 0x628, 0x628, 0x628, 0x628, 0x629, 0x629, 0x62A, 0x62A, 0x62A,
            0x62A, 0x62B, 0x62B, 0x62B, 0x62B, 0x62C, 0x62C, 0x62C, 0x62C, 0x62D,
            0x62D, 0x62D, 0x62D, 0x62E, 0x62E, 0x62E, 0x62E, 0x62F, 0x62F, 0x630,
            0x630, 0x631, 0x631, 0x632, 0x632, 0x633, 0x633, 0x633, 0x633, 0x634,
            0x634, 0x634, 0x634, 0x635, 0x635, 0x635, 0x635, 0x636, 0x636, 0x636,
            0x636, 0x637, 0x637, 0x637, 0x637, 0x638, 0x638, 0x638, 0x638, 0x639,
            0x639, 0x639, 0x639, 0x63A, 0x63A, 0x63A, 0x63A, 0x641, 0x641, 0x641,
            0x641, 0x642, 0x642, 0x642, 0x642, 0x643, 0x643, 0x643, 0x643, 0x644,
            0x644, 0x644, 0x644, 0x645, 0x645, 0x645, 0x645, 0x646, 0x646, 0x646,
            0x646, 0x647, 0x647, 0x647, 0x647, 0x648, 0x648, 0x649, 0x649, 0x64A,
            0x64A, 0x64A, 0x64A, 0x65C, 0x65C, 0x65D, 0x65D, 0x65E, 0x65E, 0x65F,
            0x65F,
        )
        private val shapeTable = arrayOf(
            arrayOf(intArrayOf(0,0,0,0), intArrayOf(0,0,0,0), intArrayOf(0,1,0,3), intArrayOf(0,1,0,1)),
            arrayOf(intArrayOf(0,0,2,2), intArrayOf(0,0,1,2), intArrayOf(0,1,1,2), intArrayOf(0,1,1,3)),
            arrayOf(intArrayOf(0,0,0,0), intArrayOf(0,0,0,0), intArrayOf(0,1,0,3), intArrayOf(0,1,0,3)),
            arrayOf(intArrayOf(0,0,1,2), intArrayOf(0,0,1,2), intArrayOf(0,1,1,2), intArrayOf(0,1,1,3))
        )
    }

}
