// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.number.BigDecimalCore

actual open class BigDecimal internal constructor(internal val core: BigDecimalCore) : Number(), Comparable<BigDecimal> {
    actual constructor(value: String?) : this(BigDecimalCore(value))
    actual constructor(value: String?, context: MathContext?) : this(BigDecimalCore(value).round(context))
    actual constructor(value: Int) : this(BigDecimalCore(value.toLong()))
    actual constructor(value: Long) : this(BigDecimalCore(value))
    actual constructor(value: Double) : this(BigDecimalCore(value))
    actual constructor(value: Double, context: MathContext?) : this(BigDecimalCore(value).round(context))
    private fun wrap(result: BigDecimalCore): BigDecimal = if (result.value === core.value) this else BigDecimal(result)
    actual open fun scale(): Int = core.value.scale
    actual open fun precision(): Int = core.value.precision
    actual open fun signum(): Int = core.value.sign
    actual open fun add(other: BigDecimal?): BigDecimal = wrap(core.add(other!!.core))
    actual open fun subtract(other: BigDecimal?): BigDecimal = wrap(core.subtract(other!!.core))
    actual open fun multiply(other: BigDecimal?): BigDecimal = wrap(core.multiply(other!!.core))
    actual open fun divide(other: BigDecimal?): BigDecimal = wrap(core.divide(other!!.core))
    actual open fun divide(other: BigDecimal?, context: MathContext?): BigDecimal = wrap(core.divide(other!!.core, context))
    actual open fun divide(other: BigDecimal?, scale: Int, mode: RoundingMode?): BigDecimal = wrap(core.divide(other!!.core, scale, mode))
    actual open fun divide(other: BigDecimal?, scale: Int, mode: Int): BigDecimal {
        val rounding = RoundingModes.valueOf(mode)
        return divide(other, scale, rounding)
    }
    actual open fun divide(other: BigDecimal?, mode: RoundingMode?): BigDecimal = divide(other, scale(), mode)
    actual open fun divide(other: BigDecimal?, mode: Int): BigDecimal = divide(other, scale(), mode)
    actual open fun round(context: MathContext?): BigDecimal = wrap(core.round(context))
    actual open fun setScale(scale: Int): BigDecimal = setScale(scale, RoundingMode.UNNECESSARY)
    actual open fun setScale(scale: Int, mode: RoundingMode?): BigDecimal = wrap(core.setScale(scale, mode))
    actual open fun setScale(scale: Int, mode: Int): BigDecimal = setScale(scale, RoundingModes.valueOf(mode))
    actual open fun scaleByPowerOfTen(n: Int): BigDecimal = wrap(core.scaleByPowerOfTen(n))
    actual open fun movePointLeft(n: Int): BigDecimal = wrap(core.movePointLeft(n))
    actual open fun movePointRight(n: Int): BigDecimal = wrap(core.movePointRight(n))
    actual open fun stripTrailingZeros(): BigDecimal = if (core.value.sign == 0) BigDecimals.ZERO else wrap(core.stripTrailingZeros())
    actual open fun negate(): BigDecimal = wrap(core.negate())
    actual open fun abs(): BigDecimal = wrap(core.abs())
    actual open fun plus(): BigDecimal = this
    actual open fun plus(context: MathContext?): BigDecimal = round(context)
    actual open fun min(other: BigDecimal?): BigDecimal = if (compareTo(other!!) <= 0) this else other
    actual open fun max(other: BigDecimal?): BigDecimal = if (compareTo(other!!) >= 0) this else other
    actual open fun ulp(): BigDecimal = BigDecimals.valueOf(1, scale())
    actual open fun toPlainString(): String = core.toPlainString()
    actual override fun compareTo(other: BigDecimal): Int = core.compareTo(other.core)
    actual override fun equals(other: Any?): Boolean = other is BigDecimal && core == other.core
    actual override fun hashCode(): Int = core.hashCode()
    actual override fun toString(): String = core.toString()
    actual override fun toDouble(): Double = core.toDouble()
    actual override fun toFloat(): Float = core.toFloat()
    actual override fun toLong(): Long = core.toLong()
    actual override fun toInt(): Int = core.toInt()
    actual override fun toShort(): Short = toInt().toShort()
    actual override fun toByte(): Byte = toInt().toByte()
}

private val decimalZeroCache = Array(16) { BigDecimal(BigDecimalCore.unscaled(0, it)) }
private val decimalIntegerCache = Array(11) { if (it == 0) decimalZeroCache[0] else BigDecimal(BigDecimalCore(it.toLong())) }
internal actual fun decimalValueOf(value: Long, scale: Int): BigDecimal = when {
    scale == 0 && value in 0L..10L -> decimalIntegerCache[value.toInt()]
    value == 0L && scale in 0..15 -> decimalZeroCache[scale]
    else -> BigDecimal(BigDecimalCore.unscaled(value, scale))
}
internal actual fun decimalValueOfDouble(value: Double): BigDecimal = BigDecimal(BigDecimalCore.shortest(value))

internal actual fun BigDecimal.decimalValue(): io.github.heapy.icu4k.impl.units.UnitDecimal = core.value
internal actual fun decimalFromValue(value: io.github.heapy.icu4k.impl.units.UnitDecimal): BigDecimal = BigDecimal(BigDecimalCore(value))
