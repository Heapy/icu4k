// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.impl.TextTrieMap
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.decimalSymbolsULocale
import io.github.heapy.icu4k.util.Currency
import kotlin.jvm.JvmStatic

open class CombinedCurrencyMatcher private constructor(currency: Currency?, dfs: DecimalFormatSymbols?, parseFlags: Int) : NumberParseMatcher {
    private val isoCode = currency!!.getSubtype()
    private val currency1 = currency!!.getSymbol(dfs!!.getULocale())
    private val currency2 = currency!!.getCurrencyCode()
    private val afterPrefixInsert = dfs!!.getPatternForCurrencySpacing(DecimalFormatSymbols.CURRENCY_SPC_INSERT, false)
    private val beforeSuffixInsert = dfs!!.getPatternForCurrencySpacing(DecimalFormatSymbols.CURRENCY_SPC_INSERT, true)
    private val longNameTrie: TextTrieMap<Currency.CurrencyStringInfo>?
    private val symbolTrie: TextTrieMap<Currency.CurrencyStringInfo>?
    private val localLongNames: Array<String?>?
    init {
        if (parseFlags and ParsingUtils.PARSE_FLAG_NO_FOREIGN_CURRENCIES == 0) {
            longNameTrie = Currency.getParsingTrie(dfs!!.getULocale(), Currency.LONG_NAME)
            symbolTrie = Currency.getParsingTrie(dfs.getULocale(), Currency.SYMBOL_NAME)
            localLongNames = null
        } else {
            longNameTrie = null; symbolTrie = null
            localLongNames = arrayOfNulls(StandardPlural.COUNT)
            for (i in 0 until StandardPlural.COUNT) {
                localLongNames[i] = currency!!.getName(decimalSymbolsULocale(dfs!!.getRequestedLocale()), Currency.PLURAL_LONG_NAME, StandardPlural.VALUES[i].getKeyword(), null)
            }
        }
    }
    override fun match(segment: StringSegment?, result: ParsedNumber?): Boolean {
        if (result!!.currencyCode != null) return false
        val initialOffset = segment!!.getOffset()
        var maybeMore = false
        if (result.seenNumber() && beforeSuffixInsert!!.isNotEmpty()) {
            val overlap = segment.getCommonPrefixLength(beforeSuffixInsert)
            if (overlap == beforeSuffixInsert.length) segment.adjustOffset(overlap)
            maybeMore = maybeMore || overlap == segment.length
        }
        // Source short-circuiting is observable: a spacing prefix may defer currency matching.
        maybeMore = maybeMore || matchCurrency(segment, result)
        if (result.currencyCode == null) { segment.setOffset(initialOffset); return maybeMore }
        if (!result.seenNumber() && afterPrefixInsert!!.isNotEmpty()) {
            val overlap = segment.getCommonPrefixLength(afterPrefixInsert)
            if (overlap == afterPrefixInsert.length) segment.adjustOffset(overlap)
            maybeMore = maybeMore || overlap == segment.length
        }
        return maybeMore
    }
    private fun matchCurrency(segment: StringSegment, result: ParsedNumber): Boolean {
        var maybeMore = false
        val overlap1 = if (currency1!!.isNotEmpty()) segment.getCaseSensitivePrefixLength(currency1) else -1
        maybeMore = maybeMore || overlap1 == segment.length
        if (overlap1 == currency1.length) {
            result.currencyCode = isoCode; segment.adjustOffset(overlap1); result.setCharsConsumed(segment); return maybeMore
        }
        val overlap2 = if (currency2!!.isNotEmpty()) segment.getCommonPrefixLength(currency2) else -1
        maybeMore = maybeMore || overlap2 == segment.length
        if (overlap2 == currency2!!.length) {
            result.currencyCode = isoCode; segment.adjustOffset(overlap2); result.setCharsConsumed(segment); return maybeMore
        }
        if (longNameTrie != null) {
            val output = TextTrieMap.Output()
            var values = longNameTrie.get(segment, 0, output)
            maybeMore = maybeMore || output.partialMatch
            if (values == null) { values = symbolTrie!!.get(segment, 0, output); maybeMore = maybeMore || output.partialMatch }
            if (values != null) {
                result.currencyCode = values.next().getISOCode()
                segment.adjustOffset(output.matchLength); result.setCharsConsumed(segment); return maybeMore
            }
        } else {
            var longestFullMatch = 0
            for (i in 0 until StandardPlural.COUNT) {
                val name = localLongNames!![i]!!
                if (name.isEmpty()) continue
                val overlap = segment.getCommonPrefixLength(name)
                if (overlap == name.length && name.length > longestFullMatch) longestFullMatch = name.length
                maybeMore = maybeMore || overlap > 0
            }
            if (longestFullMatch > 0) {
                result.currencyCode = isoCode; segment.adjustOffset(longestFullMatch); result.setCharsConsumed(segment); return maybeMore
            }
        }
        return maybeMore
    }
    override fun smokeTest(segment: StringSegment?): Boolean = true
    override fun postProcess(result: ParsedNumber?) {}
    override fun toString(): String = "<CombinedCurrencyMatcher $isoCode>"
    companion object {
        @JvmStatic fun getInstance(currency: Currency?, dfs: DecimalFormatSymbols?, parseFlags: Int): CombinedCurrencyMatcher = CombinedCurrencyMatcher(currency, dfs, parseFlags)
    }
}
