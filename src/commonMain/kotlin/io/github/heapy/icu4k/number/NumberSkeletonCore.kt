// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.PatternProps
import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.impl.stringSegmentCharCount
import io.github.heapy.icu4k.impl.number.MacroProps
import io.github.heapy.icu4k.impl.number.RoundingUtils
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberingSystem
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.util.Currency.CurrencyUsage

/** ICU skeleton parser and canonical generator, before the formatter cache/facade binding. */
internal object NumberSkeletonCore {
    private enum class State { NULL, SCIENTIFIC, FRACTION_PRECISION, PRECISION, INCREMENT_PRECISION, MEASURE_UNIT, PER_MEASURE_UNIT, IDENTIFIER_UNIT, UNIT_USAGE, CURRENCY_UNIT, INTEGER_WIDTH, NUMBERING_SYSTEM, SCALE }
    private enum class Stem { COMPACT_SHORT, COMPACT_LONG, SCIENTIFIC, ENGINEERING, NOTATION_SIMPLE, BASE_UNIT, PERCENT, PERMILLE, PERCENT_100, PRECISION_INTEGER, PRECISION_UNLIMITED, PRECISION_CURRENCY_STANDARD, PRECISION_CURRENCY_CASH, ROUNDING_MODE_CEILING, ROUNDING_MODE_FLOOR, ROUNDING_MODE_DOWN, ROUNDING_MODE_UP, ROUNDING_MODE_HALF_EVEN, ROUNDING_MODE_HALF_DOWN, ROUNDING_MODE_HALF_UP, ROUNDING_MODE_UNNECESSARY, INTEGER_WIDTH_TRUNC, GROUP_OFF, GROUP_MIN2, GROUP_AUTO, GROUP_ON_ALIGNED, GROUP_THOUSANDS, LATIN, UNIT_WIDTH_NARROW, UNIT_WIDTH_SHORT, UNIT_WIDTH_FULL_NAME, UNIT_WIDTH_ISO_CODE, UNIT_WIDTH_FORMAL, UNIT_WIDTH_VARIANT, UNIT_WIDTH_HIDDEN, SIGN_AUTO, SIGN_ALWAYS, SIGN_NEVER, SIGN_ACCOUNTING, SIGN_ACCOUNTING_ALWAYS, SIGN_EXCEPT_ZERO, SIGN_ACCOUNTING_EXCEPT_ZERO, SIGN_NEGATIVE, SIGN_ACCOUNTING_NEGATIVE, DECIMAL_AUTO, DECIMAL_ALWAYS, PRECISION_INCREMENT, MEASURE_UNIT, PER_MEASURE_UNIT, UNIT, UNIT_USAGE, CURRENCY, INTEGER_WIDTH, NUMBERING_SYSTEM, SCALE }
    private val serializedStems = CharsTrieBuilder().also { builder ->
        builder.add("compact-short", Stem.COMPACT_SHORT.ordinal)
        builder.add("compact-long", Stem.COMPACT_LONG.ordinal)
        builder.add("scientific", Stem.SCIENTIFIC.ordinal)
        builder.add("engineering", Stem.ENGINEERING.ordinal)
        builder.add("notation-simple", Stem.NOTATION_SIMPLE.ordinal)
        builder.add("base-unit", Stem.BASE_UNIT.ordinal)
        builder.add("percent", Stem.PERCENT.ordinal)
        builder.add("permille", Stem.PERMILLE.ordinal)
        builder.add("precision-integer", Stem.PRECISION_INTEGER.ordinal)
        builder.add("precision-unlimited", Stem.PRECISION_UNLIMITED.ordinal)
        builder.add("precision-currency-standard", Stem.PRECISION_CURRENCY_STANDARD.ordinal)
        builder.add("precision-currency-cash", Stem.PRECISION_CURRENCY_CASH.ordinal)
        builder.add("rounding-mode-ceiling", Stem.ROUNDING_MODE_CEILING.ordinal)
        builder.add("rounding-mode-floor", Stem.ROUNDING_MODE_FLOOR.ordinal)
        builder.add("rounding-mode-down", Stem.ROUNDING_MODE_DOWN.ordinal)
        builder.add("rounding-mode-up", Stem.ROUNDING_MODE_UP.ordinal)
        builder.add("rounding-mode-half-even", Stem.ROUNDING_MODE_HALF_EVEN.ordinal)
        builder.add("rounding-mode-half-down", Stem.ROUNDING_MODE_HALF_DOWN.ordinal)
        builder.add("rounding-mode-half-up", Stem.ROUNDING_MODE_HALF_UP.ordinal)
        builder.add("rounding-mode-unnecessary", Stem.ROUNDING_MODE_UNNECESSARY.ordinal)
        builder.add("integer-width-trunc", Stem.INTEGER_WIDTH_TRUNC.ordinal)
        builder.add("group-off", Stem.GROUP_OFF.ordinal)
        builder.add("group-min2", Stem.GROUP_MIN2.ordinal)
        builder.add("group-auto", Stem.GROUP_AUTO.ordinal)
        builder.add("group-on-aligned", Stem.GROUP_ON_ALIGNED.ordinal)
        builder.add("group-thousands", Stem.GROUP_THOUSANDS.ordinal)
        builder.add("latin", Stem.LATIN.ordinal)
        builder.add("unit-width-narrow", Stem.UNIT_WIDTH_NARROW.ordinal)
        builder.add("unit-width-short", Stem.UNIT_WIDTH_SHORT.ordinal)
        builder.add("unit-width-full-name", Stem.UNIT_WIDTH_FULL_NAME.ordinal)
        builder.add("unit-width-iso-code", Stem.UNIT_WIDTH_ISO_CODE.ordinal)
        builder.add("unit-width-formal", Stem.UNIT_WIDTH_FORMAL.ordinal)
        builder.add("unit-width-variant", Stem.UNIT_WIDTH_VARIANT.ordinal)
        builder.add("unit-width-hidden", Stem.UNIT_WIDTH_HIDDEN.ordinal)
        builder.add("sign-auto", Stem.SIGN_AUTO.ordinal)
        builder.add("sign-always", Stem.SIGN_ALWAYS.ordinal)
        builder.add("sign-never", Stem.SIGN_NEVER.ordinal)
        builder.add("sign-accounting", Stem.SIGN_ACCOUNTING.ordinal)
        builder.add("sign-accounting-always", Stem.SIGN_ACCOUNTING_ALWAYS.ordinal)
        builder.add("sign-except-zero", Stem.SIGN_EXCEPT_ZERO.ordinal)
        builder.add("sign-accounting-except-zero", Stem.SIGN_ACCOUNTING_EXCEPT_ZERO.ordinal)
        builder.add("sign-negative", Stem.SIGN_NEGATIVE.ordinal)
        builder.add("sign-accounting-negative", Stem.SIGN_ACCOUNTING_NEGATIVE.ordinal)
        builder.add("decimal-auto", Stem.DECIMAL_AUTO.ordinal)
        builder.add("decimal-always", Stem.DECIMAL_ALWAYS.ordinal)
        builder.add("precision-increment", Stem.PRECISION_INCREMENT.ordinal)
        builder.add("measure-unit", Stem.MEASURE_UNIT.ordinal)
        builder.add("per-measure-unit", Stem.PER_MEASURE_UNIT.ordinal)
        builder.add("unit", Stem.UNIT.ordinal)
        builder.add("usage", Stem.UNIT_USAGE.ordinal)
        builder.add("currency", Stem.CURRENCY.ordinal)
        builder.add("integer-width", Stem.INTEGER_WIDTH.ordinal)
        builder.add("numbering-system", Stem.NUMBERING_SYSTEM.ordinal)
        builder.add("scale", Stem.SCALE.ordinal)
        builder.add("K", Stem.COMPACT_SHORT.ordinal)
        builder.add("KK", Stem.COMPACT_LONG.ordinal)
        builder.add("%", Stem.PERCENT.ordinal)
        builder.add("%x100", Stem.PERCENT_100.ordinal)
        builder.add(",_", Stem.GROUP_OFF.ordinal)
        builder.add(",?", Stem.GROUP_MIN2.ordinal)
        builder.add(",!", Stem.GROUP_ON_ALIGNED.ordinal)
        builder.add("+!", Stem.SIGN_ALWAYS.ordinal)
        builder.add("+_", Stem.SIGN_NEVER.ordinal)
        builder.add("()", Stem.SIGN_ACCOUNTING.ordinal)
        builder.add("()!", Stem.SIGN_ACCOUNTING_ALWAYS.ordinal)
        builder.add("+?", Stem.SIGN_EXCEPT_ZERO.ordinal)
        builder.add("()?", Stem.SIGN_ACCOUNTING_EXCEPT_ZERO.ordinal)
        builder.add("+-", Stem.SIGN_NEGATIVE.ordinal)
        builder.add("()-", Stem.SIGN_ACCOUNTING_NEGATIVE.ordinal)
    }.buildCharSequence(StringTrieBuilder.Option.FAST).toString()
    private fun wildcard(c: Char): Boolean = c == '*' || c == '+'
    private fun hasValue(result: BytesTrie.Result): Boolean = result == BytesTrie.Result.INTERMEDIATE_VALUE || result == BytesTrie.Result.FINAL_VALUE
    private fun checkNull(value: Any?, token: CharSequence) { if (value != null) throw SkeletonSyntaxException("Duplicated setting", token) }

