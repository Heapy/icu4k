// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.MicroProps
import io.github.heapy.icu4k.text.NumberFormatField as Field

/** Output stage extracted from ICU4J NumberFormatterImpl, shared by the pending pipeline assembly. */
internal object NumberFormatterOutput {
    fun writeAffixes(micros: MicroProps?, string: FormattedStringBuilder?, start: Int, end: Int): Int {
        var length = micros!!.modInner!!.apply(string, start, end)
        if (micros.padding!!.isValid()) {
            // Source deliberately excludes the padding and outer modifiers from this return value.
            micros.padding!!.padAndApply(micros.modMiddle, micros.modOuter, string, start, end + length)
        } else {
            length += micros.modMiddle!!.apply(string, start, end + length)
            length += micros.modOuter!!.apply(string, start, end + length)
        }
        return length
    }

    fun writeNumber(micros: MicroProps?, quantity: DecimalQuantity?, string: FormattedStringBuilder?, index: Int): Int {
        var length = 0
        if (quantity!!.isInfinite()) {
            val symbol = micros!!.symbols!!.getInfinity()
            length += string!!.insert(length + index, symbol!!, Field.INTEGER)
        } else if (quantity.isNaN()) {
            val symbol = micros!!.symbols!!.getNaN()
            length += string!!.insert(length + index, symbol!!, Field.INTEGER)
        } else {
            length += writeIntegerDigits(micros, quantity, string, length + index)
            if (quantity.getLowerDisplayMagnitude() < 0 || micros!!.decimal == DecimalSeparatorDisplay.ALWAYS) {
                val field: Field
                val separator: String?
                if (micros!!.currencyAsDecimal != null) {
                    separator = micros.currencyAsDecimal
                    field = Field.CURRENCY
                } else if (micros.useCurrency) {
                    separator = micros.symbols!!.getMonetaryDecimalSeparatorString()
                    field = Field.DECIMAL_SEPARATOR
                } else {
                    separator = micros.symbols!!.getDecimalSeparatorString()
                    field = Field.DECIMAL_SEPARATOR
                }
                length += string!!.insert(length + index, separator!!, field)
            }
            length += writeFractionDigits(micros, quantity, string, length + index)
            if (length == 0) {
                if (micros!!.symbols!!.getCodePointZero() != -1) {
                    val zero = micros.symbols!!.getCodePointZero()
                    length += string!!.insertCodePoint(index, zero, Field.INTEGER)
                } else {
                    val zero = micros.symbols!!.getDigitStringsLocal()[0]
                    length += string!!.insert(index, zero, Field.INTEGER)
                }
            }
        }
        return length
    }

    private fun writeIntegerDigits(micros: MicroProps?, quantity: DecimalQuantity, string: FormattedStringBuilder?, index: Int): Int {
        var length = 0
        val integerCount = quantity.getUpperDisplayMagnitude() + 1
        var i = 0
        while (i < integerCount) {
            if (micros!!.grouping!!.groupAtPosition(i, quantity)) {
                val separator = if (micros.useCurrency) micros.symbols!!.getMonetaryGroupingSeparatorString()
                    else micros.symbols!!.getGroupingSeparatorString()
                length += string!!.insert(index, separator, Field.GROUPING_SEPARATOR)
            }
            val digit = quantity.getDigit(i)
            if (micros.symbols!!.getCodePointZero() != -1) {
                val codePoint = micros.symbols!!.getCodePointZero() + digit
                length += string!!.insertCodePoint(index, codePoint, Field.INTEGER)
            } else {
                val text = micros.symbols!!.getDigitStringsLocal()[digit.toInt()]
                length += string!!.insert(index, text, Field.INTEGER)
            }
            i++
        }
        return length
    }

    private fun writeFractionDigits(micros: MicroProps?, quantity: DecimalQuantity, string: FormattedStringBuilder?, index: Int): Int {
        var length = 0
        val fractionCount = -quantity.getLowerDisplayMagnitude()
        var i = 0
        while (i < fractionCount) {
            val digit = quantity.getDigit(-i - 1)
            if (micros!!.symbols!!.getCodePointZero() != -1) {
                val codePoint = micros.symbols!!.getCodePointZero() + digit
                length += string!!.insertCodePoint(length + index, codePoint, Field.FRACTION)
            } else {
                val text = micros.symbols!!.getDigitStringsLocal()[digit.toInt()]
                length += string!!.insert(length + index, text, Field.FRACTION)
            }
            i++
        }
        return length
    }
}
