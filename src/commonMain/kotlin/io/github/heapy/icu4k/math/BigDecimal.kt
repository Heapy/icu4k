// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.math

import io.github.heapy.icu4k.util.BigDecimal as StandardBigDecimal
import io.github.heapy.icu4k.util.BigInteger
import io.github.heapy.icu4k.util.ICUSerializable
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** ICU's arbitrary-precision decimal, with representation-sensitive equality and ICU arithmetic contexts.
 * This is distinct from [StandardBigDecimal], the standard decimal interchange type.
 */
class BigDecimal private constructor(value: IcuDecimalValue) : Number(), Comparable<BigDecimal>, ICUSerializable {
    // Preserve the source's Java serialization fields and mantissa sharing. Working states are ephemeral.
    private var ind: Byte = value.ind
    private var form: Byte = value.form
    private var mant: ByteArray? = value.rawMant
    private var exp: Int = value.exp

    constructor(value: String?) : this(IcuDecimalValue.parse(value))
    constructor(value: CharArray?) : this(IcuDecimalValue.parse(value, 0, value!!.size))
    constructor(value: CharArray?, offset: Int, length: Int) : this(IcuDecimalValue.parse(value, offset, length))
    constructor(value: Int) : this(IcuDecimalValue.fromInt(value))
    constructor(value: Long) : this(IcuDecimalValue.fromLong(value))
    constructor(value: Double) : this(IcuDecimalValue.fromDouble(value))
    constructor(value: StandardBigDecimal?) : this(IcuDecimalValue.fromStandardBigDecimal(value))
    constructor(value: BigInteger?) : this(IcuDecimalValue.parse(value!!.toString(10)))
    constructor(value: BigInteger?, scale: Int) : this(IcuDecimalValue.fromIntegerText(value!!.toString(10), scale))

    private fun state(): IcuDecimalValue = IcuDecimalValue().also {
        it.ind = ind; it.form = form; it.rawMant = mant; it.exp = exp
    }
    private fun unary(operation: (IcuDecimalValue) -> IcuDecimalValue): BigDecimal {
        val left = state()
        return wrap(operation(left), left, this, null, null)
    }
    private fun binary(rhs: BigDecimal?, operation: (IcuDecimalValue, IcuDecimalValue?) -> IcuDecimalValue): BigDecimal {
        val left = state()
        val right = if (rhs === this) left else rhs?.state()
        return wrap(operation(left, right), left, this, right, rhs)
    }
    fun abs(): BigDecimal = unary { it.abs() }
    fun abs(set: MathContext?): BigDecimal = unary { it.abs(set) }
    fun negate(): BigDecimal = unary { it.negate() }
    fun negate(set: MathContext?): BigDecimal = unary { it.negate(set) }
    fun plus(): BigDecimal = unary { it.plus() }
    fun plus(set: MathContext?): BigDecimal = unary { it.plus(set) }
    fun add(rhs: BigDecimal?): BigDecimal = binary(rhs) { l, r -> l.add(r) }
    fun add(rhs: BigDecimal?, set: MathContext?): BigDecimal = binary(rhs) { l, r -> l.add(r, set) }
    fun subtract(rhs: BigDecimal?): BigDecimal = binary(rhs) { l, r -> l.subtract(r) }
    fun subtract(rhs: BigDecimal?, set: MathContext?): BigDecimal = binary(rhs) { l, r -> l.subtract(r, set) }
    fun multiply(rhs: BigDecimal?): BigDecimal = binary(rhs) { l, r -> l.multiply(r) }
    fun multiply(rhs: BigDecimal?, set: MathContext?): BigDecimal = binary(rhs) { l, r -> l.multiply(r, set) }
    fun divide(rhs: BigDecimal?): BigDecimal = binary(rhs) { l, r -> l.divide(r) }
    fun divide(rhs: BigDecimal?, set: MathContext?): BigDecimal = binary(rhs) { l, r -> l.divide(r, set) }
    fun divideInteger(rhs: BigDecimal?): BigDecimal = binary(rhs) { l, r -> l.divideInteger(r) }
    fun divideInteger(rhs: BigDecimal?, set: MathContext?): BigDecimal = binary(rhs) { l, r -> l.divideInteger(r, set) }
    fun remainder(rhs: BigDecimal?): BigDecimal = binary(rhs) { l, r -> l.remainder(r) }
    fun remainder(rhs: BigDecimal?, set: MathContext?): BigDecimal = binary(rhs) { l, r -> l.remainder(r, set) }
    fun pow(rhs: BigDecimal?): BigDecimal = binary(rhs) { l, r -> l.pow(r) }
    fun pow(rhs: BigDecimal?, set: MathContext?): BigDecimal = binary(rhs) { l, r -> l.pow(r, set) }
    fun max(rhs: BigDecimal?): BigDecimal = binary(rhs) { l, r -> l.max(r) }
    fun max(rhs: BigDecimal?, set: MathContext?): BigDecimal = binary(rhs) { l, r -> l.max(r, set) }
    fun min(rhs: BigDecimal?): BigDecimal = binary(rhs) { l, r -> l.min(r) }
    fun min(rhs: BigDecimal?, set: MathContext?): BigDecimal = binary(rhs) { l, r -> l.min(r, set) }
    fun divide(rhs: BigDecimal?, round: Int): BigDecimal = binary(rhs) { l, r -> l.divide(r, round) }
    fun divide(rhs: BigDecimal?, scale: Int, round: Int): BigDecimal = binary(rhs) { l, r -> l.divide(r, scale, round) }
    override fun compareTo(other: BigDecimal): Int = state().compareTo(other.state())
    fun compareTo(rhs: BigDecimal?, set: MathContext?): Int = state().compareTo(rhs?.state(), set)
    fun movePointLeft(n: Int): BigDecimal = unary { it.movePointLeft(n) }
    fun movePointRight(n: Int): BigDecimal = unary { it.movePointRight(n) }
    fun setScale(scale: Int): BigDecimal = unary { it.setScale(scale) }
    fun setScale(scale: Int, round: Int): BigDecimal = unary { it.setScale(scale, round) }
    fun scale(): Int = if (exp >= 0) 0 else -exp
    fun signum(): Int = ind.toInt()
    fun byteValueExact(): Byte = state().byteValueExact()
    fun shortValueExact(): Short = state().shortValueExact()
    fun intValueExact(): Int = state().intValueExact()
    fun longValueExact(): Long = state().longValueExact()
    fun toBigDecimal(): StandardBigDecimal = state().toStandardBigDecimal()
    fun toBigInteger(): BigInteger = BigInteger(state().integerText())
    fun toBigIntegerExact(): BigInteger = BigInteger(state().exactIntegerText())
    fun unscaledValue(): BigInteger = BigInteger(state().unscaledText())
    fun toCharArray(): CharArray = state().layout()
    fun format(before: Int, after: Int): String = state().format(before, after)
    fun format(before: Int, after: Int, explaces: Int, exdigits: Int, exformint: Int, exround: Int): String =
        state().format(before, after, explaces, exdigits, exformint, exround)
    override fun toInt(): Int = state().lowIntValue()
    override fun toLong(): Long = state().lowLongValue()
    override fun toByte(): Byte = toInt().toByte()
    override fun toShort(): Short = toInt().toShort()
    override fun toDouble(): Double = state().doubleValue()
    override fun toFloat(): Float = state().floatValue()
    override fun equals(other: Any?): Boolean = other is BigDecimal && state().sameRepresentation(other.state())
    override fun hashCode(): Int = state().representationHashCode()
    override fun toString(): String = state().toString()