    fun parseSkeleton(input: String?): MacroProps {
        val segment = StringSegment(input.toString() + " ", false)
        val macros = MacroProps()
        val trie = CharsTrie(serializedStems, 0)
        var state = State.NULL
        var offset = 0
        while (offset < segment.length) {
            val cp = segment.codePointAt(offset)
            val space = PatternProps.isWhiteSpace(cp)
            val slash = cp == '/'.code
            if (!space && !slash) {
                offset += stringSegmentCharCount(cp)
                if (state == State.NULL) trie.nextForCodePoint(cp)
                continue
            }
            if (offset != 0) {
                segment.setLength(offset)
                if (state == State.NULL) { state = parseStem(segment, trie, macros); trie.reset() }
                else state = parseOption(state, segment, macros)
                segment.resetLength()
                segment.adjustOffset(offset)
                offset = 0
            } else if (state != State.NULL) {
                segment.setLength(stringSegmentCharCount(cp))
                throw SkeletonSyntaxException("Unexpected separator character", segment)
            }
            if (slash && state == State.NULL) {
                segment.setLength(stringSegmentCharCount(cp))
                throw SkeletonSyntaxException("Unexpected option separator", segment)
            }
            if (space && state != State.NULL) {
                if (state.ordinal >= State.INCREMENT_PRECISION.ordinal) {
                    segment.setLength(stringSegmentCharCount(cp))
                    throw SkeletonSyntaxException("Stem requires an option", segment)
                }
                state = State.NULL
            }
            segment.adjustOffset(stringSegmentCharCount(cp))
        }
        assertNumberSkeleton { state == State.NULL }
        return macros
    }

