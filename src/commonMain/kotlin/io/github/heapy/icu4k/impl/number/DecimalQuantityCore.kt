// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.UnitDecimal

/** Numeric and display state from DecimalQuantity_AbstractBCD; plural operands and public number adapters are separate. */
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
    enum class Signum { NEG, NEG_ZERO, POS_ZERO, POS }
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
    private fun round(magnitude: Int, precision: Int, mode: UnitDecimal.Rounding, nickel: Boolean) {
        DecimalBcdRounding.round(bcd, isNegative(), magnitude, precision, mode.ordinal, nickel, this) { accurate() }
    }
    fun roundToIncrement(increment: UnitDecimal, precision: Int, mode: UnitDecimal.Rounding) {
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
    fun checkHealth(): String? = bcd.checkHealth()
    override fun toString(): String = "<DecimalQuantity $leftRequired:$rightRequired ${if (bcd.usingBytes) "bytes" else "long"} ${if (isNegative()) "-" else ""}${bcd.toNumberString()}>"
    companion object {
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