    companion object {
        const val ROUND_CEILING = MathContext.ROUND_CEILING
        const val ROUND_DOWN = MathContext.ROUND_DOWN
        const val ROUND_FLOOR = MathContext.ROUND_FLOOR
        const val ROUND_HALF_DOWN = MathContext.ROUND_HALF_DOWN
        const val ROUND_HALF_EVEN = MathContext.ROUND_HALF_EVEN
        const val ROUND_HALF_UP = MathContext.ROUND_HALF_UP
        const val ROUND_UNNECESSARY = MathContext.ROUND_UNNECESSARY
        const val ROUND_UP = MathContext.ROUND_UP
        @JvmField val ZERO = BigDecimal(IcuDecimalValue.ZERO_VALUE)
        @JvmField val ONE = BigDecimal(IcuDecimalValue.ONE_VALUE)
        @JvmField val TEN = BigDecimal(IcuDecimalValue.TEN_VALUE)
        @JvmStatic fun valueOf(value: Double): BigDecimal = BigDecimal(IcuDecimalValue.valueOf(value))
        @JvmStatic fun valueOf(value: Long): BigDecimal = valueOf(value, 0)
        @JvmStatic fun valueOf(value: Long, scale: Int): BigDecimal = wrap(IcuDecimalValue.valueOf(value, scale), null, null, null, null)
        private const val serialVersionUID: Long = 8245355804974198832L

        private fun wrap(value: IcuDecimalValue, left: IcuDecimalValue?, lhs: BigDecimal?, right: IcuDecimalValue?, rhs: BigDecimal?): BigDecimal = when {
            value === left -> lhs!!
            value === right -> rhs!!
            value === IcuDecimalValue.ZERO_VALUE -> ZERO
            value === IcuDecimalValue.ONE_VALUE -> ONE
            value === IcuDecimalValue.TEN_VALUE -> TEN
            else -> BigDecimal(value)
        }
    }
}
