// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.MultiplierProducer
import io.github.heapy.icu4k.impl.number.RoundingUtils
import io.github.heapy.icu4k.text.PluralRules.Operand
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.BigDecimals
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.Currency.CurrencyUsage
import io.github.heapy.icu4k.util.MathContext
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Rounding and minimum displayed precision for the number formatter. */
abstract class Precision internal constructor() {
    @JvmField internal var mathContext: MathContext? = RoundingUtils.DEFAULT_MATH_CONTEXT_UNLIMITED
    @JvmField internal var trailingZeroDisplay: TrailingZeroDisplay? = null

    open fun trailingZeroDisplay(trailingZeroDisplay: TrailingZeroDisplay?): Precision = createCopy().also {
        it.trailingZeroDisplay = trailingZeroDisplay
    }
    open fun withMode(mathContext: MathContext?): Precision {
        if (this.mathContext!!.equals(mathContext)) return this
        return createCopy().also { it.mathContext = mathContext }
    }
    internal abstract fun createCopy(): Precision
    internal fun createCopyHelper(copy: Precision) {
        copy.mathContext = mathContext
        copy.trailingZeroDisplay = trailingZeroDisplay
    }
    abstract fun apply(value: DecimalQuantity?)
    internal fun withLocaleData(currency: Currency?): Precision = if (this is CurrencyPrecision) withCurrency(currency) else this
    internal fun chooseMultiplierAndApply(input: DecimalQuantity?, producer: MultiplierProducer?): Int {
        assertPrecision { !input!!.isZeroish() }
        val magnitude = input!!.getMagnitude()
        val multiplier = producer!!.getMultiplier(magnitude)
        input.adjustMagnitude(multiplier)
        apply(input)
        if (input.isZeroish()) return multiplier
        if (input.getMagnitude() == magnitude + multiplier) return multiplier
        val next = producer.getMultiplier(magnitude + 1)
        if (multiplier == next) return multiplier
        input.adjustMagnitude(next - multiplier)
        apply(input)
        return next
    }
    internal fun setResolvedMinFraction(value: DecimalQuantity?, resolved: Int) {
        if (trailingZeroDisplay == null || trailingZeroDisplay == TrailingZeroDisplay.AUTO || value!!.getPluralOperand(Operand.t) != 0.0) {
            value!!.setMinFraction(resolved)
        }
    }

