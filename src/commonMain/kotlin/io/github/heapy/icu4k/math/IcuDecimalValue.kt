// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.math

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.impl.number.CommonDoubleToDecimal
import io.github.heapy.icu4k.util.BigDecimal as StandardBigDecimal

/** Mutable working representation for the legacy ICU decimal algorithms; copies share mantissas. */
internal class IcuDecimalValue {
    var ind: Byte = 0
    var form: Byte = 0
    internal var rawMant: ByteArray? = ZERO
    var mant: ByteArray
        get() = rawMant!!
        set(value) { rawMant = value }
    var exp: Int = 0
    fun copy(): IcuDecimalValue = IcuDecimalValue().also { it.ind = ind; it.form = form; it.rawMant = rawMant; it.exp = exp }
    fun integerText(): String {
        val result: IcuDecimalValue
        if (exp >= 0 && form.toInt() == MathContext.PLAIN) result = this
        else if (exp >= 0) result = copy().also { it.form = MathContext.PLAIN.toByte() }
        else if (-exp >= mant.size) result = ZERO_VALUE
        else result = copy().also { it.mant = it.mant.copyOfRange(0, it.mant.size + it.exp); it.form = MathContext.PLAIN.toByte(); it.exp = 0 }
        return result.layout().concatToString()
    }
    fun exactIntegerText(): String {
        if (exp < 0 && !IcuDecimalDigits.allZero(mant, mant.size + exp)) throw ArithmeticException("Decimal part non-zero: $this")
        return integerText()
    }
    fun unscaledText(): String = if (exp >= 0) integerText() else copy().also { it.exp = 0 }.integerText()
    fun toStandardBigDecimal(): StandardBigDecimal {
        // Equivalent to new java.math.BigDecimal(unscaledValue(), scale()).
        val unscaled = unscaledText()
        val scale = scale()
        return StandardBigDecimal(unscaled + "E" + (-scale.toLong()))
    }
    fun lowLongValue(): Long {
        val text = integerText()
        var result = 0L
        var i = if (text[0] == '-') 1 else 0
        while (i < text.length) { result = result * 10 + (text[i] - '0'); i++ }
        return if (text[0] == '-') -result else result
    }
    fun lowIntValue(): Int = lowLongValue().toInt()
    fun longValueExact(): Long {
        if (ind.toInt() == 0) return 0L
        var lodigit = mant.size - 1
        val useexp: Int
        if (exp < 0) {
            lodigit += exp
            val cstart = if (lodigit < 0) 0 else lodigit + 1
            if (!IcuDecimalDigits.allZero(mant, cstart)) throw ArithmeticException("Decimal part non-zero: $this")
            if (lodigit < 0) return 0L
            useexp = 0
        } else {
            // ICU's source rejects 19-digit exponent-zero values here; preserve this asymmetry.
            if (exp + mant.size > 18) throw ArithmeticException("Conversion overflow: $this")
            useexp = exp
        }
        var result = 0L
        for (i in 0..lodigit + useexp) { result *= 10; if (i <= lodigit) result += mant[i] }
        if (lodigit + useexp == 18 && result / 1000000000000000000L != mant[0].toLong()) {
            if (result == Long.MIN_VALUE && ind.toInt() == -1 && mant[0].toInt() == 9) return result
            throw ArithmeticException("Conversion overflow: $this")
        }
        return if (ind.toInt() == 1) result else -result
    }
    fun byteValueExact(): Byte {
        val value = intValueExact()
        if (value > 127 || value < -128) throw ArithmeticException("Conversion overflow: $this")
        return value.toByte()
    }
    fun shortValueExact(): Short {
        val value = intValueExact()
        if (value > 32767 || value < -32768) throw ArithmeticException("Conversion overflow: $this")
        return value.toShort()
    }
    fun doubleValue(): Double = toString().toDouble()
    fun floatValue(): Float = icuDecimalToFloat(toString())
    fun signum(): Int = ind.toInt()
    fun movePointLeft(n: Int): IcuDecimalValue = copy().also { it.exp -= n }.finish(PLAIN_CONTEXT, false)
    fun movePointRight(n: Int): IcuDecimalValue = copy().also { it.exp += n }.finish(PLAIN_CONTEXT, false)
    /** Equality operation for the eventual immutable facade; working values retain object identity. */
    fun sameRepresentation(rhs: IcuDecimalValue?): Boolean {
        if (rhs == null || ind != rhs.ind) return false
        if ((mant.size == rhs.mant.size) and (exp == rhs.exp) and (form == rhs.form)) return mant.contentEquals(rhs.mant)
        return layout().contentEquals(rhs.layout())
    }
    fun representationHashCode(): Int = toString().hashCode()
    fun format(before: Int, after: Int): String = format(before, after, -1, -1, MathContext.SCIENTIFIC, MathContext.ROUND_HALF_UP)
    fun format(before: Int, after: Int, explaces: Int, exdigits: Int, initialForm: Int, initialRound: Int): String {
        fun badarg(position: Int, value: Int): Nothing = throw IllegalArgumentException("Bad argument $position to format: $value")
        if (before < -1 || before == 0) badarg(1, before)
        if (after < -1) badarg(2, after)
        if (explaces < -1 || explaces == 0) badarg(3, explaces)
        // This diagnostic uses explaces in ICU4J, rather than exdigits.
        if (exdigits < -1) badarg(4, explaces)
        val exform = when (initialForm) {
            MathContext.SCIENTIFIC, MathContext.ENGINEERING -> initialForm
            -1 -> MathContext.SCIENTIFIC
            else -> badarg(5, initialForm)
        }
        var exround = initialRound
        if (exround != MathContext.ROUND_HALF_UP) {
            try {
                if (exround == -1) exround = MathContext.ROUND_HALF_UP
                else MathContext(9, MathContext.SCIENTIFIC, false, exround)
            } catch (failure: IllegalArgumentException) { badarg(6, exround) }
        }
        val num = copy()
        if (exdigits == -1 || num.ind.toInt() == 0) num.form = MathContext.PLAIN.toByte()
        else {
            val mag = num.exp + num.mant.size
            num.form = if (mag > exdigits || mag < -5) exform.toByte() else MathContext.PLAIN.toByte()
        }
        if (after >= 0) {
            while (true) {
                val thisafter = when (num.form.toInt()) {
                    MathContext.PLAIN -> -num.exp
                    MathContext.SCIENTIFIC -> num.mant.size - 1
                    else -> {
                        var lead = (num.exp + num.mant.size - 1) % 3
                        if (lead < 0) lead += 3
                        lead++
                        if (lead >= num.mant.size) 0 else num.mant.size - lead
                    }
                }
                if (thisafter == after) break
                if (thisafter < after) {
                    num.mant = IcuDecimalDigits.extend(num.mant, num.mant.size + after - thisafter)
                    num.exp -= after - thisafter
                    if (num.exp < MIN_EXP) throw ArithmeticException("Exponent Overflow: ${num.exp}")
                    break
                }
                val chop = thisafter - after
                if (chop > num.mant.size) { num.mant = ZERO; num.ind = 0; num.exp = 0; continue }
                val need = num.mant.size - chop
                val oldexp = num.exp
                num.round(need, exround)
                if (num.exp - oldexp == chop) break
            }
        }
        var chars = num.layout()
        if (before > 0) {
            var p = 0
            while (p < chars.size) { if (chars[p] == '.' || chars[p] == 'E') break; p++ }
            if (p > before) badarg(1, before)
            if (p < before) {
                val expanded = CharArray(chars.size + before - p)
                var i = 0; var remaining = before - p
                while (remaining > 0) { expanded[i++] = ' '; remaining-- }
                chars.copyInto(expanded, i); chars = expanded
            }
        }
        if (explaces > 0) {
            var p = chars.size - 1
            var remaining = chars.size - 1
            while (remaining > 0) { if (chars[p] == 'E') break; remaining--; p-- }
            if (p == 0) {
                val expanded = CharArray(chars.size + explaces + 2)
                chars.copyInto(expanded)
                var i = chars.size; remaining = explaces + 2
                while (remaining > 0) { expanded[i++] = ' '; remaining-- }
                chars = expanded
            } else {
                val places = chars.size - p - 2
                if (places > explaces) badarg(3, explaces)
                if (places < explaces) {
                    val expanded = CharArray(chars.size + explaces - places)
                    chars.copyInto(expanded, 0, 0, p + 2)
                    var i = p + 2; remaining = explaces - places
                    while (remaining > 0) { expanded[i++] = '0'; remaining-- }
                    chars.copyInto(expanded, i, p + 2, p + 2 + places); chars = expanded
                }
            }
        }
        return chars.concatToString()
    }
    fun scale(): Int = if (exp >= 0) 0 else -exp
    fun setScale(scale: Int, mode: Int = MathContext.ROUND_UNNECESSARY): IcuDecimalValue {
        val ourscale = scale()
        if (ourscale == scale && form.toInt() == MathContext.PLAIN) return this
        val result = copy()
        if (ourscale <= scale) {
            val padding = if (ourscale == 0) result.exp + scale else scale - ourscale
            result.mant = IcuDecimalDigits.extend(result.mant, result.mant.size + padding); result.exp = -scale
        } else {
            if (scale < 0) throw ArithmeticException("Negative scale: $scale")
            result.round(result.mant.size - (ourscale - scale), mode)
            if (result.exp != -scale) { result.mant = IcuDecimalDigits.extend(result.mant, result.mant.size + 1); result.exp-- }
        }
        result.form = MathContext.PLAIN.toByte()
        return result
    }
    fun intValueExact(): Int {
        if (ind.toInt() == 0) return 0
        var lodigit = mant.size - 1
        val useexp: Int
        if (exp < 0) {
            lodigit += exp
            if (!IcuDecimalDigits.allZero(mant, lodigit + 1)) throw ArithmeticException("Decimal part non-zero: $this")
            if (lodigit < 0) return 0
            useexp = 0
        } else {
            if (exp + lodigit > 9) throw ArithmeticException("Conversion overflow: $this")
            useexp = exp
        }
        var result = 0
        for (i in 0..lodigit + useexp) { result *= 10; if (i <= lodigit) result += mant[i] }
        if (lodigit + useexp == 9 && result / 1000000000 != mant[0].toInt()) {
            if (result == Int.MIN_VALUE && ind.toInt() == -1 && mant[0].toInt() == 2) return result
            throw ArithmeticException("Conversion overflow: $this")
        }
        return if (ind.toInt() == 1) result else -result
    }
    fun divide(rhs: IcuDecimalValue?, set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue = doDivide('D', rhs, set, -1)
    fun divide(rhs: IcuDecimalValue?, mode: Int): IcuDecimalValue = doDivide('D', rhs, MathContext(0, MathContext.PLAIN, false, mode), -1)
    fun divide(rhs: IcuDecimalValue?, scale: Int, mode: Int): IcuDecimalValue {
        if (scale < 0) throw ArithmeticException("Negative scale: $scale")
        return doDivide('D', rhs, MathContext(0, MathContext.PLAIN, false, mode), scale)
    }
    fun divideInteger(rhs: IcuDecimalValue?, set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue = doDivide('I', rhs, set, 0)
    fun remainder(rhs: IcuDecimalValue?, set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue = doDivide('R', rhs, set, -1)
    private fun doDivide(code: Char, right: IcuDecimalValue?, set: MathContext?, initialScale: Int): IcuDecimalValue {
        if (set!!.lostDigits) checkDigits(right, set.digits)
        var lhs = this; var rhs = right
        var scale = initialScale
        if (rhs!!.ind.toInt() == 0) throw ArithmeticException("Divide by 0")
        if (lhs.ind.toInt() == 0) {
            if (set.form != MathContext.PLAIN) return ZERO_VALUE
            if (scale == -1) return lhs
            return lhs.setScale(scale)
        }
        var reqdig = set.digits
        if (reqdig > 0) {
            if (lhs.mant.size > reqdig) lhs = lhs.copy().round(set)
            if (rhs.mant.size > reqdig) rhs = rhs.copy().round(set)
        } else {
            if (scale == -1) scale = lhs.scale()
            reqdig = lhs.mant.size
            if (scale != -lhs.exp) reqdig = reqdig + scale + lhs.exp
            reqdig = reqdig - (rhs.mant.size - 1) - rhs.exp
            if (reqdig < lhs.mant.size) reqdig = lhs.mant.size
            if (reqdig < rhs.mant.size) reqdig = rhs.mant.size
        }
        val newexp = lhs.exp - rhs.exp + lhs.mant.size - rhs.mant.size
        if (newexp < 0 && code != 'D') {
            if (code == 'I') return ZERO_VALUE
            return lhs.copy().finish(set, false)
        }
        val result = IcuDecimalValue()
        result.ind = (lhs.ind * rhs.ind).toByte(); result.exp = newexp; result.mant = ByteArray(reqdig + 1)
        val newlen = reqdig + reqdig + 1
        var var1 = IcuDecimalDigits.extend(lhs.mant, newlen)
        var var1len = newlen
        val var2 = rhs.mant
        var var2len = newlen
        var b2b = var2[0] * 10 + 1
        if (var2.size > 1) b2b += var2[1]
        var have = 0
        outer@ while (true) {
            var thisdigit = 0
            inner@ while (true) {
                if (var1len < var2len) break
                val ba: Int
                if (var1len == var2len) {
                    var equal = true
                    for (i in 0 until var1len) {
                        val v2: Byte = if (i < var2.size) var2[i] else 0
                        if (var1[i] < v2) break@inner
                        if (var1[i] > v2) { equal = false; break }
                    }
                    if (equal) { thisdigit++; result.mant[have++] = thisdigit.toByte(); var1[0] = 0; break@outer }
                    ba = var1[0].toInt()
                } else { ba = var1[0] * 10 + if (var1len > 1) var1[1].toInt() else 0 }
                var mult = ba * 10 / b2b
                if (mult == 0) mult = 1
                thisdigit += mult
                var1 = IcuDecimalDigits.addSub(var1, var1len, var2, var2len, -mult, true)
                if (var1[0].toInt() != 0) continue
                var start = 0
                val last = var1len - 2
                while (start <= last) { if (var1[start].toInt() != 0) break; var1len--; start++ }
                if (start == 0) continue
                var1.copyInto(var1, 0, start, start + var1len)
            }
            if (have != 0 || thisdigit != 0) {
                result.mant[have++] = thisdigit.toByte()
                if (have == reqdig + 1 || var1[0].toInt() == 0) break
            }
            if (scale >= 0 && -result.exp > scale) break
            if (code != 'D' && result.exp <= 0) break
            result.exp--; var2len--
        }
        if (have == 0) have = 1
        if (code == 'I' || code == 'R') {
            if (have + result.exp > reqdig) throw ArithmeticException("Integer overflow")
            if (code == 'R') {
                if (result.mant[0].toInt() == 0) return lhs.copy().finish(set, false)
                if (var1[0].toInt() == 0) return ZERO_VALUE
                result.ind = lhs.ind
                val padding = reqdig + reqdig + 1 - lhs.mant.size
                result.exp = result.exp - padding + lhs.exp
                var d = var1len
                var i = d - 1
                while (i >= 1) {
                    if (!(result.exp < lhs.exp && result.exp < rhs.exp)) break
                    if (var1[i].toInt() != 0) break
                    d--; result.exp++; i--
                }
                if (d < var1.size) var1 = var1.copyOfRange(0, d)
                result.mant = var1
                return result.finish(set, false)
            }
        } else if (var1[0].toInt() != 0) {
            val lasthave = result.mant[have - 1]
            if (lasthave % 5 == 0) result.mant[have - 1] = (lasthave + 1).toByte()
        }
        if (scale >= 0) {
            if (have != result.mant.size) result.exp -= result.mant.size - have
            val actdig = result.mant.size - (-result.exp - scale)
            result.round(actdig, set.roundingMode)
            if (result.exp != -scale) { result.mant = IcuDecimalDigits.extend(result.mant, result.mant.size + 1); result.exp-- }
            return result.finish(set, true)
        }
        if (have == result.mant.size) result.round(set)
        else {
            if (result.mant[0].toInt() == 0) return ZERO_VALUE
            result.mant = result.mant.copyOfRange(0, have)
        }
        return result.finish(set, true)
    }
    fun pow(rhs: IcuDecimalValue?, set: MathContext? = PLAIN_CONTEXT): IcuDecimalValue {
        if (set!!.lostDigits) checkDigits(rhs, set.digits)
        var n = rhs!!.intValueExact()
        if (n < -999999999 || n > 999999999) throw ArithmeticException("Conversion overflow: $n")
        var lhs = this
        val reqdig = set.digits
        val workdigits: Int
        if (reqdig == 0) {
            if (rhs.ind.toInt() == -1) throw ArithmeticException("Negative power: $rhs")
            workdigits = 0
        } else {
            if (rhs.mant.size + rhs.exp > reqdig) throw ArithmeticException("Too many digits: $rhs")
            if (lhs.mant.size > reqdig) lhs = lhs.copy().round(set)
            val length = rhs.mant.size + rhs.exp
            workdigits = reqdig + length + 1
        }
        val workset = MathContext(workdigits, set.form, false, set.roundingMode)
        var result = ONE_VALUE
        if (n == 0) return result
        if (n < 0) n = -n
        var seenbit = false
        for (i in 1..31) {
            n += n
            if (n < 0) { seenbit = true; result = result.multiply(lhs, workset) }
            if (i == 31) break
            if (!seenbit) continue
            result = result.multiply(result, workset)
        }
        if (rhs.ind < 0) result = ONE_VALUE.divide(result, workset)
        return result.finish(set, true)
    }
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
        val ONE_VALUE = IcuDecimalValue().also { it.ind = 1; it.mant = ONE }
        val TEN_VALUE = fromLong(10)
        private val PLAIN_CONTEXT = MathContext(0, MathContext.PLAIN)
        fun fromInt(input: Int): IcuDecimalValue {
            if (input in -9..9) return IcuDecimalValue().also {
                when (input) {
                    0 -> { it.mant = ZERO; it.ind = 0 }
                    1 -> { it.mant = ONE; it.ind = 1 }
                    -1 -> { it.mant = ONE; it.ind = -1 }
                    else -> { it.mant = byteArrayOf((if (input > 0) input else -input).toByte()); it.ind = if (input > 0) 1 else -1 }
                }
            }
            val result = IcuDecimalValue()
            var num = input
            if (num > 0) { result.ind = 1; num = -num } else result.ind = -1
            var mun = num; var i = 9
            while (true) { mun /= 10; if (mun == 0) break; i-- }
            result.mant = ByteArray(10 - i); i = 10 - i - 1
            while (true) { result.mant[i] = (-(num % 10).toByte()).toByte(); num /= 10; if (num == 0) break; i-- }
            return result
        }
        fun fromLong(input: Long): IcuDecimalValue {
            val result = IcuDecimalValue()
            var num = input
            if (num > 0) { result.ind = 1; num = -num } else result.ind = if (num == 0L) 0 else -1
            var mun = num; var i = 18
            while (true) { mun /= 10; if (mun == 0L) break; i-- }
            result.mant = ByteArray(19 - i); i = 19 - i - 1
            while (true) { result.mant[i] = (-(num % 10).toByte()).toByte(); num /= 10; if (num == 0L) break; i-- }
            return result
        }
        fun fromDouble(input: Double): IcuDecimalValue = parse(StandardBigDecimal(input).toString())
        fun fromStandardBigDecimal(input: StandardBigDecimal?): IcuDecimalValue = parse(input!!.toString())
        fun fromIntegerText(input: String?, scale: Int = 0): IcuDecimalValue {
            val result = parse(input)
            if (scale < 0) throw NumberFormatException("Negative scale: $scale")
            result.exp = -scale
            return result
        }
        fun valueOf(input: Long, scale: Int = 0): IcuDecimalValue {
            val result = when (input) { 0L -> ZERO_VALUE; 1L -> ONE_VALUE; 10L -> TEN_VALUE; else -> fromLong(input) }
            if (scale == 0) return result
            if (scale < 0) throw NumberFormatException("Negative scale: $scale")
            return result.copy().also { it.exp = -scale }
        }
        fun valueOf(input: Double): IcuDecimalValue = parse(CommonDoubleToDecimal.format(input))
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

internal expect fun icuDecimalToFloat(text: String): Float

/** Correct a binary64-to-binary32 candidate using exact decimal midpoint comparisons. */
internal fun icuDecimalFloatFromCandidate(text: String, candidate: Float): Float {
    val value = IcuDecimalValue.parse(text)
    val negative = value.ind < 0
    if (negative) value.ind = 1
    var bits = candidate.toRawBits() and 0x7fffffff
    fun midpoint(lowerBits: Int): IcuDecimalValue {
        val exponent = lowerBits ushr 23
        val significand = if (exponent == 0) lowerBits and 0x7fffff else (lowerBits and 0x7fffff) or 0x800000
        val power = (if (exponent == 0) -149 else exponent - 150) - 1
        var boundary = IcuDecimalValue.parse((significand * 2 + 1).toString())
        if (power >= 0) boundary = boundary.multiply(IcuDecimalValue.parse("2").pow(IcuDecimalValue.parse(power.toString())))
        else { boundary = boundary.multiply(IcuDecimalValue.parse("5").pow(IcuDecimalValue.parse((-power).toString()))); boundary.exp += power }
        return boundary
    }
    fun compare(boundary: IcuDecimalValue): Int {
        if (value.ind.toInt() == 0) return -1
        val magnitude = value.exp.toLong() + value.mant.size
        val boundaryMagnitude = boundary.exp.toLong() + boundary.mant.size
        if (magnitude != boundaryMagnitude) return if (magnitude < boundaryMagnitude) -1 else 1
        for (i in 0 until maxOf(value.mant.size, boundary.mant.size)) {
            val a = if (i < value.mant.size) value.mant[i].toInt() else 0
            val b = if (i < boundary.mant.size) boundary.mant[i].toInt() else 0
            if (a != b) return if (a < b) -1 else 1
        }
        return 0
    }
    if (bits > 0) {
        val comparison = compare(midpoint(bits - 1))
        if (comparison < 0 || comparison == 0 && bits and 1 != 0) return Float.fromBits((bits - 1) or if (negative) Int.MIN_VALUE else 0)
    }
    if (bits < 0x7f800000) {
        val comparison = compare(midpoint(bits))
        if (comparison > 0 || comparison == 0 && bits and 1 != 0) bits++
    }
    return Float.fromBits(bits or if (negative) Int.MIN_VALUE else 0)
}
