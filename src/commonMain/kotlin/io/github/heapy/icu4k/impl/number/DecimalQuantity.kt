// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.text.FormatFieldPosition
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.MathContext

interface DecimalQuantity : PluralRules.IFixedDecimal {
    fun setMinInteger(minInt: Int)
    fun setMinFraction(minFrac: Int)
    fun applyMaxInteger(maxInt: Int)
    fun roundToIncrement(roundingInterval: BigDecimal?, mathContext: MathContext?)
    fun roundToNickel(magnitude: Int, mathContext: MathContext?)
    fun roundToMagnitude(roundingMagnitude: Int, mathContext: MathContext?)
    fun roundToInfinity()
    fun multiplyBy(multiplicand: BigDecimal?)
    fun negate()
    fun adjustMagnitude(delta: Int)
    fun getMagnitude(): Int
    fun getExponent(): Int
    fun adjustExponent(delta: Int)
    fun resetExponent()
    fun isZeroish(): Boolean
    fun isNegative(): Boolean
    fun signum(): Modifier.Signum
    fun toDouble(): Double
    fun toBigDecimal(): BigDecimal
    fun toLong(truncateIfOverflow: Boolean): Long
    fun setToBigDecimal(input: BigDecimal?)
    fun maxRepresentableDigits(): Int
    fun getStandardPlural(rules: PluralRules?): StandardPlural
    fun getDigit(magnitude: Int): Byte
    fun getUpperDisplayMagnitude(): Int
    fun getLowerDisplayMagnitude(): Int
    fun toPlainString(): String
    fun toExponentString(): String
    fun createCopy(): DecimalQuantity
    fun copyFrom(other: DecimalQuantity?)
    fun getPositionFingerprint(): Long
    fun populateUFieldPosition(fp: FormatFieldPosition?)
}
