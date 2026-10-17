// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.math.BigDecimal as IcuBigDecimal
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*

/** ICU's mutable, integer-only date number formatter. Instances are not thread-safe. */
class DateNumberFormat : NumberFormat {
    private var digits: CharArray? = null
    private var zeroDigit: Char = '\u0000'
    private var minusSign: Char = '\u0000'
    private var positiveOnly = false
    @kotlin.jvm.Transient private var decimalBuf = CharArray(DECIMAL_BUF_SIZE)
    // Deliberately separate from NumberFormat's serialized integer-digit settings.
    private var maxIntDigits = 0
    private var minIntDigits = 0

    constructor(loc: ULocale?, digitString: String?, nsName: String?) : super() {
        if (digitString!!.length > 10) throw UnsupportedOperationException("DateNumberFormat does not support digits out of BMP.")
        initialize(loc, digitString, nsName)
    }
    constructor(loc: ULocale?, zeroDigit: Char, nsName: String?) : super() {
        initialize(loc, buildString { repeat(10) { append((zeroDigit.code + it).toChar()) } }, nsName)
    }
    private constructor(other: DateNumberFormat) : super() {
        other.copyNumberFormatTo(this)
        digits = other.digits; zeroDigit = other.zeroDigit; minusSign = other.minusSign
        positiveOnly = other.positiveOnly; maxIntDigits = other.maxIntDigits; minIntDigits = other.minIntDigits
    }
    private fun initialize(loc: ULocale?, digitString: String, nsName: String?) {
        var elems = CACHE.get(loc)
        if (elems == null) {
            val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, loc) as ICUResourceBundle
            val minus = try {
                bundle.getStringWithFallback("NumberElements/$nsName/symbols/minusSign")
            } catch (_: MissingResourceException) {
                if (nsName!! != "latn") {
                    try { bundle.getStringWithFallback("NumberElements/latn/symbols/minusSign") }
                    catch (_: MissingResourceException) { "-" }
                } else "-"
            }
            elems = CharArray(11)
            for (i in 0 until 10) elems[i] = digitString[i]
            elems[10] = minus[0]
            CACHE.put(loc, elems)
        }
        digits = elems.copyOfRange(0, 10)
        zeroDigit = digits!![0]
        minusSign = elems[10]
    }
    override fun setMaximumIntegerDigits(value: Int) { maxIntDigits = value }
    override fun getMaximumIntegerDigits(): Int = maxIntDigits
    override fun setMinimumIntegerDigits(value: Int) { minIntDigits = value }
    override fun getMinimumIntegerDigits(): Int = minIntDigits
    fun setParsePositiveOnly(isPositiveOnly: Boolean) { positiveOnly = isPositiveOnly }
    fun getZeroDigit(): Char = zeroDigit
    fun setZeroDigit(zero: Char) {
        zeroDigit = zero
        if (digits == null) digits = CharArray(10)
        digits!![0] = zero
        for (i in 1 until 10) digits!![i] = (zero.code + i).toChar()
    }
    fun getDigits(): CharArray = digits!!.copyOf()
    override fun format(number: Double, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder =
        throw UnsupportedOperationException("StringBuffer format(double, StringBuffer, FieldPosition) is not implemented")
    override fun format(number: Long, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        var value = number
        if (value < 0) { output!!.append(minusSign); value = -value }
        var integer = value.toInt()
        val limit = minOf(decimalBuf.size, maxIntDigits)
        var index = limit - 1
        while (true) {
            decimalBuf[index] = digits!![integer % 10]
            integer /= 10
            if (index == 0 || integer == 0) break
            index--
        }
        var padding = minIntDigits - (limit - index)
        while (padding > 0) { decimalBuf[--index] = digits!![0]; padding-- }
        val length = limit - index
        output!!.appendRange(decimalBuf, index, index + length)
        position!!.setBeginIndex(0)
        position.setEndIndex(if (position.getField() == INTEGER_FIELD) length else 0)
        return output
    }
    override fun format(number: BigInteger?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder =
        throw UnsupportedOperationException("StringBuffer format(BigInteger, StringBuffer, FieldPosition) is not implemented")
    override fun format(number: BigDecimal?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder =
        throw UnsupportedOperationException("StringBuffer format(BigDecimal, StringBuffer, FieldPosition) is not implemented")
    override fun format(number: IcuBigDecimal?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder =
        throw UnsupportedOperationException("StringBuffer format(BigDecimal, StringBuffer, FieldPosition) is not implemented")
    override fun parse(text: String?, position: ParsePosition?): Number? {
        var number = 0L
        var sawNumber = false
        var negative = false
        val base = position!!.index
        var offset = 0
        while (base + offset < text!!.length) {
            val ch = text[base + offset]
            if (offset == 0 && ch == minusSign) {
                if (positiveOnly) break
                negative = true
            } else {
                var digit = ch - digits!![0]
                if (digit !in 0..9) digit = UCharacter.digit(ch.code)
                if (digit !in 0..9) {
                    digit = 0
                    while (digit < 10) { if (ch == digits!![digit]) break; digit++ }
                }
                if (digit in 0..9 && number < PARSE_THRESHOLD) { sawNumber = true; number = number * 10 + digit }
                else break
            }
            offset++
        }
        if (!sawNumber) return null
        if (negative) number *= -1
        position.index = base + offset
        return number
    }
    override fun equals(other: Any?): Boolean {
        if (other == null || !super.equals(other) || other !is DateNumberFormat) return false
        return maxIntDigits == other.maxIntDigits && minIntDigits == other.minIntDigits && minusSign == other.minusSign &&
            positiveOnly == other.positiveOnly && (digits === other.digits ||
            (digits != null && other.digits != null && digits!!.contentEquals(other.digits!!)))
    }
    override fun hashCode(): Int = super.hashCode()
    private fun readObject(input: PluralObjectInputStream) {
        dateNumberFormatReadFields(input)
        if (digits == null) setZeroDigit(zeroDigit)
        decimalBuf = CharArray(DECIMAL_BUF_SIZE)
    }
    override fun clone(): DateNumberFormat {
        val copy = cloneHost() as? DateNumberFormat ?: DateNumberFormat(this)
        copy.digits = digits!!.copyOf()
        copy.decimalBuf = CharArray(DECIMAL_BUF_SIZE)
        return copy
    }
    companion object {
        private const val serialVersionUID = -6315692826916346953L
        private const val DECIMAL_BUF_SIZE = 20
        private const val PARSE_THRESHOLD = 922337203685477579L
        private val CACHE = SimpleCache<ULocale?, CharArray>()
    }
}
internal expect fun dateNumberFormatReadFields(input: PluralObjectInputStream)