    private fun parseStem(s: StringSegment, trie: CharsTrie, m: MacroProps): State {
        when (s[0]) {
            '.' -> { checkNull(m.precision, s); parseFraction(s, m); return State.FRACTION_PRECISION }
            '@' -> { checkNull(m.precision, s); parseDigits(s, m); return State.PRECISION }
            'E' -> { checkNull(m.notation, s); parseScientific(s, m); return State.NULL }
            '0' -> { checkNull(m.integerWidth, s); parseInteger(s, m); return State.NULL }
        }
        if (!hasValue(trie.current())) throw SkeletonSyntaxException("Unknown stem", s)
        val stem = Stem.entries[trie.getValue()]
        when (stem) {
            Stem.COMPACT_SHORT, Stem.COMPACT_LONG, Stem.SCIENTIFIC, Stem.ENGINEERING, Stem.NOTATION_SIMPLE -> {
                checkNull(m.notation, s)
                m.notation = when (stem) {
                    Stem.COMPACT_SHORT -> Notation.compactShort()
                    Stem.COMPACT_LONG -> Notation.compactLong()
                    Stem.SCIENTIFIC -> Notation.scientific()
                    Stem.ENGINEERING -> Notation.engineering()
                    else -> Notation.simple()
                }
                return if (stem == Stem.SCIENTIFIC || stem == Stem.ENGINEERING) State.SCIENTIFIC else State.NULL
            }
            Stem.BASE_UNIT, Stem.PERCENT, Stem.PERMILLE -> {
                checkNull(m.unit, s)
                m.unit = when (stem) { Stem.PERCENT -> NoUnit.PERCENT; Stem.PERMILLE -> NoUnit.PERMILLE; else -> NoUnit.BASE }
            }
            Stem.PERCENT_100 -> { checkNull(m.scale, s); checkNull(m.unit, s); m.scale = Scale.powerOfTen(2); m.unit = NoUnit.PERCENT }
            Stem.PRECISION_INTEGER, Stem.PRECISION_UNLIMITED, Stem.PRECISION_CURRENCY_STANDARD, Stem.PRECISION_CURRENCY_CASH -> {
                checkNull(m.precision, s)
                m.precision = when (stem) {
                    Stem.PRECISION_INTEGER -> Precision.integer()
                    Stem.PRECISION_UNLIMITED -> Precision.unlimited()
                    Stem.PRECISION_CURRENCY_STANDARD -> Precision.currency(CurrencyUsage.STANDARD)
                    else -> Precision.currency(CurrencyUsage.CASH)
                }
                return if (stem == Stem.PRECISION_INTEGER) State.FRACTION_PRECISION else State.PRECISION
            }
            Stem.INTEGER_WIDTH_TRUNC -> { checkNull(m.integerWidth, s); m.integerWidth = IntegerWidth.zeroFillTo(0).truncateAt(0) }
            Stem.LATIN -> { checkNull(m.symbols, s); m.symbols = NumberingSystem.LATIN }
            Stem.PRECISION_INCREMENT -> { checkNull(m.precision, s); return State.INCREMENT_PRECISION }
            Stem.MEASURE_UNIT -> { checkNull(m.unit, s); return State.MEASURE_UNIT }
            Stem.PER_MEASURE_UNIT -> {
                if (m.unit is Currency) throw SkeletonSyntaxException("Duplicated setting", s)
                checkNull(m.perUnit, s); return State.PER_MEASURE_UNIT
            }
            Stem.UNIT -> { checkNull(m.unit, s); checkNull(m.perUnit, s); return State.IDENTIFIER_UNIT }
            Stem.UNIT_USAGE -> { checkNull(m.usage, s); return State.UNIT_USAGE }
            Stem.CURRENCY -> { checkNull(m.unit, s); checkNull(m.perUnit, s); return State.CURRENCY_UNIT }
            Stem.INTEGER_WIDTH -> { checkNull(m.integerWidth, s); return State.INTEGER_WIDTH }
            Stem.NUMBERING_SYSTEM -> { checkNull(m.symbols, s); return State.NUMBERING_SYSTEM }
            Stem.SCALE -> { checkNull(m.scale, s); return State.SCALE }
            else -> when {
                stem.name.startsWith("ROUNDING_MODE_") -> { checkNull(m.roundingMode, s); m.roundingMode = RoundingMode.valueOf(stem.name.removePrefix("ROUNDING_MODE_")) }
                stem.name.startsWith("GROUP_") -> { checkNull(m.grouping, s); m.grouping = GroupingStrategy.valueOf(stem.name.removePrefix("GROUP_")) }
                stem.name.startsWith("UNIT_WIDTH_") -> { checkNull(m.unitWidth, s); m.unitWidth = UnitWidth.valueOf(stem.name.removePrefix("UNIT_WIDTH_")) }
                stem.name.startsWith("SIGN_") -> { checkNull(m.sign, s); m.sign = SignDisplay.valueOf(stem.name.removePrefix("SIGN_")) }
                stem.name.startsWith("DECIMAL_") -> { checkNull(m.decimal, s); m.decimal = DecimalSeparatorDisplay.valueOf(stem.name.removePrefix("DECIMAL_")) }
                else -> throw AssertionError()
            }
        }
        return State.NULL
    }

