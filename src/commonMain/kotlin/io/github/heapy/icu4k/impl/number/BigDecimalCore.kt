// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.UnitDecimal
import io.github.heapy.icu4k.util.MathContext
import io.github.heapy.icu4k.util.RoundingMode

internal class BigDecimalCore(val value: UnitDecimal) : Comparable<BigDecimalCore> {
    constructor(text: String?) : this(UnitDecimal(text!!))
    constructor(value: Long) : this(UnitDecimal.valueOf(value))
    constructor(value: Double) : this(CommonDoubleToDecimal.exact(value))
    fun round(context: MathContext?): BigDecimalCore = BigDecimalCore(value.round(context!!.getPrecision(), mode(context.getRoundingMode())))
    fun add(other: BigDecimalCore): BigDecimalCore = BigDecimalCore(value.add(other.value))
    fun subtract(other: BigDecimalCore): BigDecimalCore = BigDecimalCore(value.subtract(other.value))
    fun multiply(other: BigDecimalCore): BigDecimalCore = BigDecimalCore(value.multiply(other.value))
    fun divide(other: BigDecimalCore): BigDecimalCore = BigDecimalCore(value.divideExact(other.value))
    fun divide(other: BigDecimalCore, context: MathContext?): BigDecimalCore = BigDecimalCore(value.divide(other.value, context!!.getPrecision(), mode(context.getRoundingMode())))
    fun divide(other: BigDecimalCore, scale: Int, rounding: RoundingMode?): BigDecimalCore = BigDecimalCore(value.divideToScale(other.value, scale, mode(rounding)))
    fun setScale(scale: Int, rounding: RoundingMode?): BigDecimalCore = BigDecimalCore(value.setScale(scale, mode(rounding)))
    fun scaleByPowerOfTen(n: Int): BigDecimalCore = BigDecimalCore(value.scaleByPowerOfTen(n))
    fun movePointLeft(n: Int): BigDecimalCore = BigDecimalCore(value.movePointLeft(n))
    fun movePointRight(n: Int): BigDecimalCore = BigDecimalCore(value.movePointRight(n))
    fun stripTrailingZeros(): BigDecimalCore = BigDecimalCore(value.stripTrailingZeros())
    fun negate(): BigDecimalCore = BigDecimalCore(value.negate())
    fun abs(): BigDecimalCore = BigDecimalCore(value.abs())
    fun toPlainString(): String = value.toPlainString()
    fun toDouble(): Double = value.toString().toDouble()
    fun toFloat(): Float = value.toString().toFloat()
    fun toLong(): Long = value.longValue()
    fun toInt(): Int = value.intValue()
    override fun compareTo(other: BigDecimalCore): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is BigDecimalCore && value == other.value
    override fun hashCode(): Int = value.bigDecimalHashCode()
    override fun toString(): String = value.toString()
    companion object {
        fun unscaled(value: Long, scale: Int): BigDecimalCore = BigDecimalCore("${value}E${-scale.toLong()}")
        fun shortest(value: Double): BigDecimalCore = BigDecimalCore(CommonDoubleToDecimal.format(value))
        private fun mode(mode: RoundingMode?): UnitDecimal.Rounding = UnitDecimal.Rounding.entries[mode!!.ordinal]
    }
}
