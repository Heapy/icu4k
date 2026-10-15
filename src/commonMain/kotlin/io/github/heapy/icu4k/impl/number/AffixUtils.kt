// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.jvm.JvmStatic

/** Decimal-format affix tokenization, quoting and locale-symbol substitution. */
open class AffixUtils {
    fun interface SymbolProvider { fun getSymbol(type: Int): CharSequence? }
    fun interface TokenConsumer { fun consumeToken(typeOrCp: Int) }
    companion object {
        private const val BASE = 0
        private const val FIRST_QUOTE = 1
        private const val INSIDE_QUOTE = 2
        private const val AFTER_QUOTE = 3
        private const val FIRST_CURR = 4
        private const val SECOND_CURR = 5
        private const val THIRD_CURR = 6
        private const val FOURTH_CURR = 7
        private const val FIFTH_CURR = 8
        private const val OVERFLOW_CURR = 9
        const val TYPE_MINUS_SIGN = -1
        const val TYPE_PLUS_SIGN = -2
        const val TYPE_APPROXIMATELY_SIGN = -3
        const val TYPE_PERCENT = -4
        const val TYPE_PERMILLE = -5
        const val TYPE_CURRENCY_SINGLE = -6
        const val TYPE_CURRENCY_DOUBLE = -7
        const val TYPE_CURRENCY_TRIPLE = -8
        const val TYPE_CURRENCY_QUAD = -9
        const val TYPE_CURRENCY_QUINT = -10
        const val TYPE_CURRENCY_OVERFLOW = -15

        @JvmStatic fun estimateLength(patternString: CharSequence?): Int {
            if (patternString == null) return 0
            var state = BASE
            var offset = 0
            var length = 0
            while (offset < patternString.length) {
                val cp = codePointAt(patternString, offset)
                when (state) {
                    BASE -> if (cp == 39) state = FIRST_QUOTE else length++
                    FIRST_QUOTE -> { length++; state = if (cp == 39) BASE else INSIDE_QUOTE }
                    INSIDE_QUOTE -> if (cp == 39) state = AFTER_QUOTE else length++
                    AFTER_QUOTE -> if (cp == 39) { length++; state = INSIDE_QUOTE } else length++
                    else -> throw AssertionError()
                }
                offset += charCount(cp)
            }
            if (state == FIRST_QUOTE || state == INSIDE_QUOTE) throw IllegalArgumentException("Unterminated quote: \"$patternString\"")
            return length
        }

        @JvmStatic fun escape(input: CharSequence?, output: StringBuilder?): Int {
            if (input == null) return 0
            var state = BASE
            var offset = 0
            val startLength = output!!.length
            while (offset < input.length) {
                val cp = codePointAt(input, offset)
                when (cp) {
                    39 -> output.append("''")
                    45, 43, 37, 0x2030, 0xa4 -> {
                        if (state == BASE) { output.append('\''); state = INSIDE_QUOTE }
                        UTF16.appendCodePoint(output, cp)
                    }
                    else -> {
                        if (state == INSIDE_QUOTE) { output.append('\''); state = BASE }
                        UTF16.appendCodePoint(output, cp)
                    }
                }
                offset += charCount(cp)
            }
            if (state == INSIDE_QUOTE) output.append('\'')
            return output.length - startLength
        }
        @JvmStatic fun escape(input: CharSequence?): String? {
            if (input == null) return null
            return StringBuilder().also { escape(input, it) }.toString()
        }

        @JvmStatic fun getFieldForType(type: Int): NumberFormatField = when (type) {
            TYPE_MINUS_SIGN, TYPE_PLUS_SIGN -> NumberFormatField.SIGN
            TYPE_APPROXIMATELY_SIGN -> NumberFormatField.APPROXIMATELY_SIGN
            TYPE_PERCENT -> NumberFormatField.PERCENT
            TYPE_PERMILLE -> NumberFormatField.PERMILLE
            TYPE_CURRENCY_SINGLE, TYPE_CURRENCY_DOUBLE, TYPE_CURRENCY_TRIPLE,
            TYPE_CURRENCY_QUAD, TYPE_CURRENCY_QUINT, TYPE_CURRENCY_OVERFLOW -> NumberFormatField.CURRENCY
            else -> throw AssertionError()
        }

        @JvmStatic fun unescape(affixPattern: CharSequence?, output: FormattedStringBuilder?, position: Int,
                                provider: SymbolProvider?, field: NumberFormatField?): Int {
            assertAffixUtils(affixPattern != null)
            var length = 0
            var tag = 0L
            while (hasNext(tag, affixPattern)) {
                tag = nextToken(tag, affixPattern)
                val type = getTypeOrCp(tag)
                length += when {
                    type == TYPE_CURRENCY_OVERFLOW -> output!!.insertCodePoint(position + length, 0xfffd, NumberFormatField.CURRENCY)
                    type < 0 -> {
                        // Java evaluates provider callbacks before dispatching insert on output.
                        val symbol = provider!!.getSymbol(type)
                        val symbolField = getFieldForType(type)
                        output!!.insert(position + length, symbol!!, symbolField)
                    }
                    else -> output!!.insertCodePoint(position + length, type, field)
                }
            }
            return length
        }
        @JvmStatic fun unescapedCount(affixPattern: CharSequence?, lengthOrCount: Boolean, provider: SymbolProvider?): Int {
            var length = 0
            var tag = 0L
            while (hasNext(tag, affixPattern)) {
                tag = nextToken(tag, affixPattern)
                val type = getTypeOrCp(tag)
                length += when {
                    type == TYPE_CURRENCY_OVERFLOW -> 1
                    type < 0 -> {
                        val symbol = provider!!.getSymbol(type)!!
                        if (lengthOrCount) symbol.length else symbolCodePointCount(symbol)
                    }
                    else -> if (lengthOrCount) charCount(type) else 1
                }
            }
            return length
        }
        @JvmStatic fun containsType(affixPattern: CharSequence?, type: Int): Boolean {
            if (affixPattern == null || affixPattern.isEmpty()) return false
            var tag = 0L
            while (hasNext(tag, affixPattern)) {
                tag = nextToken(tag, affixPattern)
                if (getTypeOrCp(tag) == type) return true
            }
            return false
        }
        @JvmStatic fun hasCurrencySymbols(affixPattern: CharSequence?): Boolean {
            if (affixPattern == null || affixPattern.isEmpty()) return false
            var tag = 0L
            while (hasNext(tag, affixPattern)) {
                tag = nextToken(tag, affixPattern)
                val type = getTypeOrCp(tag)
                if (type < 0 && getFieldForType(type) === NumberFormatField.CURRENCY) return true
            }
            return false
        }
        @JvmStatic fun replaceType(affixPattern: CharSequence?, type: Int, replacementChar: Char): String {
            if (affixPattern == null || affixPattern.isEmpty()) return ""
            val chars = affixPattern.toString().toCharArray()
            var tag = 0L
            while (hasNext(tag, affixPattern)) {
                tag = nextToken(tag, affixPattern)
                if (getTypeOrCp(tag) == type) chars[getOffset(tag) - 1] = replacementChar
            }
            return chars.concatToString()
        }
        @JvmStatic fun containsOnlySymbolsAndIgnorables(affixPattern: CharSequence?, ignorables: UnicodeSet?): Boolean {
            if (affixPattern == null) return true
            var tag = 0L
            while (hasNext(tag, affixPattern)) {
                tag = nextToken(tag, affixPattern)
                val type = getTypeOrCp(tag)
                if (type >= 0 && !ignorables!!.contains(type)) return false
            }
            return true
        }
        @JvmStatic fun iterateWithConsumer(affixPattern: CharSequence?, consumer: TokenConsumer?) {
            assertAffixUtils(affixPattern != null)
            var tag = 0L
            while (hasNext(tag, affixPattern)) {
                tag = nextToken(tag, affixPattern)
                consumer!!.consumeToken(getTypeOrCp(tag))
            }
        }

        private fun nextToken(tag: Long, pattern: CharSequence?): Long {
            var offset = getOffset(tag)
            var state = getState(tag)
            while (offset < pattern!!.length) {
                val cp = codePointAt(pattern, offset)
                val count = charCount(cp)
                when (state) {
                    BASE -> when (cp) {
                        39 -> { state = FIRST_QUOTE; offset += count }
                        45 -> return makeTag(offset + count, TYPE_MINUS_SIGN, BASE, 0)
                        43 -> return makeTag(offset + count, TYPE_PLUS_SIGN, BASE, 0)
                        126 -> return makeTag(offset + count, TYPE_APPROXIMATELY_SIGN, BASE, 0)
                        37 -> return makeTag(offset + count, TYPE_PERCENT, BASE, 0)
                        0x2030 -> return makeTag(offset + count, TYPE_PERMILLE, BASE, 0)
                        0xa4 -> { state = FIRST_CURR; offset += count }
                        else -> return makeTag(offset + count, 0, BASE, cp)
                    }
                    FIRST_QUOTE -> return makeTag(offset + count, 0, if (cp == 39) BASE else INSIDE_QUOTE, cp)
                    INSIDE_QUOTE -> if (cp == 39) { state = AFTER_QUOTE; offset += count } else return makeTag(offset + count, 0, INSIDE_QUOTE, cp)
                    AFTER_QUOTE -> if (cp == 39) return makeTag(offset + count, 0, INSIDE_QUOTE, cp) else state = BASE
                    FIRST_CURR, SECOND_CURR, THIRD_CURR, FOURTH_CURR, FIFTH_CURR -> {
                        if (cp == 0xa4) { state++; offset += count }
                        else return makeTag(offset, currencyType(state), BASE, 0)
                    }
                    OVERFLOW_CURR -> if (cp == 0xa4) offset += count else return makeTag(offset, TYPE_CURRENCY_OVERFLOW, BASE, 0)
                    else -> throw AssertionError()
                }
            }
            return when (state) {
                BASE, AFTER_QUOTE -> -1L
                FIRST_QUOTE, INSIDE_QUOTE -> throw IllegalArgumentException("Unterminated quote in pattern affix: \"$pattern\"")
                FIRST_CURR, SECOND_CURR, THIRD_CURR, FOURTH_CURR, FIFTH_CURR, OVERFLOW_CURR -> makeTag(offset, currencyType(state), BASE, 0)
                else -> throw AssertionError()
            }
        }
        private fun currencyType(state: Int): Int = if (state == OVERFLOW_CURR) TYPE_CURRENCY_OVERFLOW else TYPE_CURRENCY_SINGLE - (state - FIRST_CURR)
        private fun hasNext(tag: Long, pattern: CharSequence?): Boolean {
            assertAffixUtils(tag >= 0)
            val state = getState(tag)
            val offset = getOffset(tag)
            return if (state == INSIDE_QUOTE && offset == pattern!!.length - 1 && pattern[offset] == '\'') false
            else if (state != BASE) true else offset < pattern!!.length
        }
        private fun getTypeOrCp(tag: Long): Int {
            assertAffixUtils(tag >= 0)
            val type = ((tag ushr 32) and 0xf).toInt()
            return if (type == 0) (tag ushr 40).toInt() else -type
        }
        private fun makeTag(offset: Int, type: Int, state: Int, cp: Int): Long {
            val tag = offset.toLong() or (-type.toLong() shl 32) or (state.toLong() shl 36) or (cp.toLong() shl 40)
            assertAffixUtils(tag >= 0)
            return tag
        }
        private fun getOffset(tag: Long): Int = (tag and 0xffffffffL).toInt()
        private fun getState(tag: Long): Int = ((tag ushr 36) and 0xf).toInt()
        private fun charCount(cp: Int): Int = if (cp >= 0x10000) 2 else 1
        private fun codePointAt(text: CharSequence, offset: Int): Int {
            val first = text[offset]
            if (first.isHighSurrogate() && offset + 1 < text.length) {
                val second = text[offset + 1]
                if (second.isLowSurrogate()) return 0x10000 + ((first.code - 0xd800) shl 10) + second.code - 0xdc00
            }
            return first.code
        }
        private fun symbolCodePointCount(text: CharSequence): Int {
            val end = text.length
            if (end > text.length || end < 0) throw IndexOutOfBoundsException()
            var offset = 0
            var count = 0
            while (offset < end) {
                val first = text[offset++]
                if (first.isHighSurrogate() && offset < end && text[offset].isLowSurrogate()) offset++
                count++
            }
            return count
        }
    }
}

internal expect fun assertAffixUtils(condition: Boolean)