    private fun parseOption(state: State, s: StringSegment, m: MacroProps): State {
        when (state) {
            State.CURRENCY_UNIT -> { parseCurrency(s, m); return State.NULL }
            State.MEASURE_UNIT -> { parseMeasure(s, m); return State.NULL }
            State.PER_MEASURE_UNIT -> { val numerator = m.unit; parseMeasure(s, m); m.perUnit = m.unit; m.unit = numerator; return State.NULL }
            State.IDENTIFIER_UNIT -> {
                try { m.unit = MeasureUnit.forIdentifier(s.asString()) }
                catch (_: IllegalArgumentException) { throw SkeletonSyntaxException("Invalid unit stem", s) }
                return State.NULL
            }
            State.UNIT_USAGE -> { m.usage = s.asString(); return State.NULL }
            State.INCREMENT_PRECISION -> { m.precision = Precision.increment(parseDecimal(s, "Invalid rounding increment")); return State.PRECISION }
            State.INTEGER_WIDTH -> { parseWidth(s, m); return State.NULL }
            State.NUMBERING_SYSTEM -> {
                m.symbols = NumberingSystem.getInstanceByName(s.subSequence(0, s.length).toString())
                    ?: throw SkeletonSyntaxException("Unknown numbering system", s)
                return State.NULL
            }
            State.SCALE -> { m.scale = Scale.byBigDecimal(parseDecimal(s, "Invalid scale")); return State.NULL }
            else -> Unit
        }
        if (state == State.SCIENTIFIC) {
            if (wildcard(s[0])) {
                var offset = 1
                var min = 0
                while (offset < s.length && s[offset] == 'e') { min++; offset++ }
                if (offset == s.length) { m.notation = (m.notation as ScientificNotation).withMinExponentDigits(min); return State.SCIENTIFIC }
            }
            val trie = CharsTrie(serializedStems, 0)
            if (hasValue(trie.next(s, 0, s.length))) {
                val stem = Stem.entries[trie.getValue()]
                if (stem.name.startsWith("SIGN_")) {
                    m.notation = (m.notation as ScientificNotation).withExponentSignDisplay(SignDisplay.valueOf(stem.name.removePrefix("SIGN_")))
                    return State.SCIENTIFIC
                }
            }
        }
        if (state == State.FRACTION_PRECISION && parseFracSig(s, m)) return State.PRECISION
        if ((state == State.FRACTION_PRECISION || state == State.PRECISION) && s.contentEquals("w")) {
            m.precision = m.precision!!.trailingZeroDisplay(TrailingZeroDisplay.HIDE_IF_WHOLE)
            return State.NULL
        }
        throw SkeletonSyntaxException("Invalid option", s)
    }

