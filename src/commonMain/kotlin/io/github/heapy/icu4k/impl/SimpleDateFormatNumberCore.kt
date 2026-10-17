// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*

/** SimpleDateFormat's local integer fast path, including its mutable digit buffer. */
internal class SimpleDateFormatNumberCore {
    var useLocalZeroPaddingNumberFormat = false
        private set
    private var decDigits: CharArray? = null
    private var decimalBuf: CharArray? = null

    fun initialize(numberFormat: NumberFormat?) {
        when (numberFormat) {
            is DecimalFormat -> {
                val digits = numberFormat.getDecimalFormatSymbols().getDigitStringsLocal()
                useLocalZeroPaddingNumberFormat = true
                decDigits = CharArray(10)
                for (i in 0..9) {
                    if (digits[i].length > 1) {
                        useLocalZeroPaddingNumberFormat = false
                        break
                    }
                    decDigits!![i] = digits[i][0]
                }
            }
            is DateNumberFormat -> {
                decDigits = numberFormat.getDigits()
                useLocalZeroPaddingNumberFormat = true
            }
            else -> useLocalZeroPaddingNumberFormat = false
        }
        if (useLocalZeroPaddingNumberFormat) decimalBuf = CharArray(10)
    }

    fun disableLocalFormat() { useLocalZeroPaddingNumberFormat = false }

    fun format(nf: NumberFormat?, buf: StringBuilder?, value: Int, minDigits: Int, maxDigits: Int) {
        if (useLocalZeroPaddingNumberFormat && value >= 0) fastFormat(buf, value, minDigits, maxDigits)
        else {
            nf!!.setMinimumIntegerDigits(minDigits)
            nf.setMaximumIntegerDigits(maxDigits)
            nf.format(value.toLong(), buf, FormatFieldPosition(-1))
        }
    }

    private fun fastFormat(buf: StringBuilder?, input: Int, minDigits: Int, maxDigits: Int) {
        val buffer = decimalBuf!!
        val digits = decDigits!!
        val limit = minOf(buffer.size, maxDigits)
        var index = limit - 1
        var value = input
        while (true) {
            if (index !in buffer.indices) throw IndexOutOfBoundsException()
            buffer[index] = digits[value % 10]
            value /= 10
            if (index == 0 || value == 0) break
            index--
        }
        var padding = minDigits - (limit - index)
        while (padding > 0 && index > 0) {
            buffer[--index] = digits[0]
            padding--
        }
        while (padding > 0) {
            buf!!.append(digits[0])
            padding--
        }
        buf!!.appendRange(buffer, index, limit)
    }

    fun copyStateFrom(other: SimpleDateFormatNumberCore) {
        useLocalZeroPaddingNumberFormat = other.useLocalZeroPaddingNumberFormat
        decDigits = other.decDigits
        decimalBuf = if (other.decimalBuf != null) CharArray(10) else null
    }
}
