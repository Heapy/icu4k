// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.UnitDecimal
import io.github.heapy.icu4k.impl.number.Modifier.Signum
import io.github.heapy.icu4k.util.MathContext

/** Numeric and display state from DecimalQuantity_AbstractBCD; public plural-rule and number adapters are separate. */
internal class DecimalQuantityCore {
    private var bcd = DecimalBcdStorage()
    private var flags = 0
    private var leftRequired = 0
    private var rightRequired = 0
    var explicitlyExactDouble = false
        private set
    constructor()
    constructor(value: UnitDecimal) { setToDecimal(value) }
    constructor(value: Double) { setToDouble(value) }
    constructor(value: Long) { setToDecimal(UnitDecimal.valueOf(value)) }
    fun setToDecimal(value: UnitDecimal) {
        bcd.setBcdToZero(); flags = if (value.sign < 0) NEGATIVE else 0
        if (value.sign == 0) return
        val coefficient = value.magnitude.toLongOrNull()
        when {
            coefficient == null -> bcd.readBigIntegerToBcd(value.magnitude)
            coefficient <= Int.MAX_VALUE -> bcd.readIntToBcd(coefficient.toInt())
            else -> bcd.readLongToBcd(coefficient)
        }
        bcd.scale -= value.scale; bcd.compact()
    }
    fun setToDouble(value: Double) {
        val state = DecimalDoubleLoader.load(bcd, value)
        flags = (if (state.negative) NEGATIVE else 0) or (if (state.infinite) INFINITE else 0) or (if (state.nan) NAN else 0)
        explicitlyExactDouble = explicitlyExactDouble || state.explicitlyExact
    }
    private fun accurate() {
        val state = DecimalDoubleLoader.State(isNegative(), isInfinite(), isNaN(), explicitlyExactDouble)
        DecimalDoubleLoader.makeAccurate(bcd, state); explicitlyExactDouble = state.explicitlyExact
    }
    fun roundToInfinity() { if (bcd.isApproximate) accurate() }
    fun toDecimal(): UnitDecimal { roundToInfinity(); return bcd.toDecimal(isNegative()) }
    fun clear(): DecimalQuantityCore {
        leftRequired = 0; rightRequired = 0; flags = 0; bcd.setBcdToZero(); return this
    }
    fun copyFrom(other: DecimalQuantityCore) {
        if (other === this) { bcd.copyBcdFrom(bcd); return }
        bcd = other.bcd.copy(); flags = other.flags; leftRequired = other.leftRequired; rightRequired = other.rightRequired
        // The source does not copy or clear the diagnostic explicitExactDouble flag.
    }
    fun createCopy(): DecimalQuantityCore = DecimalQuantityCore().also { it.copyFrom(this) }
    fun setMinInteger(value: Int) { assertDecimalBcd(value >= 0); leftRequired = maxOf(value, leftRequired) }
    fun setMinFraction(value: Int) { assertDecimalBcd(value >= 0); rightRequired = -value }
    fun getPositionFingerprint(): Long = (leftRequired shl 16).toLong() xor (rightRequired.toLong() shl 32)
    fun applyMaxInteger(maximum: Int) {
        assertDecimalBcd(maximum >= 0)
        if (bcd.precision == 0) return
        if (maximum <= bcd.scale) { bcd.setBcdToZero(); return }
        val magnitude = getMagnitude()
        if (maximum <= magnitude) { bcd.popFromLeft(magnitude - maximum + 1); bcd.compact() }
    }
    fun negate() { flags = flags xor NEGATIVE }
    fun isNegative(): Boolean = flags and NEGATIVE != 0
    fun isInfinite(): Boolean = flags and INFINITE != 0
    fun isNaN(): Boolean = flags and NAN != 0
    fun isZeroish(): Boolean = bcd.precision == 0
    fun signum(): Signum = if (isZeroish() && !isInfinite()) { if (isNegative()) Signum.NEG_ZERO else Signum.POS_ZERO } else if (isNegative()) Signum.NEG else Signum.POS
    fun getMagnitude(): Int = if (bcd.precision == 0) throw ArithmeticException("Magnitude is not well-defined for zero") else bcd.scale + bcd.precision - 1
    fun adjustMagnitude(delta: Int) {
        if (bcd.precision != 0) {
            bcd.scale = addExact(bcd.scale, delta); bcd.origDelta = addExact(bcd.origDelta, delta); addExact(bcd.scale, bcd.precision)
        }
    }
    fun getExponent(): Int = bcd.exponent
    fun adjustExponent(delta: Int) { bcd.exponent += delta }
    fun resetExponent() { adjustMagnitude(bcd.exponent); bcd.exponent = 0 }
    fun isHasIntegerValue(): Boolean = bcd.scale >= 0
    fun getUpperDisplayMagnitude(): Int { assertDecimalBcd(!bcd.isApproximate); return maxOf(leftRequired, bcd.scale + bcd.precision) - 1 }
    fun getLowerDisplayMagnitude(): Int { assertDecimalBcd(!bcd.isApproximate); return minOf(rightRequired, bcd.scale) }
    fun getDigit(magnitude: Int): Byte { assertDecimalBcd(!bcd.isApproximate); return bcd.getDigitPos(magnitude - bcd.scale) }
    fun roundToMagnitude(magnitude: Int, precision: Int, mode: UnitDecimal.Rounding) = round(magnitude, precision, mode, false)
    fun roundToNickel(magnitude: Int, precision: Int, mode: UnitDecimal.Rounding) = round(magnitude, precision, mode, true)
    fun roundToMagnitude(magnitude: Int, context: MathContext?) = roundToMagnitude(magnitude, context!!.getPrecision(), UnitDecimal.Rounding.entries[context.getRoundingMode().ordinal])
    fun roundToNickel(magnitude: Int, context: MathContext?) = roundToNickel(magnitude, context!!.getPrecision(), UnitDecimal.Rounding.entries[context.getRoundingMode().ordinal])
    private fun round(magnitude: Int, precision: Int, mode: UnitDecimal.Rounding, nickel: Boolean) {
        DecimalBcdRounding.round(bcd, isNegative(), magnitude, precision, mode.ordinal, nickel, this) { accurate() }
    }
    fun roundToIncrement(increment: UnitDecimal, precision: Int, mode: UnitDecimal.Rounding) {
        assertDecimalBcd {
            val normalized = increment.stripTrailingZeros()
            normalized.precision != 1 || normalized.magnitude.toInt() * normalized.sign != 5 || normalized.magnitude.toInt() * normalized.sign != 1
        }
        val result = toDecimal().divideToScale(increment, 0, mode).multiply(increment).round(precision, mode)
        if (result.sign == 0) bcd.setBcdToZero() else setToDecimal(result)
    }
    fun multiplyBy(value: UnitDecimal) { if (!isZeroish()) setToDecimal(toDecimal().multiply(value)) }
    fun truncate() { if (bcd.scale < 0) { bcd.shiftRight(-bcd.scale); bcd.scale = 0; bcd.compact() } }
    fun appendDigit(value: Byte, leadingZeros: Int, asInteger: Boolean) {
        assertDecimalBcd(leadingZeros >= 0)
        var zeros = leadingZeros
        if (value == 0.toByte()) { if (asInteger && bcd.precision != 0) bcd.scale += zeros + 1; return }
        if (bcd.scale > 0) { zeros += bcd.scale; if (asInteger) bcd.scale = 0 }
        bcd.shiftLeft(zeros + 1); bcd.setDigitPos(0, value)
        if (asInteger) bcd.scale += zeros + 1
    }
    fun toPlainString(): String = render(true, true)
    fun toExponentString(): String = render(false, false) + if (bcd.exponent != 0) "c${bcd.exponent}" else ""
    private fun render(includeExponent: Boolean, zeroSpecial: Boolean): String = buildString {
        assertDecimalBcd(!bcd.isApproximate)
        if (isNegative()) append('-')
        if (zeroSpecial && bcd.precision == 0) { append('0'); return@buildString }
        val exponent = if (includeExponent) bcd.exponent else 0
        val upper = maxOf(bcd.scale + bcd.precision + exponent - 1, leftRequired - 1)
        val lower = minOf(bcd.scale + exponent, rightRequired)
        var p = upper
        if (p < 0) append('0')
        while (p >= 0) { append(('0'.code + bcd.getDigitPos(p - bcd.scale - exponent)).toChar()); p-- }
        if (lower < 0) append('.')
        while (p >= lower) { append(('0'.code + bcd.getDigitPos(p - bcd.scale - exponent)).toChar()); p-- }
    }
    fun toScientificString(): String = buildString {
        assertDecimalBcd(!bcd.isApproximate)
        if (isNegative()) append('-')
        if (bcd.precision == 0) { append("0E+0"); return@buildString }
        append(('0'.code + bcd.getDigitPos(bcd.precision - 1)).toChar())
        if (bcd.precision > 1) { append('.'); for (p in bcd.precision - 2 downTo 0) append(('0'.code + bcd.getDigitPos(p)).toChar()) }
        val magnitude = bcd.precision - 1 + bcd.scale + bcd.exponent
        append('E'); if (magnitude >= 0) append('+'); append(magnitude)
    }
    fun toLong(truncateIfOverflow: Boolean): Long {
        assertDecimalBcd { truncateIfOverflow || fitsInLong() }
        var result = 0L
        var upper = bcd.exponent + bcd.scale + bcd.precision - 1
        if (truncateIfOverflow) upper = minOf(upper, 17)
        for (magnitude in upper downTo 0) result = result * 10 + bcd.getDigitPos(magnitude - bcd.scale - bcd.exponent)
        return if (isNegative()) -result else result
    }
    fun toFractionLong(includeTrailingZeros: Boolean): Long {
        var result = 0L
        var magnitude = -1 - bcd.exponent
        val lower = if (includeTrailingZeros) minOf(bcd.scale, rightRequired) else bcd.scale
        while (magnitude >= lower && result.toDouble() <= 1e17) {
            result = result * 10 + bcd.getDigitPos(magnitude - bcd.scale)
            magnitude--
        }
        if (!includeTrailingZeros) while (result > 0 && result % 10 == 0L) result /= 10
        return result
    }
    fun fitsInLong(): Boolean {
        if (isInfinite() || isNaN()) return false
        if (isZeroish()) return true
        if (bcd.exponent + bcd.scale < 0) return false
        val magnitude = getMagnitude()
        if (magnitude < 18) return true
        if (magnitude > 18) return false
        for (p in 0 until bcd.precision) {
            val digit = getDigit(18 - p)
            if (digit < INT64_BCD[p]) return true
            if (digit > INT64_BCD[p]) return false
        }
        return isNegative()
    }
    fun toDouble(): Double {
        assertDecimalBcd(!bcd.isApproximate)
        if (isNaN()) return Double.NaN
        if (isInfinite()) return if (isNegative()) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        return toScientificString().toDouble()
    }
    enum class Operand { n, i, f, t, v, w, e, c, j }
    fun getPluralOperand(operand: Operand?): Double {
        assertDecimalBcd(!bcd.isApproximate)
        return when (operand!!) {
            Operand.i -> (if (isNegative()) -toLong(true) else toLong(true)).toDouble()
            Operand.f -> toFractionLong(true).toDouble()
            Operand.t -> toFractionLong(false).toDouble()
            Operand.v -> maxOf(0, -getLowerDisplayMagnitude() - bcd.exponent).toDouble()
            Operand.w -> maxOf(-bcd.scale - bcd.exponent, 0).toDouble()
            Operand.e, Operand.c -> bcd.exponent.toDouble()
            else -> kotlin.math.abs(toDouble())
        }
    }
    @Suppress("DEPRECATION")
    fun populateUFieldPosition(position: io.github.heapy.icu4k.text.FormatFieldPosition?) {
        if (position is io.github.heapy.icu4k.text.UFieldPosition) position.setFractionDigits(getPluralOperand(Operand.v).toInt(), getPluralOperand(Operand.f).toLong())
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecimalQuantityCore) return false
        if (bcd.scale != other.bcd.scale || bcd.precision != other.bcd.precision || flags != other.flags || leftRequired != other.leftRequired || rightRequired != other.rightRequired || bcd.isApproximate != other.bcd.isApproximate) return false
        if (bcd.precision == 0) return true
        if (bcd.isApproximate) return bcd.origDouble == other.bcd.origDouble && bcd.origDelta == other.bcd.origDelta
        for (m in getUpperDisplayMagnitude() downTo getLowerDisplayMagnitude()) if (getDigit(m) != other.getDigit(m)) return false
        return true
    }
    override fun hashCode(): Int {
        val result = objectHash(bcd.scale, bcd.precision, flags, leftRequired, rightRequired, if (bcd.isApproximate) 1231 else 1237)
        if (bcd.precision == 0) return result
        if (bcd.isApproximate) {
            val bits = bcd.origDouble.toBits()
            return objectHash(result, (bits xor (bits ushr 32)).toInt(), bcd.origDelta)
        }
        // The source returns from the first loop iteration: only the first displayed digit is hashed.
        for (m in getUpperDisplayMagnitude() downTo getLowerDisplayMagnitude()) return objectHash(result, getDigit(m).toInt())
        return result
    }
    fun checkHealth(): String? = bcd.checkHealth()
    override fun toString(): String = "<DecimalQuantity $leftRequired:$rightRequired ${if (bcd.usingBytes) "bytes" else "long"} ${if (isNegative()) "-" else ""}${bcd.toNumberString()}>"
    companion object {
        fun fromExponentString(number: String?): DecimalQuantityCore {
            val text = number!!
            val hasExponent = text.any { it == 'e' || it == 'E' || it == 'c' || it == 'C' }
            var exponent = 0
            val fraction = if (hasExponent) {
                var marker = text.lastIndexOf('e')
                if (marker < 0) marker = text.lastIndexOf('c')
                if (marker < 0) marker = text.lastIndexOf('E')
                if (marker < 0) marker = text.lastIndexOf('C')
                exponent = DecimalNumberParsing.int(text.substring(marker + 1))
                text.substring(0, marker)
            } else text
            val point = fraction.indexOf('.') + 1
            return DecimalQuantityCore(UnitDecimal(fraction)).also {
                it.setMinFraction(if (point == 0) 0 else fraction.length - point)
                if (hasExponent) it.adjustExponent(exponent)
            }
        }
        private val INT64_BCD = byteArrayOf(9, 2, 2, 3, 3, 7, 2, 0, 3, 6, 8, 5, 4, 7, 7, 5, 8, 0, 8)
        private fun objectHash(vararg values: Int): Int = values.fold(1) { result, value -> 31 * result + value }
        private const val NEGATIVE = 1
        private const val INFINITE = 2
        private const val NAN = 4
        private fun addExact(a: Int, b: Int): Int {
            val result = a + b
            if (((a xor result) and (b xor result)) < 0) throw ArithmeticException("integer overflow")
            return result
        }
    }
}
