// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import kotlin.math.floor
import kotlin.math.pow

/** Numeric model from PluralRules.FixedDecimal, pending its public PluralRules owner. */
internal class FixedDecimalCore(n: Double, private val visible: Int, private val fraction: Long, e: Int, c: Int) : Number(), Comparable<FixedDecimalCore> {
    private val negative = n < 0
    private val source = if (negative) -n else n
    private val integer = if (n > MAX) MAX else source.toLong()
    private val exponent = if (e == 0) c else e
    private val hasInteger = source == integer.toDouble()
    private val withoutZeros: Long
    private val visibleWithoutZeros: Int
    private val baseFactor = powerOfTen(visible).toInt()
    init {
        if (fraction == 0L) { withoutZeros = 0; visibleWithoutZeros = 0 }
        else {
            var digits = fraction; var count = visible
            while (digits % 10 == 0L) { digits /= 10; count-- }
            withoutZeros = digits; visibleWithoutZeros = count
        }
    }
    constructor(n: Double, v: Int, f: Long, e: Int) : this(n, v, f, e, e)
    constructor(n: Double, v: Int, f: Long) : this(n, v, f, 0)
    constructor(n: Double, v: Int) : this(n, v, fractional(n, v))
    constructor(n: Double) : this(n, decimals(n))
    constructor(n: Long) : this(n.toDouble(), 0)
    fun getSource(): Double = source
    fun getVisibleDecimalDigitCount(): Int = visible
    fun getVisibleDecimalDigitCountWithoutTrailingZeros(): Int = visibleWithoutZeros
    fun getDecimalDigits(): Long = fraction
    fun getDecimalDigitsWithoutTrailingZeros(): Long = withoutZeros
    fun getIntegerValue(): Long = integer
    fun isHasIntegerValue(): Boolean = hasInteger
    fun hasIntegerValue(): Boolean = hasInteger
    fun isNegative(): Boolean = negative
    fun getBaseFactor(): Int = baseFactor
    fun isNaN(): Boolean = source.isNaN()
    fun isInfinite(): Boolean = source.isInfinite()
    fun getPluralOperand(operand: DecimalQuantityCore.Operand?): Double = when (operand!!) {
        DecimalQuantityCore.Operand.n -> if (exponent == 0) source else source * powerOfTen(exponent)
        DecimalQuantityCore.Operand.i -> toInt().toDouble()
        DecimalQuantityCore.Operand.f -> fraction.toDouble()
        DecimalQuantityCore.Operand.t -> withoutZeros.toDouble()
        DecimalQuantityCore.Operand.v -> visible.toDouble()
        DecimalQuantityCore.Operand.w -> visibleWithoutZeros.toDouble()
        DecimalQuantityCore.Operand.e, DecimalQuantityCore.Operand.c -> exponent.toDouble()
        else -> toDouble()
    }
    override fun compareTo(other: FixedDecimalCore): Int {
        if (exponent != other.exponent) return if (toDouble() < other.toDouble()) -1 else 1
        if (integer != other.integer) return if (integer < other.integer) -1 else 1
        if (source != other.source) return if (source < other.source) -1 else 1
        if (visible != other.visible) return if (visible < other.visible) -1 else 1
        val difference = fraction - other.fraction
        return if (difference == 0L) 0 else if (difference < 0) -1 else 1
    }
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is FixedDecimalCore -> false
        else -> source == other.source && visible == other.visible && fraction == other.fraction && exponent == other.exponent
    }
    override fun hashCode(): Int = (fraction + 37 * (visible + (37 * source).toInt())).toInt()
    override fun toString(): String = fixedDecimalPlainFormat(source, visible) + if (exponent == 0) "" else "e$exponent"
    override fun toInt(): Int = toLong().toInt()
    override fun toLong(): Long = if (exponent == 0) integer else (powerOfTen(exponent) * integer).toLong()
    override fun toFloat(): Float = (source * powerOfTen(exponent)).toFloat()
    override fun toDouble(): Double = (if (negative) -source else source) * powerOfTen(exponent)
    override fun toByte(): Byte = toInt().toByte()
    override fun toShort(): Short = toInt().toShort()
    // Number is Serializable on the JVM, but the source explicitly rejects serialization.
    private fun writeReplace(): Any = fixedDecimalSerializationFailure()
    private fun readResolve(): Any = fixedDecimalSerializationFailure()
    companion object {
        private const val MAX = 1000000000000000000L
        private fun powerOfTen(exponent: Int): Double = 10.0.pow(exponent.toDouble())
        fun createWithExponent(n: Double, v: Int, e: Int): FixedDecimalCore = FixedDecimalCore(n, v, fractional(n, v), e)
        private fun fractional(input: Double, v: Int): Long {
            if (v == 0) return 0
            val n = if (input < 0) -input else input
            val base = powerOfTen(v).toInt()
            return (roundPositiveDoubleToLong(n * base) % base).toInt().toLong()
        }
        fun decimals(input: Double): Int {
            if (input.isInfinite() || input.isNaN()) return 0
            val n = if (input < 0) -input else input
            if (n == floor(n)) return 0
            if (n < 1000000000L) {
                val fraction = (n * 1000000).toLong() % 1000000
                var mask = 10; var digits = 6
                while (digits > 0) { if (fraction % mask != 0L) return digits; mask *= 10; digits-- }
                return 0
            }
            val text = fixedDecimalScientificFormat(n)
            val marker = text.lastIndexOf('e')
            val exponent = text.substring(marker + 1).toInt()
            var digits = marker - 2 - exponent
            if (digits < 0) return 0
            var i = marker - 1
            while (digits > 0 && text[i--] == '0') digits--
            return digits
        }
    }
}
internal expect fun fixedDecimalPlainFormat(value: Double, fraction: Int): String
internal expect fun fixedDecimalScientificFormat(value: Double): String

internal expect fun fixedDecimalSerializationFailure(): Nothing
