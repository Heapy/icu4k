// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.UnitDecimal

/** Storage engine extracted from DecimalQuantity_DualStorageBCD, pending the full quantity pipeline. */
internal class DecimalBcdStorage {
    private var bytes: ByteArray? = null
    private var packed = 0L
    var usingBytes = false
        private set
    var scale = 0
    var precision = 0
    var isApproximate = false
    var origDouble = 0.0
    var origDelta = 0
    var exponent = 0
    fun getDigitPos(position: Int): Byte = if (usingBytes) {
        if (position < 0 || position >= precision) 0 else bytes!![position]
    } else {
        if (position < 0 || position >= 16) 0 else ((packed ushr (position * 4)) and 15).toByte()
    }
    fun setDigitPos(position: Int, value: Byte) {
        assertDecimalBcd(position >= 0)
        if (usingBytes) { ensureCapacity(position + 1); bytes!![position] = value }
        else if (position >= 16) { switchStorage(); ensureCapacity(position + 1); bytes!![position] = value }
        else {
            val shift = position * 4
            packed = (packed and (15L shl shift).inv()) or (value.toLong() shl shift)
        }
    }
    fun shiftLeft(count: Int) {
        if (!usingBytes && precision + count >= 16) switchStorage()
        if (usingBytes) {
            ensureCapacity(precision + count)
            bytes!!.copyInto(bytes!!, count, 0, precision)
            bytes!!.fill(0, 0, count)
        } else packed = packed shl (count * 4)
        scale -= count; precision += count
    }
    fun shiftRight(count: Int) {
        if (usingBytes) {
            var i = 0
            while (i < precision - count) { bytes!![i] = bytes!![i + count]; i++ }
            while (i < precision) { bytes!![i] = 0; i++ }
        } else packed = packed ushr (count * 4)
        scale += count; precision -= count
    }
    fun popFromLeft(count: Int) {
        assertDecimalBcd(count <= precision)
        if (usingBytes) for (i in precision - 1 downTo precision - count) bytes!![i] = 0
        else packed = packed and ((1L shl ((precision - count) * 4)) - 1)
        precision -= count
    }
    fun setBcdToZero() {
        if (usingBytes) { bytes = null; usingBytes = false }
        packed = 0L; scale = 0; precision = 0; isApproximate = false
        origDouble = 0.0; origDelta = 0; exponent = 0
    }
    fun readIntToBcd(value: Int) {
        assertDecimalBcd(value != 0)
        var n = value; var result = 0L; var i = 16
        while (n != 0) { result = (result ushr 4) + ((n.toLong() % 10) shl 60); n /= 10; i-- }
        assertDecimalBcd(!usingBytes)
        packed = result ushr (i * 4); scale = 0; precision = 16 - i
    }
    fun readLongToBcd(value: Long) {
        assertDecimalBcd(value != 0L)
        var n = value
        if (n >= 10000000000000000L) {
            ensureCapacity(); var i = 0
            while (n != 0L) { bytes!![i++] = (n % 10).toByte(); n /= 10 }
            scale = 0; precision = i
        } else {
            var result = 0L; var i = 16
            while (n != 0L) { result = (result ushr 4) + ((n % 10) shl 60); n /= 10; i-- }
            assertDecimalBcd(i >= 0 && !usingBytes)
            packed = result ushr (i * 4); scale = 0; precision = 16 - i
        }
    }
    /** Positive integer coefficient boundary replacing java.math.BigInteger. */
    fun readBigIntegerToBcd(coefficient: String) {
        val digits = coefficient.trimStart('0')
        assertDecimalBcd(digits.isNotEmpty())
        ensureCapacity()
        for (i in digits.indices) { ensureCapacity(i + 1); bytes!![i] = (digits[digits.lastIndex - i] - '0').toByte() }
        scale = 0; precision = digits.length
    }
    fun toDecimal(negative: Boolean): UnitDecimal {
        val result = if (usingBytes) UnitDecimal(toNumberString()) else {
            var value = 0L
            for (shift in precision - 1 downTo 0) value = value * 10 + getDigitPos(shift)
            // Retain the source's int addition, branch boundary and byte-mode exponent behavior.
            val newScale = scale + exponent
            if (newScale == Int.MIN_VALUE) UnitDecimal.ZERO else UnitDecimal("${value}E$newScale")
        }
        return if (negative) result.negate() else result
    }
    fun compact() {
        if (usingBytes) {
            var delta = 0
            while (delta < precision && bytes!![delta] == 0.toByte()) delta++
            if (delta == precision) { setBcdToZero(); return }
            shiftRight(delta)
            var leading = precision - 1
            while (leading >= 0 && bytes!![leading] == 0.toByte()) leading--
            precision = leading + 1
            if (precision <= 16) switchStorage()
        } else {
            if (packed == 0L) { setBcdToZero(); return }
            val delta = packed.countTrailingZeroBits() / 4
            packed = packed ushr (delta * 4); scale += delta
            precision = 16 - packed.countLeadingZeroBits() / 4
        }
    }
    private fun ensureCapacity(capacity: Int = 40) {
        if (capacity == 0) return
        val previous = if (usingBytes) bytes!!.size else 0
        if (!usingBytes) bytes = ByteArray(capacity)
        else if (previous < capacity) bytes = bytes!!.copyOf(capacity * 2)
        usingBytes = true
    }
    private fun switchStorage() {
        if (usingBytes) {
            packed = 0L
            for (i in precision - 1 downTo 0) packed = (packed shl 4) or bytes!![i].toLong()
            bytes = null; usingBytes = false
        } else {
            ensureCapacity()
            for (i in 0 until precision) { bytes!![i] = (packed and 15).toByte(); packed = packed ushr 4 }
        }
    }
    fun copyBcdFrom(other: DecimalBcdStorage) {
        setBcdToZero()
        if (other.usingBytes) { ensureCapacity(other.precision); other.bytes!!.copyInto(bytes!!, 0, 0, other.precision) }
        else packed = other.packed
    }
    fun copy(): DecimalBcdStorage = DecimalBcdStorage().also {
        it.copyBcdFrom(this); it.scale = scale; it.precision = precision
        it.isApproximate = isApproximate; it.origDouble = origDouble; it.origDelta = origDelta; it.exponent = exponent
    }
    fun checkHealth(): String? {
        if (usingBytes) {
            if (packed != 0L) return "Value in bcdLong but we are in byte mode"
            if (precision == 0) return "Zero precision but we are in byte mode"
            if (precision > bytes!!.size) return "Precision exceeds length of byte array"
            if (getDigitPos(precision - 1) == 0.toByte()) return "Most significant digit is zero in byte mode"
            if (getDigitPos(0) == 0.toByte()) return "Least significant digit is zero in long mode"
            for (i in 0 until precision) {
                if (getDigitPos(i) >= 10) return "Digit exceeding 10 in byte array"
                if (getDigitPos(i) < 0) return "Digit below 0 in byte array"
            }
            for (i in precision until bytes!!.size) if (getDigitPos(i) != 0.toByte()) return "Nonzero digits outside of range in byte array"
        } else {
            bytes?.let { for (digit in it) if (digit != 0.toByte()) return "Nonzero digits in byte array but we are in long mode" }
            if (precision == 0 && packed != 0L) return "Value in bcdLong even though precision is zero"
            if (precision > 16) return "Precision exceeds length of long"
            if (precision != 0 && getDigitPos(precision - 1) == 0.toByte()) return "Most significant digit is zero in long mode"
            if (precision != 0 && getDigitPos(0) == 0.toByte()) return "Least significant digit is zero in long mode"
            for (i in 0 until precision) {
                if (getDigitPos(i) >= 10) return "Digit exceeding 10 in long"
                if (getDigitPos(i) < 0) return "Digit below 0 in long (?!)"
            }
            for (i in precision until 16) if (getDigitPos(i) != 0.toByte()) return "Nonzero digits outside of range in long"
        }
        return null
    }
    fun toNumberString(): String = buildString {
        if (usingBytes) {
            if (precision == 0) append('0')
            for (i in precision - 1 downTo 0) append(bytes!![i])
        } else append(packed.toULong().toString(16))
        append('E'); append(scale)
    }
}
internal expect fun assertDecimalBcd(condition: Boolean)