    open class BogusRounder : Precision() {
        override fun apply(value: DecimalQuantity?) { throw AssertionError("BogusRounder must not be applied") }
        internal override fun createCopy(): BogusRounder = BogusRounder().also { createCopyHelper(it) }
        open fun into(precision: Precision?): Precision = precision!!.createCopy().also { createCopyHelper(it) }
    }
    internal open class InfiniteRounderImpl : Precision() {
        override fun apply(value: DecimalQuantity?) { value!!.roundToInfinity(); setResolvedMinFraction(value, 0) }
        override fun createCopy(): InfiniteRounderImpl = InfiniteRounderImpl().also { createCopyHelper(it) }
    }
    internal open class FractionRounderImpl(@JvmField val minFrac: Int, @JvmField val maxFrac: Int) : FractionPrecision() {
        override fun apply(value: DecimalQuantity?) {
            value!!.roundToMagnitude(roundingMagnitudeFraction(maxFrac), mathContext)
            setResolvedMinFraction(value, maxOf(0, -displayMagnitudeFraction(minFrac)))
        }
        override fun createCopy(): FractionRounderImpl = FractionRounderImpl(minFrac, maxFrac).also { createCopyHelper(it) }
    }
    internal open class SignificantRounderImpl(@JvmField val minSig: Int, @JvmField val maxSig: Int) : Precision() {
        override fun apply(value: DecimalQuantity?) {
            val magnitude = roundingMagnitudeSignificant(value, maxSig)
            value!!.roundToMagnitude(magnitude, mathContext)
            setResolvedMinFraction(value, maxOf(0, -displayMagnitudeSignificant(value, minSig)))
            if (value.isZeroish() && minSig > 0) value.setMinInteger(1)
        }
        fun apply(quantity: DecimalQuantity?, minInt: Int) {
            assertPrecision { quantity!!.isZeroish() }
            setResolvedMinFraction(quantity, minSig - minInt)
        }
        override fun createCopy(): SignificantRounderImpl = SignificantRounderImpl(minSig, maxSig).also { createCopyHelper(it) }
    }
    internal open class FracSigRounderImpl(@JvmField val minFrac: Int, @JvmField val maxFrac: Int,
        @JvmField val minSig: Int, @JvmField val maxSig: Int, @JvmField val priority: RoundingPriority?,
        @JvmField val retain: Boolean) : Precision() {
        override fun apply(value: DecimalQuantity?) {
            val rounding1 = roundingMagnitudeFraction(maxFrac)
            var rounding2 = roundingMagnitudeSignificant(value, maxSig)
            val rounding = if (priority == RoundingPriority.RELAXED) minOf(rounding1, rounding2) else maxOf(rounding1, rounding2)
            if (!value!!.isZeroish()) {
                val upper = value.getMagnitude()
                value.roundToMagnitude(rounding, mathContext)
                if (!value.isZeroish() && value.getMagnitude() != upper && rounding1 == rounding2) rounding2++
            }
            val display1 = displayMagnitudeFraction(minFrac)
            val display2 = displayMagnitudeSignificant(value, minSig)
            val display = if (retain) minOf(display1, display2)
                else if (priority == RoundingPriority.RELAXED) { if (rounding2 <= rounding1) display2 else display1 }
                else {
                    assertPrecision { priority == RoundingPriority.STRICT }
                    if (rounding2 <= rounding1) display1 else display2
                }
            setResolvedMinFraction(value, maxOf(0, -display))
        }
        override fun createCopy(): FracSigRounderImpl = FracSigRounderImpl(minFrac, maxFrac, minSig, maxSig, priority, retain).also { createCopyHelper(it) }
    }
    internal open class IncrementRounderImpl(@JvmField val increment: BigDecimal) : Precision() {
        override fun apply(value: DecimalQuantity?) {
            value!!.roundToIncrement(increment, mathContext)
            setResolvedMinFraction(value, maxOf(0, increment.scale()))
        }
        override fun createCopy(): IncrementRounderImpl = IncrementRounderImpl(increment).also { createCopyHelper(it) }
    }
    internal open class IncrementOneRounderImpl(increment: BigDecimal, @JvmField val minFrac: Int, @JvmField val maxFrac: Int) : IncrementRounderImpl(increment) {
        override fun apply(value: DecimalQuantity?) { value!!.roundToMagnitude(-maxFrac, mathContext); setResolvedMinFraction(value, minFrac) }
        override fun createCopy(): IncrementOneRounderImpl = IncrementOneRounderImpl(increment, minFrac, maxFrac).also { createCopyHelper(it) }
    }
    internal open class IncrementFiveRounderImpl(increment: BigDecimal, @JvmField val minFrac: Int, @JvmField val maxFrac: Int) : IncrementRounderImpl(increment) {
        override fun apply(value: DecimalQuantity?) { value!!.roundToNickel(-maxFrac, mathContext); setResolvedMinFraction(value, minFrac) }
        override fun createCopy(): IncrementFiveRounderImpl = IncrementFiveRounderImpl(increment, minFrac, maxFrac).also { createCopyHelper(it) }
    }
    internal open class CurrencyRounderImpl(@JvmField val usage: CurrencyUsage) : CurrencyPrecision() {
        override fun apply(value: DecimalQuantity?) { throw AssertionError() }
        override fun createCopy(): CurrencyRounderImpl = CurrencyRounderImpl(usage).also { createCopyHelper(it) }
    }

