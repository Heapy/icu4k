// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

/** Double loading paths from DecimalQuantity_AbstractBCD. Sign/nonfinite flags are returned to the quantity owner. */
internal object DecimalDoubleLoader {
    class State(val negative: Boolean, val infinite: Boolean, val nan: Boolean, var explicitlyExact: Boolean = false)
    private val multipliers = doubleArrayOf(1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21)
    fun load(storage: DecimalBcdStorage, input: Double): State {
        storage.setBcdToZero()
        val negative = input.toRawBits() < 0
        val n = if (negative) -input else input
        val state = State(negative, n.isInfinite(), n.isNaN())
        if (state.nan || state.infinite || n == 0.0) return state
        storage.isApproximate = true; storage.origDouble = n; storage.origDelta = 0
        val exponent = ((n.toBits() and 0x7ff0000000000000L) ushr 52).toInt() - 0x3ff
        if (exponent <= 52 && n.toLong().toDouble() == n) {
            readLong(storage, n.toLong())
        } else if (exponent == -1023 || exponent == 1024) {
            makeAccurate(storage, state)
        } else {
            val fraction = ((52 - exponent) / 3.32192809488736234787031942948939017586).toInt()
            var scaled = n
            var i = fraction
            if (i >= 0) {
                while (i >= 22) { scaled *= 1e22; i -= 22 }
                scaled *= multipliers[i]
            } else {
                while (i <= -22) { scaled /= 1e22; i += 22 }
                scaled /= multipliers[-i]
            }
            val result = roundPositiveDoubleToLong(scaled)
            if (result != 0L) { readLong(storage, result); storage.scale -= fraction }
        }
        storage.compact()
        return state
    }
    private fun readLong(storage: DecimalBcdStorage, n: Long) {
        if (n <= Int.MAX_VALUE) storage.readIntToBcd(n.toInt()) else storage.readLongToBcd(n)
    }
    fun makeAccurate(storage: DecimalBcdStorage, state: State) {
        val n = storage.origDouble
        assertDecimalBcd(n != 0.0)
        val delta = storage.origDelta
        storage.setBcdToZero()
        val text = javaCompatibleDoubleString(n)
        val exponent = text.indexOf('E')
        when {
            exponent != -1 -> {
                readLong(storage, (text[0] + text.substring(2, exponent)).toLong())
                storage.scale += text.substring(exponent + 1).toInt() - (exponent - 1) + 1
            }
            text[0] == '0' -> { readLong(storage, text.substring(2).toLong()); storage.scale += 2 - text.length }
            text.last() == '0' -> readLong(storage, text.substring(0, text.length - 2).toLong())
            else -> {
                val point = text.indexOf('.')
                readLong(storage, (text.substring(0, point) + text.substring(point + 1)).toLong())
                storage.scale += point - text.length + 1
            }
        }
        storage.scale += delta; storage.compact(); state.explicitlyExact = true
    }
}
internal expect fun javaCompatibleDoubleString(value: Double): String
internal expect fun roundPositiveDoubleToLong(value: Double): Long