    private fun parseCurrency(s: StringSegment, m: MacroProps) {
        val code = s.subSequence(0, s.length).toString()
        try { m.unit = Currency.getInstance(code) }
        catch (e: IllegalArgumentException) { throw SkeletonSyntaxException("Invalid currency", s, e) }
    }
    private fun parseMeasure(s: StringSegment, m: MacroProps) {
        var hyphen = 0
        while (hyphen < s.length && s[hyphen] != '-') hyphen++
        if (hyphen == s.length) throw SkeletonSyntaxException("Invalid measure unit option", s)
        val type = s.subSequence(0, hyphen).toString()
        val subtype = s.subSequence(hyphen + 1, s.length).toString()
        for (unit in MeasureUnit.getAvailable(type)) if (subtype.equals(unit.getSubtype())) { m.unit = unit; return }
        throw SkeletonSyntaxException("Unknown measure unit", s)
    }
    private fun parseDecimal(s: StringSegment, message: String): BigDecimal {
        val text = s.subSequence(0, s.length).toString()
        try { return BigDecimal(text) }
        catch (e: NumberFormatException) { throw SkeletonSyntaxException(message, s, e) }
    }
    private fun parseFraction(s: StringSegment, m: MacroProps) {
        assertNumberSkeleton { s[0] == '.' }
        var offset = 1; var min = 0
        while (offset < s.length && s[offset] == '0') { min++; offset++ }
        var max = min
        if (offset < s.length) {
            if (wildcard(s[offset])) { max = -1; offset++ }
            else while (offset < s.length && s[offset] == '#') { max++; offset++ }
        }
        if (offset < s.length) throw SkeletonSyntaxException("Invalid fraction stem", s)
        m.precision = if (max == -1) { if (min == 0) Precision.unlimited() else Precision.minFraction(min) }
            else Precision.minMaxFraction(min, max)
    }
    private fun parseDigits(s: StringSegment, m: MacroProps) {
        assertNumberSkeleton { s[0] == '@' }
        val digits = readDigits(s)
        if (digits.offset < s.length) throw SkeletonSyntaxException("Invalid significant digits stem", s)
        m.precision = if (digits.max == -1) Precision.minSignificantDigits(digits.min) else Precision.minMaxSignificantDigits(digits.min, digits.max)
    }
    private class Digits(val min: Int, val max: Int, val offset: Int)
    private fun readDigits(s: StringSegment): Digits {
        var offset = 0; var min = 0
        while (offset < s.length && s[offset] == '@') { min++; offset++ }
        var max = min
        if (offset < s.length) {
            if (wildcard(s[offset])) { max = -1; offset++ }
            else while (offset < s.length && s[offset] == '#') { max++; offset++ }
        }
        return Digits(min, max, offset)
    }
    private fun parseScientific(s: StringSegment, m: MacroProps) {
        assertNumberSkeleton { s[0] == 'E' }
        fun invalid(): Nothing = throw SkeletonSyntaxException("Invalid scientific stem", s)
        var offset = 1
        if (s.length == offset) invalid()
        var engineering = false
        if (s[offset] == 'E') { engineering = true; offset++; if (s.length == offset) invalid() }
        var sign = SignDisplay.AUTO
        if (s[offset] == '+') {
            offset++; if (s.length == offset) invalid()
            sign = when (s[offset]) { '!' -> SignDisplay.ALWAYS; '?' -> SignDisplay.EXCEPT_ZERO; else -> invalid() }
            offset++; if (s.length == offset) invalid()
        }
        var min = 0
        while (offset < s.length) { if (s[offset] != '0') invalid(); min++; offset++ }
        m.notation = (if (engineering) Notation.engineering() else Notation.scientific()).withExponentSignDisplay(sign).withMinExponentDigits(min)
    }
    private fun parseInteger(s: StringSegment, m: MacroProps) {
        assertNumberSkeleton { s[0] == '0' }
        var offset = 1
        while (offset < s.length) { if (s[offset] != '0') { offset--; break }; offset++ }
        if (offset < s.length) throw SkeletonSyntaxException("Invalid integer stem", s)
        m.integerWidth = IntegerWidth.zeroFillTo(offset)
    }
    private fun parseFracSig(s: StringSegment, m: MacroProps): Boolean {
        if (s[0] != '@') return false
        val digits = readDigits(s)
        var offset = digits.offset
        val old = m.precision as FractionPrecision
        if (offset < s.length) {
            if (digits.max == -1) throw SkeletonSyntaxException("Invalid digits option: Wildcard character not allowed with the priority annotation", s)
            val priority = when (s.codePointAt(offset)) {
                'r'.code -> { offset++; RoundingPriority.RELAXED }
                's'.code -> { offset++; RoundingPriority.STRICT }
                else -> { assertNumberSkeleton { offset < s.length }; RoundingPriority.RELAXED }
            }
            if (offset < s.length) throw SkeletonSyntaxException("Invalid digits option for fraction rounder", s)
            m.precision = old.withSignificantDigits(digits.min, digits.max, priority)
        } else if (digits.max == -1) m.precision = old.withMinDigits(digits.min)
        else if (digits.min == 1) m.precision = old.withMaxDigits(digits.max)
        else throw SkeletonSyntaxException("Invalid digits option: Priority annotation required", s)
        return true
    }
    private fun parseWidth(s: StringSegment, m: MacroProps) {
        var offset = 0; var min = 0
        var max = if (wildcard(s[0])) { offset++; -1 } else 0
        while (offset < s.length && max != -1 && s[offset] == '#') { max++; offset++ }
        while (offset < s.length && s[offset] == '0') { min++; offset++ }
        if (max != -1) max += min
        if (offset < s.length) throw SkeletonSyntaxException("Invalid integer width stem", s)
        m.integerWidth = if (max == -1) IntegerWidth.zeroFillTo(min) else IntegerWidth.zeroFillTo(min).truncateAt(max)
    }

