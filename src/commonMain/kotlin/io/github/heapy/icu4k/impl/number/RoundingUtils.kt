// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.number.Scale
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.MathContext
import io.github.heapy.icu4k.util.RoundingMode
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Rounding direction, cached contexts and scale projection for the number pipeline. */
open class RoundingUtils {
    companion object {
        const val SECTION_LOWER = 1
        const val SECTION_MIDPOINT = 2
        const val SECTION_UPPER = 3
        const val MAX_INT_FRAC_SIG = 999
        @JvmField val DEFAULT_ROUNDING_MODE = RoundingMode.HALF_EVEN
        private val contextsUnlimited = Array(RoundingMode.entries.size) { MathContext(0, RoundingMode.entries[it]) }
        // ICU initializes each entry with MathContext(34), whose mode is HALF_UP.
        private val contexts34 = Array(RoundingMode.entries.size) { MathContext(34) }
        @JvmField val DEFAULT_MATH_CONTEXT_UNLIMITED = contextsUnlimited[DEFAULT_ROUNDING_MODE.ordinal]
        @JvmField val DEFAULT_MATH_CONTEXT_34_DIGITS = contexts34[DEFAULT_ROUNDING_MODE.ordinal]

        @JvmStatic fun getRoundingDirection(isEven: Boolean, isNegative: Boolean, section: Int, roundingMode: Int, reference: Any?): Boolean =
            DecimalRoundingDirection.getRoundingDirection(isEven, isNegative, section, roundingMode, reference)
        @JvmStatic fun roundsAtMidpoint(roundingMode: Int): Boolean = DecimalRoundingDirection.roundsAtMidpoint(roundingMode)
        @JvmStatic fun getMathContextOrUnlimited(properties: DecimalFormatProperties?): MathContext {
            val context = properties!!.getMathContext()
            if (context != null) return context
            return contextsUnlimited[(properties.getRoundingMode() ?: RoundingMode.HALF_EVEN).ordinal]
        }
        @JvmStatic fun getMathContextOr34Digits(properties: DecimalFormatProperties?): MathContext {
            val context = properties!!.getMathContext()
            if (context != null) return context
            return contexts34[(properties.getRoundingMode() ?: RoundingMode.HALF_EVEN).ordinal]
        }
        @JvmStatic fun mathContextUnlimited(roundingMode: RoundingMode?): MathContext = contextsUnlimited[roundingMode!!.ordinal]
        @JvmStatic fun getPluralSafe(rounder: Precision?, rules: PluralRules?, dq: DecimalQuantity?): StandardPlural {
            if (rounder == null) return dq!!.getStandardPlural(rules)
            val copy = dq!!.createCopy()
            rounder.apply(copy)
            return copy.getStandardPlural(rules)
        }
        @JvmStatic fun scaleFromProperties(properties: DecimalFormatProperties?): Scale? {
            val context = getMathContextOr34Digits(properties)
            val p = properties!!
            return if (p.getMagnitudeMultiplier() != 0) {
                Scale.powerOfTen(p.getMagnitudeMultiplier()).withMathContext(context)
            } else if (p.getMultiplier() != null) {
                Scale.byBigDecimal(p.getMultiplier()).withMathContext(context)
            } else null
        }
    }
}
