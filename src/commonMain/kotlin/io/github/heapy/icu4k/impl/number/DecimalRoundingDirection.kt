// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

/** Direction selection from RoundingUtils; mode integers match java.math.RoundingMode ordinals. */
internal object DecimalRoundingDirection {
    const val SECTION_LOWER = 1
    const val SECTION_MIDPOINT = 2
    const val SECTION_UPPER = 3
    const val MAX_INT_FRAC_SIG = 999
    fun getRoundingDirection(even: Boolean, negative: Boolean, section: Int, mode: Int, reference: Any?): Boolean {
        when (mode) {
            0 -> return false
            1 -> return true
            2 -> return negative
            3 -> return !negative
            4 -> when (section) { SECTION_LOWER -> return true; SECTION_MIDPOINT, SECTION_UPPER -> return false }
            5 -> when (section) { SECTION_LOWER, SECTION_MIDPOINT -> return true; SECTION_UPPER -> return false }
            6 -> when (section) { SECTION_LOWER -> return true; SECTION_MIDPOINT -> return even; SECTION_UPPER -> return false }
        }
        throw ArithmeticException("Rounding is required on " + reference!!.toString())
    }
    fun roundsAtMidpoint(mode: Int): Boolean = mode !in 0..3
}
