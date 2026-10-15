// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.units.UnitDecimal
import io.github.heapy.icu4k.text.FormatFieldPosition
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.MathContext

/** Public numeric adapter; custom protected storage hooks are ported separately. */
abstract class DecimalQuantity_AbstractBCD internal constructor(internal val core: DecimalQuantityCore) : DecimalQuantity {
    protected constructor() : this(DecimalQuantityCore())
    var explicitExactDouble: Boolean
        get() = core.explicitlyExactDouble
        set(value) { core.explicitlyExactDouble = value }
    override fun setMinInteger(minInt: Int) = core.setMinInteger(minInt)
    override fun setMinFraction(minFrac: Int) = core.setMinFraction(minFrac)
    override fun applyMaxInteger(maxInt: Int) = core.applyMaxInteger(maxInt)
    override fun roundToIncrement(roundingInterval: BigDecimal?, mathContext: MathContext?) = core.roundToIncrement(roundingInterval, mathContext)
    override fun roundToNickel(magnitude: Int, mathContext: MathContext?) = core.roundToNickel(magnitude, mathContext)
    override fun roundToMagnitude(roundingMagnitude: Int, mathContext: MathContext?) = core.roundToMagnitude(roundingMagnitude, mathContext)
    override fun roundToInfinity() = core.roundToInfinity()
    override fun multiplyBy(multiplicand: BigDecimal?) = core.multiplyBy(multiplicand)
    override fun negate() = core.negate()
    override fun adjustMagnitude(delta: Int) = core.adjustMagnitude(delta)
    override fun getMagnitude(): Int = core.getMagnitude()
    override fun getExponent(): Int = core.getExponent()
    override fun adjustExponent(delta: Int) = core.adjustExponent(delta)
    override fun resetExponent() = core.resetExponent()
    override fun isZeroish(): Boolean = core.isZeroish()
    override fun isNegative(): Boolean = core.isNegative()
    override fun signum(): Modifier.Signum = core.signum()
    override fun isNaN(): Boolean = core.isNaN()
    override fun isInfinite(): Boolean = core.isInfinite()
    override fun isHasIntegerValue(): Boolean = core.isHasIntegerValue()
    override fun toDouble(): Double = core.toDouble()
    override fun toBigDecimal(): BigDecimal = core.toBigDecimal()
    override fun toLong(truncateIfOverflow: Boolean): Long = core.toLong(truncateIfOverflow)
    override fun setToBigDecimal(input: BigDecimal?) = core.setToBigDecimal(input)
    override fun getDigit(magnitude: Int): Byte = core.getDigit(magnitude)
    override fun getUpperDisplayMagnitude(): Int = core.getUpperDisplayMagnitude()
    override fun getLowerDisplayMagnitude(): Int = core.getLowerDisplayMagnitude()
    override fun toPlainString(): String = core.toPlainString()
    override fun toExponentString(): String = core.toExponentString()
    override fun getPositionFingerprint(): Long = core.getPositionFingerprint()
    override fun populateUFieldPosition(fp: FormatFieldPosition?) = core.populateUFieldPosition(fp)
    override fun getPluralOperand(operand: PluralRules.Operand?): Double = core.getPluralOperand(operand?.let { DecimalQuantityCore.Operand.entries[it.ordinal] })
    override fun getStandardPlural(rules: PluralRules?): StandardPlural = if (rules == null) StandardPlural.OTHER else StandardPlural.orOtherFromString(rules.select(this))
    override fun copyFrom(other: DecimalQuantity?) = core.copyFrom((other as DecimalQuantity_DualStorageBCD).core)
    open fun clear(): DecimalQuantity_AbstractBCD { core.clear(); return this }
    open fun setToInt(value: Int) = core.setToDecimal(UnitDecimal.valueOf(value.toLong()))
    open fun setToLong(value: Long) = core.setToDecimal(UnitDecimal.valueOf(value))
    open fun setToDouble(value: Double) = core.setToDouble(value)
    open fun toFractionLong(includeTrailingZeros: Boolean): Long = core.toFractionLong(includeTrailingZeros)
    open fun fitsInLong(): Boolean = core.fitsInLong()
    open fun truncate() = core.truncate()
    open fun appendDigit(value: Byte, leadingZeros: Int, appendAsInteger: Boolean) = core.appendDigit(value, leadingZeros, appendAsInteger)
    open fun toPlainString(result: StringBuilder?) { val rendered = core.toPlainString(); result!!.append(rendered) }
    open fun toScientificString(): String = core.toScientificString()
    open fun toScientificString(result: StringBuilder?) { val rendered = core.toScientificString(); result!!.append(rendered) }
    override fun equals(other: Any?): Boolean = this === other || other is DecimalQuantity_AbstractBCD && core == other.core
    override fun hashCode(): Int = core.hashCode()
}
