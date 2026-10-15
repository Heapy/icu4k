// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.math

import io.github.heapy.icu4k.lang.UCharacter

/** Mutable working representation for the legacy ICU decimal algorithms; copies share mantissas. */
internal class IcuDecimalValue {
    var ind: Byte = 0
    var form: Byte = 0
    var mant: ByteArray = ZERO
    var exp: Int = 0
    fun copy(): IcuDecimalValue = IcuDecimalValue().also { it.ind = ind; it.form = form; it.mant = mant; it.exp = exp }
    fun checkDigits(rhs: IcuDecimalValue?, digits: Int) {
        if (digits == 0) return
        if (mant.size > digits && !IcuDecimalDigits.allZero(mant, digits)) throw ArithmeticException("Too many digits: $this")
        if (rhs == null) return
        if (rhs.mant.size > digits && !IcuDecimalDigits.allZero(rhs.mant, digits)) throw ArithmeticException("Too many digits: $rhs")
    }
    fun round(set: MathContext): IcuDecimalValue = round(set.digits, set.roundingMode)
    fun round(length: Int, mode: Int): IcuDecimalValue {
        val adjust = mant.size - length
        if (adjust <= 0) return this
        exp += adjust
        val sign = ind.toInt()
        val oldmant = mant
        val reuse: Boolean
        val first: Byte
        if (length > 0) { mant = oldmant.copyOfRange(0, length); reuse = true; first = oldmant[length] }
        else { mant = ZERO; ind = 0; reuse = false; first = if (length == 0) oldmant[0] else 0 }
        var increment = 0
        when (mode) {
            MathContext.ROUND_HALF_UP -> if (first >= 5) increment = sign
            MathContext.ROUND_UNNECESSARY -> if (!IcuDecimalDigits.allZero(oldmant, length)) throw ArithmeticException("Rounding necessary")
            MathContext.ROUND_HALF_DOWN -> if (first > 5 || first.toInt() == 5 && !IcuDecimalDigits.allZero(oldmant, length + 1)) increment = sign
            MathContext.ROUND_HALF_EVEN -> if (first > 5 || first.toInt() == 5 && (!IcuDecimalDigits.allZero(oldmant, length + 1) || mant.last() % 2 != 0)) increment = sign
            MathContext.ROUND_DOWN -> {}
            MathContext.ROUND_UP -> if (!IcuDecimalDigits.allZero(oldmant, length)) increment = sign
            MathContext.ROUND_CEILING -> if (sign > 0 && !IcuDecimalDigits.allZero(oldmant, length)) increment = sign
            MathContext.ROUND_FLOOR -> if (sign < 0 && !IcuDecimalDigits.allZero(oldmant, length)) increment = sign
            else -> throw IllegalArgumentException("Bad round value: $mode")
        }
        if (increment != 0) {
            if (ind.toInt() == 0) { mant = ONE; ind = increment.toByte() }
            else {
                if (ind.toInt() == -1) increment = -increment
                val newmant = IcuDecimalDigits.addSub(mant, mant.size, ONE, 1, increment, reuse)
                if (newmant.size > mant.size) { exp++; newmant.copyInto(mant, 0, 0, mant.size) }
                else mant = newmant
            }
        }
        if (exp > MAX_EXP) throw ArithmeticException("Exponent Overflow: $exp")
        return this
    }
    fun finish(set: MathContext, strip: Boolean): IcuDecimalValue {
        if (set.digits != 0 && mant.size > set.digits) round(set)
        if (strip && set.form != MathContext.PLAIN) {
            var d = mant.size
            var i = d - 1
            while (i >= 1) { if (mant[i].toInt() != 0) break; d--; exp++; i-- }
            if (d < mant.size) mant = mant.copyOfRange(0, d)
        }
        form = MathContext.PLAIN.toByte()
        for (i in mant.indices) {
            if (mant[i].toInt() != 0) {
                if (i > 0) mant = mant.copyOfRange(i, mant.size)
                var mag = exp + mant.size
                if (mag > 0) {
                    if (mag > set.digits && set.digits != 0) form = set.form.toByte()
                    if (mag - 1 <= MAX_EXP) return this
                } else if (mag < -5) form = set.form.toByte()
                mag--
                if (mag < MIN_EXP || mag > MAX_EXP) {
                    if (form.toInt() == MathContext.ENGINEERING) {
                        var sig = mag % 3
                        if (sig < 0) sig += 3
                        mag -= sig
                        if (mag >= MIN_EXP && mag <= MAX_EXP) return this
                    }
                    throw ArithmeticException("Exponent Overflow: $mag")
                }
                return this
            }
        }
        ind = 0
        if (set.form != MathContext.PLAIN || exp > 0) exp = 0
        else if (exp < MIN_EXP) throw ArithmeticException("Exponent Overflow: $exp")
        mant = ZERO
        return this
    }
    fun layout(): CharArray {
        val cmant = CharArray(mant.size) { ('0'.code + mant[it]).toChar() }
        if (form.toInt() != MathContext.PLAIN) {
            val sb = StringBuilder(cmant.size + 15)
            if (ind.toInt() == -1) sb.append('-')
            var euse = exp + cmant.size - 1
            if (form.toInt() == MathContext.SCIENTIFIC) {
                sb.append(cmant[0]); if (cmant.size > 1) sb.append('.').appendRange(cmant, 1, cmant.size)
            } else {
                var sig = euse % 3
                if (sig < 0) sig += 3
                euse -= sig; sig++
                if (sig >= cmant.size) { sb.append(cmant); repeat(sig - cmant.size) { sb.append('0') } }
                else sb.appendRange(cmant, 0, sig).append('.').appendRange(cmant, sig, cmant.size)
            }
            if (euse != 0) {
                val sign = if (euse < 0) { euse = -euse; '-' } else '+'
                sb.append('E').append(sign).append(euse)
            }
            return sb.toString().toCharArray()
        }
        if (exp == 0) {
            if (ind >= 0) return cmant
            return CharArray(cmant.size + 1).also { it[0] = '-'; cmant.copyInto(it, 1) }
        }
        val needsign = if (ind.toInt() == -1) 1 else 0
        val mag = exp + cmant.size
        if (mag < 1) {
            val result = CharArray(needsign + 2 - exp)
            if (needsign != 0) result[0] = '-'
            result[needsign] = '0'; result[needsign + 1] = '.'
            var i = needsign + 2; var remaining = -mag
            while (remaining > 0) { result[i++] = '0'; remaining-- }
            cmant.copyInto(result, needsign + 2 - mag)
            return result
        }
        if (mag > cmant.size) {
            val result = CharArray(needsign + mag)
            if (needsign != 0) result[0] = '-'
            cmant.copyInto(result, needsign)
            var i = needsign + cmant.size; var remaining = mag - cmant.size
            while (remaining > 0) { result[i++] = '0'; remaining-- }
            return result
        }
        return CharArray(needsign + 1 + cmant.size).also {
            if (needsign != 0) it[0] = '-'
            cmant.copyInto(it, needsign, 0, mag); it[needsign + mag] = '.'
            cmant.copyInto(it, needsign + mag + 1, mag, cmant.size)
        }
    }
    override fun toString(): String = layout().concatToString()
    companion object {
        const val MIN_EXP = -999999999
        const val MAX_EXP = 999999999
        private val ZERO = byteArrayOf(0)
        private val ONE = byteArrayOf(1)
        fun parse(text: String?): IcuDecimalValue = parse(text!!.toCharArray(), 0, text.length)
        private fun bad(chars: CharArray?): Nothing = throw NumberFormatException("Not a number: " + chars!!.concatToString())
        fun parse(chars: CharArray?, initialOffset: Int, initialLength: Int): IcuDecimalValue {
            val value = IcuDecimalValue()
            var offset = initialOffset; var length = initialLength
            if (length <= 0) bad(chars)
            value.ind = 1
            if (chars!![offset] == '-') { length--; if (length == 0) bad(chars); value.ind = -1; offset++ }
            else if (chars[offset] == '+') { length--; if (length == 0) bad(chars); offset++ }
            var exotic = false; var hadexp = false
            var d = 0; var dotoff = -1; var last = -1
            var i = offset; var remaining = length
            while (remaining > 0) {
                val ch = chars[i]
                if (ch in '0'..'9') { last = i; d++; i++; remaining--; continue }
                if (ch == '.') { if (dotoff >= 0) bad(chars); dotoff = i - offset; i++; remaining--; continue }
                if (ch != 'e' && ch != 'E') {
                    if (!UCharacter.isDigit(ch.code)) bad(chars)
                    exotic = true; last = i; d++; i++; remaining--; continue
                }
                if (i - offset > length - 2) bad(chars)
                val negative = chars[i + 1] == '-'
                val k = if (negative || chars[i + 1] == '+') i + 2 else i + 1
                val elen = length - (k - offset)
                if (elen == 0 || elen > 9) bad(chars)
                var j = k; var count = elen
                while (count > 0) {
                    val sj = chars[j]
                    if (sj < '0') bad(chars)
                    val digit = if (sj > '9') {
                        if (!UCharacter.isDigit(sj.code)) bad(chars)
                        UCharacter.digit(sj.code, 10).also { if (it < 0) bad(chars) }
                    } else sj - '0'
                    value.exp = value.exp * 10 + digit; count--; j++
                }
                if (negative) value.exp = -value.exp
                hadexp = true; break
            }
            if (d == 0) bad(chars)
            if (dotoff >= 0) value.exp = value.exp + dotoff - d
            i = offset
            while (i <= last - 1) {
                val ch = chars[i]
                if (ch == '0') { offset++; dotoff--; d-- }
                else if (ch == '.') { offset++; dotoff-- }
                else if (ch <= '9') break
                else { if (UCharacter.digit(ch.code, 10) != 0) break; offset++; dotoff--; d-- }
                i++
            }
            value.mant = ByteArray(d)
            var j = offset
            for (index in 0 until d) {
                if (index == dotoff) j++
                val ch = chars[j]
                value.mant[index] = if (!exotic || ch <= '9') (ch - '0').toByte()
                else UCharacter.digit(ch.code, 10).also { if (it < 0) bad(chars) }.toByte()
                j++
            }
            if (value.mant[0].toInt() == 0) {
                value.ind = 0
                if (value.exp > 0) value.exp = 0
                if (hadexp) { value.mant = ZERO; value.exp = 0 }
            } else if (hadexp) {
                value.form = MathContext.SCIENTIFIC.toByte()
                val mag = value.exp + value.mant.size - 1
                if (mag < MIN_EXP || mag > MAX_EXP) bad(chars)
            }
            return value
        }
    }
}
