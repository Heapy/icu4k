// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.number.Modifier.Signum
import io.github.heapy.icu4k.impl.number.Padder.PadPosition
import io.github.heapy.icu4k.impl.number.PatternStringParserCore.PatternProperties
import io.github.heapy.icu4k.impl.units.UnitDecimal

/** Pattern algorithms; public property, currency-plural and symbol adapters are separate. */
internal object PatternStringUtilsCore {
    enum class PatternSignType { POS, POS_SIGN, NEG }
    enum class SignDisplay { AUTO, ALWAYS, NEVER, ACCOUNTING, ACCOUNTING_ALWAYS, EXCEPT_ZERO, ACCOUNTING_EXCEPT_ZERO, NEGATIVE, ACCOUNTING_NEGATIVE }

    fun ignoreRoundingIncrement(rounding: UnitDecimal?, maxFrac: Int): Boolean {
        var value = rounding!!.toString().toDouble()
        if (value == 0.0) return true
        if (maxFrac < 0) return false
        value *= 2.0
        var frac = 0
        while (frac <= maxFrac && value <= 1.0) { frac++; value *= 10.0 }
        return frac > maxFrac
    }
    fun propertiesToPatternString(p: PatternProperties, affixes: AffixPatternProvider): String {
        val sb = StringBuilder()
        val limit = 100
        var grouping1 = p.groupingSize.coerceIn(0, limit)
        var grouping2 = p.secondaryGroupingSize.coerceIn(0, limit)
        val useGrouping = p.groupingUsed
        val paddingWidth = minOf(p.formatWidth, limit)
        val paddingLocation = p.padPosition
        val paddingString = p.padString
        val minInt = p.minimumIntegerDigits.coerceIn(0, limit)
        val maxInt = minOf(p.maximumIntegerDigits, limit)
        val minFrac = p.minimumFractionDigits.coerceIn(0, limit)
        val maxFrac = minOf(p.maximumFractionDigits, limit)
        val minSig = minOf(p.minimumSignificantDigits, limit)
        val maxSig = minOf(p.maximumSignificantDigits, limit)
        val alwaysShowDecimal = p.decimalSeparatorAlwaysShown
        val currencyAsDecimal = p.currencyAsDecimal
        val exponentDigits = minOf(p.minimumExponentDigits, limit)
        val exponentShowPlus = p.exponentSignAlwaysShown
        sb.append(affixes.getString(AffixPatternProvider.FLAG_POS_PREFIX))
        var afterPrefix = sb.length
        if (!useGrouping) { grouping1 = 0; grouping2 = 0 } else if (grouping1 == grouping2) grouping1 = 0
        val groupingLength = grouping1 + grouping2 + 1
        val digits = StringBuilder()
        var scale = 0
        val increment = p.roundingIncrement
        if (maxSig != -1) {
            while (digits.length < minSig) digits.append('@')
            while (digits.length < maxSig) digits.append('#')
        } else if (increment != null && !ignoreRoundingIncrement(increment, maxFrac)) {
            scale = -increment.scale
            // scaleByPowerOfTen(scale).toPlainString() is the unsigned coefficient here.
            digits.append(increment.magnitude)
        }
        while (digits.length + scale < minInt) digits.insert(0, '0')
        while (-scale < minFrac) { digits.append('0'); scale-- }
        var first = maxOf(groupingLength, digits.length + scale)
        first = if (maxInt != limit) maxOf(maxInt, first) - 1 else first - 1
        val last = if (maxFrac != limit) minOf(-maxFrac, scale) else scale
        for (magnitude in first downTo last) {
            val index = digits.length + scale - magnitude - 1
            sb.append(if (index < 0 || index >= digits.length) '#' else digits[index])
            if (magnitude == 0 && (alwaysShowDecimal || last < 0)) sb.append(if (currencyAsDecimal) '¤' else '.')
            if (!useGrouping) continue
            if (magnitude > 0 && magnitude == grouping1) sb.append(',')
            if (magnitude > grouping1 && grouping2 > 0 && (magnitude - grouping1) % grouping2 == 0) sb.append(',')
        }
        if (exponentDigits != -1) {
            sb.append('E')
            if (exponentShowPlus) sb.append('+')
            for (i in 0 until exponentDigits) sb.append('0')
        }
        var beforeSuffix = sb.length
        sb.append(affixes.getString(AffixPatternProvider.FLAG_POS_SUFFIX))
        if (paddingWidth > 0) {
            while (paddingWidth - sb.length > 0) { sb.insert(afterPrefix, '#'); beforeSuffix++ }
            when (paddingLocation!!) {
                PadPosition.BEFORE_PREFIX -> {
                    val added = escapePaddingString(paddingString, sb, 0)
                    sb.insert(0, '*'); afterPrefix += added + 1; beforeSuffix += added + 1
                }
                PadPosition.AFTER_PREFIX -> {
                    val added = escapePaddingString(paddingString, sb, afterPrefix)
                    sb.insert(afterPrefix, '*'); afterPrefix += added + 1; beforeSuffix += added + 1
                }
                PadPosition.BEFORE_SUFFIX -> { escapePaddingString(paddingString, sb, beforeSuffix); sb.insert(beforeSuffix, '*') }
                PadPosition.AFTER_SUFFIX -> { sb.append('*'); escapePaddingString(paddingString, sb, sb.length) }
            }
        }
        if (affixes.hasNegativeSubpattern()) {
            sb.append(';'); sb.append(affixes.getString(AffixPatternProvider.FLAG_NEG_PREFIX))
            sb.append(sb, afterPrefix, beforeSuffix)
            sb.append(affixes.getString(AffixPatternProvider.FLAG_NEG_SUFFIX))
        }
        return sb.toString()
    }
    private fun escapePaddingString(raw: CharSequence?, output: StringBuilder, startIndex: Int): Int {
        val input = if (raw == null || raw.isEmpty()) Padder.FALLBACK_PADDING_STRING else raw
        val startLength = output.length
        if (input.length == 1) output.insert(startIndex, if (input.equals("'")) "''" else input)
        else {
            output.insert(startIndex, '\'')
            var offset = 1
            var i = 0
            while (i < input.length) {
                val c = input[i++]
                if (c == '\'') { output.insert(startIndex + offset, "''"); offset += 2 }
                else { output.insert(startIndex + offset, c); offset++ }
            }
            output.insert(startIndex + offset, '\'')
        }
        return output.length - startLength
    }