    companion object {
        @JvmField val BOGUS_PRECISION = BogusRounder()
        internal val NONE = InfiniteRounderImpl()
        internal val FIXED_FRAC_0 = FractionRounderImpl(0, 0)
        internal val FIXED_FRAC_2 = FractionRounderImpl(2, 2)
        internal val DEFAULT_MAX_FRAC_6 = FractionRounderImpl(0, 6)
        internal val FIXED_SIG_2 = SignificantRounderImpl(2, 2)
        internal val FIXED_SIG_3 = SignificantRounderImpl(3, 3)
        internal val RANGE_SIG_2_3 = SignificantRounderImpl(2, 3)
        internal val COMPACT_STRATEGY = FracSigRounderImpl(0, 0, 1, 2, RoundingPriority.RELAXED, false)
        internal val NICKEL = IncrementFiveRounderImpl(BigDecimal("0.05"), 2, 2)
        internal val MONETARY_STANDARD = CurrencyRounderImpl(CurrencyUsage.STANDARD)
        internal val MONETARY_CASH = CurrencyRounderImpl(CurrencyUsage.CASH)
        private const val FRACTION_ERROR = "Fraction length must be between 0 and 999 (inclusive)"
        internal const val SIGNIFICANT_ERROR = "Significant digits must be between 1 and 999 (inclusive)"
        @JvmStatic fun unlimited(): Precision = NONE
        @JvmStatic fun integer(): FractionPrecision = constructFraction(0, 0)
        @JvmStatic fun fixedFraction(minMaxFractionPlaces: Int): FractionPrecision {
            require(minMaxFractionPlaces in 0..999) { FRACTION_ERROR }
            return constructFraction(minMaxFractionPlaces, minMaxFractionPlaces)
        }
        @JvmStatic fun minFraction(minFractionPlaces: Int): FractionPrecision {
            require(minFractionPlaces in 0..999) { FRACTION_ERROR }
            return constructFraction(minFractionPlaces, -1)
        }
        @JvmStatic fun maxFraction(maxFractionPlaces: Int): FractionPrecision {
            require(maxFractionPlaces in 0..999) { FRACTION_ERROR }
            return constructFraction(0, maxFractionPlaces)
        }
        @JvmStatic fun minMaxFraction(minFractionPlaces: Int, maxFractionPlaces: Int): FractionPrecision {
            require(minFractionPlaces >= 0 && maxFractionPlaces <= 999 && minFractionPlaces <= maxFractionPlaces) { FRACTION_ERROR }
            return constructFraction(minFractionPlaces, maxFractionPlaces)
        }
        @JvmStatic fun fixedSignificantDigits(minMaxSignificantDigits: Int): Precision {
            require(minMaxSignificantDigits in 1..999) { SIGNIFICANT_ERROR }
            return constructSignificant(minMaxSignificantDigits, minMaxSignificantDigits)
        }
        @JvmStatic fun minSignificantDigits(minSignificantDigits: Int): Precision {
            require(minSignificantDigits in 1..999) { SIGNIFICANT_ERROR }
            return constructSignificant(minSignificantDigits, -1)
        }
        @JvmStatic fun maxSignificantDigits(maxSignificantDigits: Int): Precision {
            require(maxSignificantDigits in 1..999) { SIGNIFICANT_ERROR }
            return constructSignificant(1, maxSignificantDigits)
        }
        @JvmStatic fun minMaxSignificantDigits(minSignificantDigits: Int, maxSignificantDigits: Int): Precision {
            require(minSignificantDigits >= 1 && maxSignificantDigits <= 999 && minSignificantDigits <= maxSignificantDigits) { SIGNIFICANT_ERROR }
            return constructSignificant(minSignificantDigits, maxSignificantDigits)
        }
        @JvmStatic fun increment(roundingIncrement: BigDecimal?): Precision {
            require(roundingIncrement != null && roundingIncrement.compareTo(BigDecimals.ZERO) > 0) { "Rounding increment must be positive and non-null" }
            return constructIncrement(roundingIncrement)
        }
        @JvmStatic fun currency(currencyUsage: CurrencyUsage?): CurrencyPrecision {
            require(currencyUsage != null) { "CurrencyUsage must be non-null" }
            return constructCurrency(currencyUsage)
        }
        internal fun constructFraction(minFrac: Int, maxFrac: Int): FractionPrecision = when {
            minFrac == 0 && maxFrac == 0 -> FIXED_FRAC_0
            minFrac == 2 && maxFrac == 2 -> FIXED_FRAC_2
            minFrac == 0 && maxFrac == 6 -> DEFAULT_MAX_FRAC_6
            else -> FractionRounderImpl(minFrac, maxFrac)
        }
        internal fun constructSignificant(minSig: Int, maxSig: Int): Precision = when {
            minSig == 2 && maxSig == 2 -> FIXED_SIG_2
            minSig == 3 && maxSig == 3 -> FIXED_SIG_3
            minSig == 2 && maxSig == 3 -> RANGE_SIG_2_3
            else -> SignificantRounderImpl(minSig, maxSig)
        }
        internal fun constructFractionSignificant(base_: FractionPrecision, minSig: Int, maxSig: Int, priority: RoundingPriority?, retain: Boolean): Precision {
            assertPrecision { base_ is FractionRounderImpl }
            val base = base_ as FractionRounderImpl
            val result = if (base.minFrac == 0 && base.maxFrac == 0 && minSig == 1 && maxSig == 2 && priority == RoundingPriority.RELAXED && !retain) COMPACT_STRATEGY
                else FracSigRounderImpl(base.minFrac, base.maxFrac, minSig, maxSig, priority, retain)
            return result.withMode(base.mathContext)
        }
        internal fun constructIncrement(increment: BigDecimal): Precision {
            if (increment.equals(NICKEL.increment)) return NICKEL
            val reduced = increment.stripTrailingZeros()
            if (reduced.precision() == 1) {
                val minFrac = increment.scale()
                val maxFrac = reduced.scale()
                when (precisionIncrementKind(reduced)) {
                    1 -> return IncrementOneRounderImpl(increment, minFrac, maxFrac)
                    5 -> return IncrementFiveRounderImpl(increment, minFrac, maxFrac)
                }
            }
            return IncrementRounderImpl(increment)
        }
        internal fun constructCurrency(usage: CurrencyUsage): CurrencyPrecision = when (usage) {
            CurrencyUsage.STANDARD -> MONETARY_STANDARD
            CurrencyUsage.CASH -> MONETARY_CASH
        }
        internal fun constructFromCurrency(base_: CurrencyPrecision, currency: Currency): Precision {
            assertPrecision { base_ is CurrencyRounderImpl }
            val base = base_ as CurrencyRounderImpl
            val increment = currency.getRoundingIncrement(base.usage)
            val result = if (increment != 0.0) constructIncrement(BigDecimals.valueOf(increment))
                else currency.getDefaultFractionDigits(base.usage).let { constructFraction(it, it) }
            return result.withMode(base.mathContext)
        }
        private fun roundingMagnitudeFraction(maxFrac: Int): Int = if (maxFrac == -1) Int.MIN_VALUE else -maxFrac
        private fun roundingMagnitudeSignificant(value: DecimalQuantity?, maxSig: Int): Int {
            if (maxSig == -1) return Int.MIN_VALUE
            return (if (value!!.isZeroish()) 0 else value.getMagnitude()) - maxSig + 1
        }
        private fun displayMagnitudeFraction(minFrac: Int): Int = if (minFrac == 0) Int.MAX_VALUE else -minFrac
        private fun displayMagnitudeSignificant(value: DecimalQuantity?, minSig: Int): Int =
            (if (value!!.isZeroish()) 0 else value.getMagnitude()) - minSig + 1
    }
}

internal expect fun precisionIncrementKind(reduced: BigDecimal): Int
internal expect fun assertPrecision(condition: () -> Boolean)