    private fun repeatChar(sb: StringBuilder, char: Char, count: Int) { var i = 0; while (i < count) { sb.append(char); i++ } }
    private fun enumStem(prefix: String, value: Enum<*>?): String = prefix + value!!.name.lowercase().replace('_', '-')
    private fun fraction(sb: StringBuilder, min: Int, max: Int) {
        if (min == 0 && max == 0) { sb.append("precision-integer"); return }
        sb.append('.'); repeatChar(sb, '0', min)
        if (max == -1) sb.append('*') else repeatChar(sb, '#', max - min)
    }
    private fun digits(sb: StringBuilder, min: Int, max: Int) {
        repeatChar(sb, '@', min)
        if (max == -1) sb.append('*') else repeatChar(sb, '#', max - min)
    }
    fun generate(input: MacroProps?): String {
        val m = input!!
        val sb = StringBuilder()
        fun token(printed: Boolean) { if (printed) sb.append(' ') }
        if (m.notation != null) token(generateNotation(m, sb))
        if (m.unit != null) token(generateUnit(m, sb))
        if (m.usage != null) {
            if (m.usage != null && m.usage!!.length > 0) { sb.append("usage/").append(m.usage); token(true) }
        }
        if (m.precision != null) token(generatePrecision(m, sb))
        if (m.roundingMode != null && m.roundingMode != RoundingUtils.DEFAULT_ROUNDING_MODE) { sb.append(enumStem("rounding-mode-", m.roundingMode)); token(true) }
        if (m.grouping != null) {
            if (m.grouping !is GroupingStrategy) throw UnsupportedOperationException("Cannot generate number skeleton with custom Grouper")
            if (m.grouping != GroupingStrategy.AUTO) { sb.append(enumStem("group-", m.grouping as GroupingStrategy)); token(true) }
        }
        if (m.integerWidth != null && !m.integerWidth!!.equals(IntegerWidth.DEFAULT)) {
            if (m.integerWidth!!.minInt == 0 && m.integerWidth!!.maxInt == 0) sb.append("integer-width-trunc")
            else {
                sb.append("integer-width/")
                if (m.integerWidth!!.maxInt == -1) sb.append('*') else repeatChar(sb, '#', m.integerWidth!!.maxInt - m.integerWidth!!.minInt)
                repeatChar(sb, '0', m.integerWidth!!.minInt)
            }
            token(true)
        }
        if (m.symbols != null) {
            if (m.symbols is NumberingSystem) {
                val ns = m.symbols as NumberingSystem
                if (ns.getName()!!.equals("latn")) sb.append("latin") else sb.append("numbering-system/").append(ns.getName())
                token(true)
            } else {
                assertNumberSkeleton { m.symbols is DecimalFormatSymbols }
                throw UnsupportedOperationException("Cannot generate number skeleton with custom DecimalFormatSymbols")
            }
        }
        if (m.unitWidth != null && m.unitWidth != UnitWidth.SHORT) { sb.append(enumStem("unit-width-", m.unitWidth)); token(true) }
        if (m.sign != null && m.sign != SignDisplay.AUTO) { sb.append(enumStem("sign-", m.sign)); token(true) }
        if (m.decimal != null && m.decimal != DecimalSeparatorDisplay.AUTO) { sb.append(enumStem("decimal-", m.decimal)); token(true) }
        if (m.scale != null && m.scale!!.isValid()) {
            sb.append("scale/")
            var bd = m.scale!!.arbitrary
            if (bd == null) bd = BigDecimals.ONE
            bd = bd.scaleByPowerOfTen(m.scale!!.magnitude)
            sb.append(bd.toPlainString()); token(true)
        }
        if (m.padder != null) throw UnsupportedOperationException("Cannot generate number skeleton with custom padder")
        if (m.unitDisplayCase != null && !m.unitDisplayCase!!.isEmpty()) throw UnsupportedOperationException("Cannot generate number skeleton with custom unit display case")
        if (m.affixProvider != null) throw UnsupportedOperationException("Cannot generate number skeleton with custom affix provider")
        if (m.rules != null) throw UnsupportedOperationException("Cannot generate number skeleton with custom plural rules")
        if (sb.length > 0) sb.setLength(sb.length - 1)
        return sb.toString()
    }
    private fun generateNotation(m: MacroProps, sb: StringBuilder): Boolean {
        if (m.notation is CompactNotation) {
            if (m.notation === Notation.compactLong()) sb.append("compact-long")
            else if (m.notation === Notation.compactShort()) sb.append("compact-short")
            else throw UnsupportedOperationException("Cannot generate number skeleton with custom compact data")
        } else if (m.notation is ScientificNotation) {
            val notation = m.notation as ScientificNotation
            sb.append(if (notation.engineeringInterval == 3) "engineering" else "scientific")
            if (notation.minExponentDigits > 1) { sb.append("/*"); repeatChar(sb, 'e', notation.minExponentDigits) }
            if (notation.exponentSignDisplay != SignDisplay.AUTO) sb.append('/').append(enumStem("sign-", notation.exponentSignDisplay))
        } else { assertNumberSkeleton { m.notation is SimpleNotation }; return false }
        return true
    }
    private fun generateUnit(m: MacroProps, sb: StringBuilder): Boolean {
        var unit = m.unit
        if (m.perUnit != null) {
            if (m.unit is Currency || m.perUnit is Currency) throw UnsupportedOperationException("Cannot generate number skeleton with currency unit and per-unit")
            unit = unit!!.product(m.perUnit!!.reciprocal())
        }
        if (unit is Currency) sb.append("currency/").append(unit.getCurrencyCode())
        else if (unit!!.equals(MeasureUnit.PERCENT)) sb.append("percent")
        else if (unit.equals(MeasureUnit.PERMILLE)) sb.append("permille")
        else sb.append("unit/").append(unit.getIdentifier())
        return true
    }
    private fun generatePrecision(m: MacroProps, sb: StringBuilder): Boolean {
        when (val precision = m.precision) {
            is Precision.InfiniteRounderImpl -> sb.append("precision-unlimited")
            is Precision.FractionRounderImpl -> fraction(sb, precision.minFrac, precision.maxFrac)
            is Precision.SignificantRounderImpl -> digits(sb, precision.minSig, precision.maxSig)
            is Precision.FracSigRounderImpl -> {
                fraction(sb, precision.minFrac, precision.maxFrac); sb.append('/')
                if (precision.retain) {
                    if (precision.priority == RoundingPriority.RELAXED) digits(sb, precision.maxSig, -1) else digits(sb, 1, precision.maxSig)
                } else {
                    digits(sb, precision.minSig, precision.maxSig)
                    sb.append(if (precision.priority == RoundingPriority.RELAXED) 'r' else 's')
                }
            }
            is Precision.IncrementRounderImpl -> sb.append("precision-increment/").append(precision.increment.toPlainString())
            else -> {
                assertNumberSkeleton { m.precision is Precision.CurrencyRounderImpl }
                val impl = m.precision as Precision.CurrencyRounderImpl
                sb.append(if (impl.usage == CurrencyUsage.STANDARD) "precision-currency-standard" else "precision-currency-cash")
            }
        }
        if (m.precision!!.trailingZeroDisplay == TrailingZeroDisplay.HIDE_IF_WHOLE) sb.append("/w")
        return true
    }
}
internal expect fun assertNumberSkeleton(condition: () -> Boolean)