    /** Symbols are read in the same 21-row order as DecimalFormatSymbols in the source. */
    fun convertLocalized(input: String?, localSymbol: ((Int) -> String)?, toLocalized: Boolean): String? {
        if (input == null) return null
        val standard = listOf("%", "‰", ".", ",", "-", "+", ";", "@", "E", "*", "#") + (0..9).map { it.toString() }
        val table = Array(21) { i ->
            val local = localSymbol!!(i)
            if (toLocalized) arrayOf(standard[i], local) else arrayOf(local, standard[i])
        }
        val localIndex = if (toLocalized) 1 else 0
        for (row in table) row[localIndex] = row[localIndex].replace('\'', '’')
        val result = StringBuilder()
        var state = 0
        var offset = 0
        outer@ while (offset < input.length) {
            val c = input[offset]
            if (c == '\'') {
                when (state) {
                    0 -> { result.append('\''); state = 1 }
                    1 -> { result.append('\''); state = 0 }
                    2 -> state = 3
                    3 -> { result.append("''"); state = 1 }
                    4 -> state = 5
                    else -> { assertPatternStringUtils(state == 5); result.append("''"); state = 4 }
                }
                offset++; continue
            }
            if (state == 0 || state == 3 || state == 4) {
                for (pair in table) if (input.regionMatches(offset, pair[0], 0, pair[0].length)) {
                    offset += pair[0].length
                    if (state == 3 || state == 4) { result.append('\''); state = 0 }
                    result.append(pair[1]); continue@outer
                }
                for (pair in table) if (input.regionMatches(offset, pair[1], 0, pair[1].length)) {
                    if (state == 0) { result.append('\''); state = 4 }
                    result.append(c); offset++; continue@outer
                }
                if (state == 3 || state == 4) { result.append('\''); state = 0 }
                result.append(c)
            } else {
                assertPatternStringUtils(state == 1 || state == 2 || state == 5)
                result.append(c); state = 2
            }
            offset++
        }
        if (state == 3 || state == 4) { result.append('\''); state = 0 }
        require(state == 0) { "Malformed localized pattern: unterminated quote" }
        return result.toString()
    }
    fun patternInfoToStringBuilder(info: AffixPatternProvider?, isPrefix: Boolean, sign: PatternSignType?, approximately: Boolean,
                                   plural: StandardPlural?, perMille: Boolean, output: StringBuilder?) {
        val plusReplacesMinus = sign == PatternSignType.POS_SIGN && !info!!.positiveHasPlusSign()
        val useNegative = info!!.hasNegativeSubpattern() &&
            (sign == PatternSignType.NEG || (info.negativeHasMinusSign() && (plusReplacesMinus || approximately)))
        var flags = if (useNegative) AffixPatternProvider.Flags.NEGATIVE_SUBPATTERN else 0
        if (isPrefix) flags = flags or AffixPatternProvider.Flags.PREFIX
        if (plural != null) {
            assertPatternStringUtils(plural.ordinal == (AffixPatternProvider.Flags.PLURAL_MASK and plural.ordinal))
            flags = flags or plural.ordinal
        }
        val prepend = isPrefix && !useNegative && (sign == PatternSignType.NEG || plusReplacesMinus || approximately)
        val symbols = if (approximately) {
            if (plusReplacesMinus) "~+" else if (sign == PatternSignType.NEG) "~-" else "~"
        } else if (plusReplacesMinus) "+" else "-"
        val length = info.length(flags) + if (prepend) 1 else 0
        output!!.setLength(0)
        for (index in 0 until length) {
            var candidate = if (prepend && index == 0) '-' else info.charAt(flags, if (prepend) index - 1 else index)
            if (candidate == '-') {
                if (symbols.length > 1) output.append(symbols[0])
                candidate = symbols.last()
            }
            if (perMille && candidate == '%') candidate = '‰'
            output.append(candidate)
        }
    }
    fun resolveSignDisplay(display: SignDisplay?, signum: Signum?): PatternSignType = when (display!!) {
        SignDisplay.NEVER -> PatternSignType.POS
        SignDisplay.AUTO, SignDisplay.ACCOUNTING -> when (signum!!) {
            Signum.NEG, Signum.NEG_ZERO -> PatternSignType.NEG
            else -> PatternSignType.POS
        }
        SignDisplay.ALWAYS, SignDisplay.ACCOUNTING_ALWAYS -> when (signum!!) {
            Signum.NEG, Signum.NEG_ZERO -> PatternSignType.NEG
            else -> PatternSignType.POS_SIGN
        }
        SignDisplay.EXCEPT_ZERO, SignDisplay.ACCOUNTING_EXCEPT_ZERO -> when (signum!!) {
            Signum.NEG -> PatternSignType.NEG
            Signum.POS -> PatternSignType.POS_SIGN
            else -> PatternSignType.POS
        }
        SignDisplay.NEGATIVE, SignDisplay.ACCOUNTING_NEGATIVE -> if (signum!! == Signum.NEG) PatternSignType.NEG else PatternSignType.POS
    }
}

internal expect fun assertPatternStringUtils(condition: Boolean)
