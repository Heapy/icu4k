// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.text.UTF16
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

open class Padder(paddingString: String?, internal var targetWidth: Int, position: PadPosition?) {
    internal var paddingString: String = paddingString ?: FALLBACK_PADDING_STRING
    internal var position: PadPosition = position ?: PadPosition.BEFORE_PREFIX
    enum class PadPosition {
        BEFORE_PREFIX, AFTER_PREFIX, BEFORE_SUFFIX, AFTER_SUFFIX;
        fun toOld(): Int = ordinal
        companion object {
            @JvmStatic fun fromOld(old: Int): PadPosition = entries.getOrNull(old) ?: throw IllegalArgumentException("Don't know how to map $old")
        }
    }
    open fun isValid(): Boolean = targetWidth > 0
    open fun padAndApply(mod1: Modifier?, mod2: Modifier?, string: FormattedStringBuilder?, leftIndex: Int, rightIndex: Int): Int {
        val modLength = mod1!!.getCodePointCount() + mod2!!.getCodePointCount()
        val requiredPadding = targetWidth - modLength - string!!.codePointCount()
        assertPadder(leftIndex == 0 && rightIndex == string.length)
        var length = 0
        if (requiredPadding <= 0) {
            length += mod1.apply(string, leftIndex, rightIndex)
            length += mod2.apply(string, leftIndex, rightIndex + length)
            return length
        }
        if (position == PadPosition.AFTER_PREFIX) length += addPadding(requiredPadding, string, leftIndex)
        else if (position == PadPosition.BEFORE_SUFFIX) length += addPadding(requiredPadding, string, rightIndex + length)
        length += mod1.apply(string, leftIndex, rightIndex + length)
        length += mod2.apply(string, leftIndex, rightIndex + length)
        if (position == PadPosition.BEFORE_PREFIX) length += addPadding(requiredPadding, string, leftIndex)
        else if (position == PadPosition.AFTER_SUFFIX) length += addPadding(requiredPadding, string, rightIndex + length)
        return length
    }
    private fun addPadding(required: Int, string: FormattedStringBuilder, index: Int): Int {
        repeat(required) { string.insert(index, paddingString, null) }
        return paddingString.length * required
    }
    companion object {
        const val FALLBACK_PADDING_STRING = " "
        @JvmField val NONE = Padder(null, -1, null)
        @JvmStatic fun none(): Padder = NONE
        @JvmStatic fun codePoints(cp: Int, targetWidth: Int, position: PadPosition?): Padder {
            require(targetWidth >= 0) { "Padding width must not be negative" }
            if (cp !in 0..0x10ffff) throw IllegalArgumentException("Not a valid Unicode code point: 0x${cp.toUInt().toString(16).uppercase()}")
            return Padder(UTF16.valueOf(cp), targetWidth, position)
        }
    }
}
internal expect fun assertPadder(condition: Boolean)
