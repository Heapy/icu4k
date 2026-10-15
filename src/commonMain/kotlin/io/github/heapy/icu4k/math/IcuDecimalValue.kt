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
    fun plus(set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue {
        if (set!!.lostDigits) checkDigits(null, set.digits)
        if (set.form == MathContext.PLAIN && form.toInt() == MathContext.PLAIN && (mant.size <= set.digits || set.digits == 0)) return this
        return copy().finish(set, false)
    }
    fun negate(set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue {
        if (set!!.lostDigits) checkDigits(null, set.digits)
        val result = copy(); result.ind = (-result.ind).toByte()
        return result.finish(set, false)
    }
    fun abs(set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue = if (ind.toInt() == -1) negate(set) else plus(set)
    fun subtract(rhs: IcuDecimalValue?, set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue {
        if (set!!.lostDigits) checkDigits(rhs, set.digits)
        val newrhs = rhs!!.copy(); newrhs.ind = (-newrhs.ind).toByte()
        return add(newrhs, set)
    }
    fun add(right: IcuDecimalValue?, set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue {
        if (set!!.lostDigits) checkDigits(right, set.digits)
        var lhs = this
        var rhs = right
        if (lhs.ind.toInt() == 0 && set.form != MathContext.PLAIN) return rhs!!.plus(set)
        if (rhs!!.ind.toInt() == 0 && set.form != MathContext.PLAIN) return lhs.plus(set)
        val reqdig = set.digits
        if (reqdig > 0) {
            if (lhs.mant.size > reqdig) lhs = lhs.copy().round(set)
            if (rhs.mant.size > reqdig) rhs = rhs.copy().round(set)
        }
        val result = IcuDecimalValue()
        var usel = lhs.mant; var usellen = lhs.mant.size
        var user = rhs.mant; var userlen = rhs.mant.size
        if (lhs.exp == rhs.exp) result.exp = lhs.exp
        else if (lhs.exp > rhs.exp) {
            var newlen = usellen + lhs.exp - rhs.exp
            if (newlen >= userlen + reqdig + 1 && reqdig > 0) {
                result.mant = usel; result.exp = lhs.exp; result.ind = lhs.ind
                if (usellen < reqdig) { result.mant = IcuDecimalDigits.extend(lhs.mant, reqdig); result.exp -= reqdig - usellen }
                return result.finish(set, false)
            }
            result.exp = rhs.exp
            if (newlen > reqdig + 1 && reqdig > 0) {
                val tlen = newlen - reqdig - 1
                userlen -= tlen; result.exp += tlen; newlen = reqdig + 1
            }
            if (newlen > usellen) usellen = newlen
        } else {
            var newlen = userlen + rhs.exp - lhs.exp
            if (newlen >= usellen + reqdig + 1 && reqdig > 0) {
                result.mant = user; result.exp = rhs.exp; result.ind = rhs.ind
                if (userlen < reqdig) { result.mant = IcuDecimalDigits.extend(rhs.mant, reqdig); result.exp -= reqdig - userlen }
                return result.finish(set, false)
            }
            result.exp = lhs.exp
            if (newlen > reqdig + 1 && reqdig > 0) {
                val tlen = newlen - reqdig - 1
                usellen -= tlen; result.exp += tlen; newlen = reqdig + 1
            }
            if (newlen > userlen) userlen = newlen
        }
        result.ind = if (lhs.ind.toInt() == 0) 1 else lhs.ind
        val mult: Int
        if ((lhs.ind.toInt() == -1) == (rhs.ind.toInt() == -1)) mult = 1
        else {
            mult = -1
            fun swap() {
                val t = usel; usel = user; user = t
                val tlen = usellen; usellen = userlen; userlen = tlen
                result.ind = (-result.ind).toByte()
            }
            if (rhs.ind.toInt() == 0) { /* Left side is larger. */ }
            else if (usellen < userlen || lhs.ind.toInt() == 0) swap()
            else if (usellen > userlen) { /* Left side is larger. */ }
            else {
                var ia = 0; var ib = 0
                val ea = usel.size - 1; val eb = user.size - 1
                while (true) {
                    val ca: Byte
                    if (ia <= ea) ca = usel[ia]
                    else {
                        if (ib > eb) { if (set.form != MathContext.PLAIN) return ZERO_VALUE; break }
                        ca = 0
                    }
                    val cb: Byte = if (ib <= eb) user[ib] else 0
                    if (ca != cb) { if (ca < cb) swap(); break }
                    ia++; ib++
                }
            }
        }
        result.mant = IcuDecimalDigits.addSub(usel, usellen, user, userlen, mult, false)
        return result.finish(set, false)
    }
    fun multiply(right: IcuDecimalValue?, set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue {
        if (set!!.lostDigits) checkDigits(right, set.digits)
        var lhs = this; var rhs = right
        var padding = 0
        val reqdig = set.digits
        if (reqdig > 0) {
            if (lhs.mant.size > reqdig) lhs = lhs.copy().round(set)
            if (rhs!!.mant.size > reqdig) rhs = rhs.copy().round(set)
        } else {
            if (lhs.exp > 0) padding += lhs.exp
            if (rhs!!.exp > 0) padding += rhs.exp
        }
        val multer: ByteArray; val multand: ByteArray
        if (lhs.mant.size < rhs.mant.size) { multer = lhs.mant; multand = rhs.mant }
        else { multer = rhs.mant; multand = lhs.mant }
        var multandlen = multer.size + multand.size - 1
        val acclen = if (multer[0] * multand[0] > 9) multandlen + 1 else multandlen
        val result = IcuDecimalValue()
        var acc = ByteArray(acclen)
        for (n in multer.indices) {
            val mult = multer[n]
            if (mult.toInt() != 0) acc = IcuDecimalDigits.addSub(acc, acc.size, multand, multandlen, mult.toInt(), true)
            multandlen--
        }
        result.ind = (lhs.ind * rhs.ind).toByte(); result.exp = lhs.exp + rhs.exp - padding
        result.mant = if (padding == 0) acc else IcuDecimalDigits.extend(acc, acc.size + padding)
        return result.finish(set, false)
    }
    fun compareTo(rhs: IcuDecimalValue?, set: MathContext? = PLAIN_CONTEXT): Int {
        if (set!!.lostDigits) checkDigits(rhs, set.digits)
        if ((ind == rhs!!.ind) and (exp == rhs.exp)) {
            val length = mant.size
            if (length < rhs.mant.size) return (-ind).toByte().toInt()
            if (length > rhs.mant.size) return ind.toInt()
            if ((length <= set.digits) or (set.digits == 0)) {
                for (i in 0 until length) {
                    if (mant[i] < rhs.mant[i]) return (-ind).toByte().toInt()
                    if (mant[i] > rhs.mant[i]) return ind.toInt()
                }
                return 0
            }
        } else {
            if (ind < rhs.ind) return -1
            if (ind > rhs.ind) return 1
        }
        val newrhs = rhs.copy(); newrhs.ind = (-newrhs.ind).toByte()
        return add(newrhs, set).ind.toInt()
    }
    fun max(rhs: IcuDecimalValue?, set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue = if (compareTo(rhs, set) >= 0) plus(set) else rhs!!.plus(set)
    fun min(rhs: IcuDecimalValue?, set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue = if (compareTo(rhs, set) <= 0) plus(set) else rhs!!.plus(set)
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
        val ZERO_VALUE = IcuDecimalValue()
        private val PLAIN_CONTEXT = MathContext(0, MathContext.PLAIN)
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
