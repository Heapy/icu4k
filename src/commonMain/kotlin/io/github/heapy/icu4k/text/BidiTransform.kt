// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacter

/** Conversion between logical and visual bidirectional text representations. Mutable, like ICU4J. */
open class BidiTransform {
    enum class Order { LOGICAL, VISUAL }
    enum class Mirroring { OFF, ON }

    private lateinit var bidi: Bidi
    private var text = ""
    private var reorderingOptions = 0
    private var shapingOptions = 0

    open fun transform(text: CharSequence?, inParaLevel: Byte, inOrder: Order?,
        outParaLevel: Byte, outOrder: Order?, doMirroring: Mirroring?, shapingOptions: Int): String {
        if (text == null || inOrder == null || outOrder == null || doMirroring == null) {
            throw IllegalArgumentException()
        }
        this.text = text.toString()
        val inLevel = if (inParaLevel.toInt() and 126 == 126) {
            val base = Bidi.getBaseDirection(this.text)
            if (base != Bidi.NEUTRAL) base.toInt() else if (inParaLevel == Bidi.LEVEL_DEFAULT_RTL) 1 else 0
        } else inParaLevel.toInt() and 1
        val outLevel = if (outParaLevel.toInt() and 126 == 126) {
            inLevel
        } else outParaLevel.toInt() and 1
        bidi = Bidi()
        reorderingOptions = if (doMirroring == Mirroring.ON) Bidi.DO_MIRRORING.toInt() else 0
        this.shapingOptions = shapingOptions and ArabicShaping.TEXT_DIRECTION_MASK.inv()
        val logicalIn = inOrder == Order.LOGICAL
        val logicalOut = outOrder == Order.LOGICAL
        when {
            logicalIn && !logicalOut -> {
                if (inLevel == 0) shapeArabic(0, 0)
                resolve(inLevel, Bidi.REORDER_DEFAULT.toInt())
                reorder()
                if (inLevel == 1) shapeArabic(0, ArabicShaping.TEXT_DIRECTION_VISUAL_LTR)
                if (outLevel == 1) reverse()
            }
            !logicalIn && logicalOut -> {
                if (inLevel == 1) reverse()
                if (outLevel == 1) shapeArabic(0, ArabicShaping.TEXT_DIRECTION_VISUAL_LTR)
                resolve(outLevel, Bidi.REORDER_INVERSE_LIKE_DIRECT.toInt())
                reorder()
                if (outLevel == 0) shapeArabic(0, 0)
            }
            logicalIn -> {
                if (inLevel == 0 && outLevel == 1) shapeArabic(0, 0)
                resolve(inLevel, Bidi.REORDER_DEFAULT.toInt())
                mirror()
                if (inLevel != outLevel) {
                    resolve(inLevel, Bidi.REORDER_RUNS_ONLY.toInt())
                    reorder()
                }
                if (inLevel == 1) {
                    shapeArabic(if (outLevel == 1) ArabicShaping.TEXT_DIRECTION_VISUAL_LTR else 0, 0)
                } else if (outLevel == 0) shapeArabic(0, 0)
            }
            else -> {
                if (inLevel == 1) reverse()
                resolve(0, Bidi.REORDER_DEFAULT.toInt())
                mirror()
                shapeArabic(0, ArabicShaping.TEXT_DIRECTION_VISUAL_LTR)
                if (outLevel == 1) reverse()
            }
        }
        return this.text
    }

    private fun resolve(level: Int, options: Int) {
        bidi.setInverse((options and Bidi.REORDER_INVERSE_LIKE_DIRECT.toInt()) != 0)
        bidi.setReorderingMode(options)
        bidi.setPara(text, level.toByte(), null)
    }
    private fun reorder() {
        text = bidi.writeReordered(reorderingOptions)
        reorderingOptions = Bidi.REORDER_DEFAULT.toInt()
    }
    private fun reverse() { text = Bidi.writeReverse(text, Bidi.OPTION_DEFAULT.toInt()) }
    private fun mirror() {
        if (reorderingOptions and Bidi.DO_MIRRORING.toInt() == 0) return
        val buffer = StringBuilder(text)
        val levels = bidi.getLevels()
        var i = 0
        while (i < levels.size) {
            val cp = UTF16.charAt(buffer, i)
            if (levels[i].toInt() and 1 != 0) UTF16.setCharAt(buffer, i, UCharacter.getMirror(cp))
            i += UTF16.getCharCount(cp)
        }
        text = buffer.toString()
        reorderingOptions = reorderingOptions and Bidi.DO_MIRRORING.toInt().inv()
    }
    private fun shapeArabic(digitsDir: Int, lettersDir: Int) {
        if (digitsDir == lettersDir) shapeArabic(shapingOptions or digitsDir)
        else {
            shapeArabic((shapingOptions and ArabicShaping.LETTERS_MASK.inv()) or digitsDir)
            shapeArabic((shapingOptions and ArabicShaping.DIGITS_MASK.inv()) or lettersDir)
        }
    }
    private fun shapeArabic(options: Int) {
        if (options != 0) {
            val shaper = ArabicShaping(options)
            try { text = shaper.shape(text) } catch (_: ArabicShapingException) { }
        }
    }
}
