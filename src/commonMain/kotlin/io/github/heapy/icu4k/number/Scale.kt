// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.RoundingUtils
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.BigDecimals
import io.github.heapy.icu4k.util.MathContext
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Multiplication by a power of ten and/or an arbitrary decimal for number formatting. */
open class Scale private constructor(magnitude: Int, arbitrary: BigDecimal?, @JvmField internal val mc: MathContext?) {
    @JvmField internal val magnitude: Int
    @JvmField internal val arbitrary: BigDecimal?
    @JvmField internal val reciprocal: BigDecimal?

    private constructor(magnitude: Int, arbitrary: BigDecimal?) :
        this(magnitude, arbitrary, RoundingUtils.DEFAULT_MATH_CONTEXT_34_DIGITS)

    init {
        var power = magnitude
        var factor = arbitrary
        if (factor != null) {
            factor = if (factor.compareTo(BigDecimals.ZERO) == 0) BigDecimals.ZERO else factor.stripTrailingZeros()
            if (factor.precision() == 1 && scaleHasUnitCoefficient(factor)) {
                power -= factor.scale()
                factor = null
            }
        }
        this.magnitude = power
        this.arbitrary = factor
        reciprocal = if (factor != null && BigDecimals.ZERO.compareTo(factor) != 0) BigDecimals.ONE.divide(factor, mc) else null
    }

    internal fun isValid(): Boolean = magnitude != 0 || arbitrary != null
    fun withMathContext(mc: MathContext?): Scale {
        if (this.mc!!.equals(mc)) return this
        return Scale(magnitude, arbitrary, mc)
    }
    fun applyTo(quantity: DecimalQuantity?) {
        quantity!!.adjustMagnitude(magnitude)
        if (arbitrary != null) quantity.multiplyBy(arbitrary)
    }
    fun applyReciprocalTo(quantity: DecimalQuantity?) {
        quantity!!.adjustMagnitude(-magnitude)
        if (reciprocal != null) {
            quantity.multiplyBy(reciprocal)
            quantity.roundToMagnitude(quantity.getMagnitude() - mc!!.getPrecision(), mc)
        }
    }

    companion object {
        private val DEFAULT = Scale(0, null)
        private val HUNDRED = Scale(2, null)
        private val THOUSAND = Scale(3, null)
        private val BIG_DECIMAL_100 = BigDecimals.valueOf(100L)
        private val BIG_DECIMAL_1000 = BigDecimals.valueOf(1000L)
        @JvmStatic fun none(): Scale = DEFAULT
        @JvmStatic fun powerOfTen(power: Int): Scale = when (power) {
            0 -> DEFAULT
            2 -> HUNDRED
            3 -> THOUSAND
            else -> Scale(power, null)
        }
        @JvmStatic fun byBigDecimal(multiplicand: BigDecimal?): Scale = when {
            multiplicand!!.compareTo(BigDecimals.ONE) == 0 -> DEFAULT
            multiplicand.compareTo(BIG_DECIMAL_100) == 0 -> HUNDRED
            multiplicand.compareTo(BIG_DECIMAL_1000) == 0 -> THOUSAND
            else -> Scale(0, multiplicand)
        }
        @JvmStatic fun byDouble(multiplicand: Double): Scale = when (multiplicand) {
            1.0 -> DEFAULT
            100.0 -> HUNDRED
            1000.0 -> THOUSAND
            else -> Scale(0, BigDecimals.valueOf(multiplicand))
        }
        @JvmStatic fun byDoubleAndPowerOfTen(multiplicand: Double, power: Int): Scale =
            Scale(power, BigDecimals.valueOf(multiplicand))
    }
}

/** Preserve java.math.BigInteger equality at the JVM boundary without a production Java dependency on other targets. */
internal expect fun scaleHasUnitCoefficient(value: BigDecimal): Boolean
