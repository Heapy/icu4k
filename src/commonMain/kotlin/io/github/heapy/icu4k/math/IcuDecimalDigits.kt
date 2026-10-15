// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.math

/** Decimal-array arithmetic from ICU BigDecimal; virtual lengths append zeroes on the right. */
internal object IcuDecimalDigits {
    private val bytecar = ByteArray(190)
    private val bytedig = ByteArray(190).also { work ->
        for (op in 0..189) {
            var digit = op - 90
            if (digit >= 0) { work[op] = (digit % 10).toByte(); bytecar[op] = (digit / 10).toByte() }
            else { digit += 100; work[op] = (digit % 10).toByte(); bytecar[op] = (digit / 10 - 10).toByte() }
        }
    }
    fun extend(input: ByteArray?, newLength: Int): ByteArray {
        if (input!!.size == newLength) return input
        val result = ByteArray(newLength)
        input.copyInto(result, 0, 0, input.size)
        return result
    }
    /** Computes A + B * multiplier, retaining leading zeroes and source array-reuse behavior. */
    fun addSub(a: ByteArray?, avlen: Int, b: ByteArray?, bvlen: Int, multiplier: Int, reuse: Boolean): ByteArray {
        val alength = a!!.size
        val blength = b!!.size
        var ap = avlen - 1
        var bp = bvlen - 1
        var maxarr = bp
        if (maxarr < ap) maxarr = ap
        val result = if (reuse && maxarr + 1 == alength) a else ByteArray(maxarr + 1)
        val quick = multiplier == 1 || multiplier == -1
        var digit = 0
        var op = maxarr
        while (op >= 0) {
            if (ap >= 0) { if (ap < alength) digit += a[ap]; ap-- }
            if (bp >= 0) {
                if (bp < blength) {
                    digit += if (quick) { if (multiplier > 0) b[bp].toInt() else -b[bp].toInt() } else b[bp] * multiplier
                }
                bp--
            }
            if (digit < 10 && digit >= 0) { result[op] = digit.toByte(); digit = 0; op--; continue }
            val dp90 = digit + 90
            result[op] = bytedig[dp90]
            digit = bytecar[dp90].toInt()
            op--
        }
        if (digit == 0) return result
        val expanded = if (reuse && maxarr + 2 == a.size) a else ByteArray(maxarr + 2)
        expanded[0] = digit.toByte()
        if (maxarr < 10) {
            var i = 0
            var remaining = maxarr + 1
            while (remaining > 0) { expanded[i + 1] = result[i]; remaining--; i++ }
        } else result.copyInto(expanded, 1, 0, maxarr + 1)
        return expanded
    }
    fun allZero(array: ByteArray?, start: Int): Boolean {
        var i = if (start < 0) 0 else start
        val last = array!!.size - 1
        while (i <= last) { if (array[i].toInt() != 0) return false; i++ }
        return true
    }
}
