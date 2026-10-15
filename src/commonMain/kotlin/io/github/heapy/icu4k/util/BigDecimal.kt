// SPDX-License-Identifier: Unicode-3.0
// Java Number's concrete byteValue/shortValue are mapped to abstract Kotlin built-ins during actualization.
@file:Suppress("EXPECT_ACTUAL_IR_INCOMPATIBILITY")
package io.github.heapy.icu4k.util

/** Standard decimal boundary used by the number APIs; JVM aliases java.math.BigDecimal. */
expect open class BigDecimal : Number, Comparable<BigDecimal> {
    constructor(value: String?)
    constructor(value: String?, context: MathContext?)
    constructor(value: Int)
    constructor(value: Long)
    constructor(value: Double)
    constructor(value: Double, context: MathContext?)
    open fun scale(): Int
    open fun precision(): Int
    open fun signum(): Int
    open fun add(other: BigDecimal?): BigDecimal
    open fun subtract(other: BigDecimal?): BigDecimal
    open fun multiply(other: BigDecimal?): BigDecimal
    open fun divide(other: BigDecimal?): BigDecimal
    open fun divide(other: BigDecimal?, context: MathContext?): BigDecimal
    open fun divide(other: BigDecimal?, scale: Int, mode: RoundingMode?): BigDecimal
    open fun divide(other: BigDecimal?, scale: Int, mode: Int): BigDecimal
    open fun divide(other: BigDecimal?, mode: RoundingMode?): BigDecimal
    open fun divide(other: BigDecimal?, mode: Int): BigDecimal
    open fun round(context: MathContext?): BigDecimal
    open fun setScale(scale: Int): BigDecimal
    open fun setScale(scale: Int, mode: RoundingMode?): BigDecimal
    open fun setScale(scale: Int, mode: Int): BigDecimal
    open fun scaleByPowerOfTen(n: Int): BigDecimal
    open fun movePointLeft(n: Int): BigDecimal
    open fun movePointRight(n: Int): BigDecimal
    open fun stripTrailingZeros(): BigDecimal
    open fun negate(): BigDecimal
    open fun abs(): BigDecimal
    open fun plus(): BigDecimal
    open fun plus(context: MathContext?): BigDecimal
    open fun min(other: BigDecimal?): BigDecimal
    open fun max(other: BigDecimal?): BigDecimal
    open fun ulp(): BigDecimal
    open fun toPlainString(): String
    override fun compareTo(other: BigDecimal): Int
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
    override fun toDouble(): Double
    override fun toFloat(): Float
    override fun toLong(): Long
    override fun toInt(): Int
    override fun toShort(): Short
    override fun toByte(): Byte
}
