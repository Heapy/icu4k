// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.DecimalRoundingDirection
import kotlin.jvm.JvmStatic

/** Minimum zero padding and maximum truncation for the integer part of a number. */
class IntegerWidth private constructor(internal val minInt: Int, internal val maxInt: Int) {
    fun truncateAt(maxInt: Int): IntegerWidth = when {
        maxInt == this.maxInt -> this
        maxInt >= 0 && maxInt <= DecimalRoundingDirection.MAX_INT_FRAC_SIG && maxInt >= minInt -> IntegerWidth(minInt, maxInt)
        minInt == 1 && maxInt == -1 -> DEFAULT
        maxInt == -1 -> IntegerWidth(minInt, -1)
        else -> throw IllegalArgumentException("Integer digits must be between -1 and ${DecimalRoundingDirection.MAX_INT_FRAC_SIG} (inclusive)")
    }
    companion object {
        internal val DEFAULT = IntegerWidth(1, -1)
        @JvmStatic fun zeroFillTo(minInt: Int): IntegerWidth = when {
            minInt == 1 -> DEFAULT
            minInt >= 0 && minInt <= DecimalRoundingDirection.MAX_INT_FRAC_SIG -> IntegerWidth(minInt, -1)
            else -> throw IllegalArgumentException("Integer digits must be between 0 and ${DecimalRoundingDirection.MAX_INT_FRAC_SIG} (inclusive)")
        }
    }
}
