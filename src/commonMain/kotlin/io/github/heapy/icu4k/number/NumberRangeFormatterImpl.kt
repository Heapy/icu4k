// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.impl.number.range.*
import io.github.heapy.icu4k.number.NumberRangeFormatter.RangeCollapse
import io.github.heapy.icu4k.number.NumberRangeFormatter.RangeIdentityFallback
import io.github.heapy.icu4k.number.NumberRangeFormatter.RangeIdentityResult
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.util.*

/** ICU range composition, plural resolution, collapse heuristics and identity fallbacks. */
internal open class NumberRangeFormatterImpl(input: RangeMacroProps?) {
    internal val formatterImpl1: NumberFormatterImpl
    internal val formatterImpl2: NumberFormatterImpl
    internal val sameFormatters: Boolean
    private val collapse: RangeCollapse
    private val identityFallback: RangeIdentityFallback
    private var rangePattern: String? = null
    private val approximatelyFormatter: NumberFormatterImpl?
    private val pluralRanges: StandardPluralRanges
    init {
        val macros = input!!
        val first = if (macros.formatter1 != null) macros.formatter1!!.locale(macros.loc) else NumberFormatter.withLocale(macros.loc)
        val second = if (macros.formatter2 != null) macros.formatter2!!.locale(macros.loc) else NumberFormatter.withLocale(macros.loc)
        formatterImpl1 = NumberFormatterImpl(first.resolve())
        formatterImpl2 = NumberFormatterImpl(second.resolve())
        sameFormatters = macros.sameFormatters != 0
        collapse = macros.collapse ?: RangeCollapse.AUTO
        identityFallback = macros.identityFallback ?: RangeIdentityFallback.APPROXIMATELY
        val nsName = formatterImpl1.getRawMicroProps().nsName
        if (nsName == null || (!sameFormatters && !nsName.equals(formatterImpl2.getRawMicroProps().nsName)))
            throw IllegalArgumentException("Both formatters must have same numbering system")
        loadRangeData(macros.loc, nsName)
        approximatelyFormatter = if (sameFormatters && (identityFallback == RangeIdentityFallback.APPROXIMATELY || identityFallback == RangeIdentityFallback.APPROXIMATELY_OR_SINGLE_VALUE)) {
            val approximately = MacroProps().also { it.approximately = true }
            NumberFormatterImpl(first.macros(approximately).resolve())
        } else null
        pluralRanges = StandardPluralRanges.forLocale(macros.loc!!)
    }
    private class DataSink(private val sb: StringBuilder) : UResource.Sink() {
        var pattern: String? = null
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val table = value.getTable()
            var i = 0
            while (table.getKeyAndValue(i++, key, value)) {
                if (key.contentEquals("range") && pattern == null) pattern = SimpleFormatterImpl.compileToStringMinMaxArguments(value.getString(), sb, 2, 2)
            }
        }
    }
    private fun loadRangeData(locale: ULocale?, nsName: String) {
        val sb = StringBuilder()
        val sink = DataSink(sb)
        val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
        sb.append("NumberElements/").append(nsName).append("/miscPatterns")
        val key = sb.toString()
        try { bundle.getAllItemsWithFallback(key, sink) } catch (_: MissingResourceException) { }
        if (sink.pattern == null) bundle.getAllItemsWithFallback("NumberElements/latn/miscPatterns", sink)
        if (sink.pattern == null) sink.pattern = SimpleFormatterImpl.compileToStringMinMaxArguments("{0}–{1}", sb, 2, 2)
        rangePattern = sink.pattern
    }
    open fun identity2d(a: RangeIdentityFallback?, b: RangeIdentityResult?): Int = a!!.ordinal or (b!!.ordinal shl 4)
    open fun format(quantity1: DecimalQuantity?, quantity2: DecimalQuantity?, equalBefore: Boolean): FormattedNumberRange {
        val backup = quantity1!!.createCopy()
        val string = FormattedStringBuilder()
        val micros1 = formatterImpl1.preProcess(quantity1)
        val micros2 = if (sameFormatters) formatterImpl1.preProcess(quantity2) else formatterImpl2.preProcess(quantity2)
        if (!micros1.modInner!!.semanticallyEquivalent(micros2.modInner) || !micros1.modMiddle!!.semanticallyEquivalent(micros2.modMiddle) || !micros1.modOuter!!.semanticallyEquivalent(micros2.modOuter)) {
            formatRange(quantity1, quantity2, string, micros1, micros2)
            return FormattedNumberRange(string, quantity1, quantity2, RangeIdentityResult.NOT_EQUAL)
        }
        val identity = if (equalBefore) RangeIdentityResult.EQUAL_BEFORE_ROUNDING else if (quantity1.equals(quantity2)) RangeIdentityResult.EQUAL_AFTER_ROUNDING else RangeIdentityResult.NOT_EQUAL
        when (identity2d(identityFallback, identity)) {
            35, 19, 3, 34, 33, 32 -> formatRange(quantity1, quantity2, string, micros1, micros2)
            18, 2, 17 -> formatApproximately(backup, quantity1, quantity2, string, micros1, micros2)
            1, 16, 0 -> formatSingleValue(quantity1, quantity2, string, micros1, micros2)
            else -> assertNumberRangeFormatter { false }
        }
        return FormattedNumberRange(string, quantity1, quantity2, identity)
    }
    private fun formatSingleValue(a: DecimalQuantity, b: DecimalQuantity?, string: FormattedStringBuilder, m1: MicroProps, m2: MicroProps) {
        if (sameFormatters) {
            val length = NumberFormatterImpl.writeNumber(m1, a, string, 0)
            NumberFormatterImpl.writeAffixes(m1, string, 0, length)
        } else formatRange(a, b, string, m1, m2)
    }
    private fun formatApproximately(backup: DecimalQuantity, a: DecimalQuantity, b: DecimalQuantity?, string: FormattedStringBuilder, m1: MicroProps, m2: MicroProps) {
        if (sameFormatters) {
            val m = approximatelyFormatter!!.preProcess(backup)
            var length = NumberFormatterImpl.writeNumber(m, backup, string, 0)
            length += m.modInner!!.apply(string, 0, length)
            length += m.modMiddle!!.apply(string, 0, length)
            m.modOuter!!.apply(string, 0, length)
        } else formatRange(a, b, string, m1, m2)
    }
    private fun formatRange(a: DecimalQuantity, b: DecimalQuantity?, string: FormattedStringBuilder, m1: MicroProps, m2: MicroProps) {
        var outer = false; var middle = false; var inner = false
        when (collapse) {
            RangeCollapse.ALL, RangeCollapse.AUTO, RangeCollapse.UNIT -> {
                outer = m1.modOuter!!.semanticallyEquivalent(m2.modOuter)
                if (outer) {
                    middle = m1.modMiddle!!.semanticallyEquivalent(m2.modMiddle)
                    if (middle) {
                        val mm = m1.modMiddle!!
                        if (collapse == RangeCollapse.UNIT) {
                            if (!mm.containsField(NumberFormatField.CURRENCY) && !mm.containsField(NumberFormatField.PERCENT)) middle = false
                        } else if (collapse == RangeCollapse.AUTO && mm.getCodePointCount() <= 1) middle = false
                        if (middle && collapse == RangeCollapse.ALL) inner = m1.modInner!!.semanticallyEquivalent(m2.modInner)
                    }
                }
            }
            else -> Unit
        }
        val h = PrefixInfixSuffixLengthHelper()
        SimpleModifier.formatTwoArgPattern(rangePattern, string, 0, h, null)
        assertNumberRangeFormatter { h.lengthInfix > 0 }
        val repeatInner = !inner && m1.modInner!!.getCodePointCount() > 0
        val repeatMiddle = !middle && m1.modMiddle!!.getCodePointCount() > 0
        val repeatOuter = !outer && m1.modOuter!!.getCodePointCount() > 0
        if (repeatInner || repeatMiddle || repeatOuter) {
            if (!PatternProps.isWhiteSpace(string[h.index1()].code)) h.lengthInfix += string.insertCodePoint(h.index1(), 0x20, null)
            if (!PatternProps.isWhiteSpace(string[h.index2() - 1].code)) h.lengthInfix += string.insertCodePoint(h.index2(), 0x20, null)
        }
        h.length1 += NumberFormatterImpl.writeNumber(m1, a, string, h.index0())
        val temp = FormattedStringBuilder()
        NumberFormatterImpl.writeNumber(m2, b, temp, 0)
        h.length2 += string.insert(h.index2(), temp)
        if (inner) {
            val mod = resolveModifierPlurals(m1.modInner, m2.modInner)
            h.lengthSuffix += mod!!.apply(string, h.index0(), h.index4())
            h.lengthPrefix += mod.getPrefixLength(); h.lengthSuffix -= mod.getPrefixLength()
        } else {
            h.length1 += m1.modInner!!.apply(string, h.index0(), h.index1())
            h.length2 += m2.modInner!!.apply(string, h.index2(), h.index4())
        }
        if (middle) {
            val mod = resolveModifierPlurals(m1.modMiddle, m2.modMiddle)
            h.lengthSuffix += mod!!.apply(string, h.index0(), h.index4())
            h.lengthPrefix += mod.getPrefixLength(); h.lengthSuffix -= mod.getPrefixLength()
        } else {
            h.length1 += m1.modMiddle!!.apply(string, h.index0(), h.index1())
            h.length2 += m2.modMiddle!!.apply(string, h.index2(), h.index4())
        }
        if (outer) {
            val mod = resolveModifierPlurals(m1.modOuter, m2.modOuter)
            h.lengthSuffix += mod!!.apply(string, h.index0(), h.index4())
            h.lengthPrefix += mod.getPrefixLength(); h.lengthSuffix -= mod.getPrefixLength()
        } else {
            h.length1 += m1.modOuter!!.apply(string, h.index0(), h.index1())
            h.length2 += m2.modOuter!!.apply(string, h.index2(), h.index4())
        }
        FormattedValueStringBuilderImpl.applySpanRange(string, NumberRangeFormatter.SpanField.NUMBER_RANGE_SPAN, 0, h.index0(), h.index1())
        FormattedValueStringBuilderImpl.applySpanRange(string, NumberRangeFormatter.SpanField.NUMBER_RANGE_SPAN, 1, h.index2(), h.index3())
    }
    internal open fun resolveModifierPlurals(first: Modifier?, second: Modifier?): Modifier? {
        val a = first!!.getParameters() ?: return first
        val b = second!!.getParameters() ?: return first
        val plural = pluralRanges.resolve(a.plural, b.plural)
        val result = a.obj!!.getModifier(a.signum, plural)
        assertNumberRangeFormatter { result != null }
        return result
    }
}
internal expect fun assertNumberRangeFormatter(condition: () -> Boolean)
