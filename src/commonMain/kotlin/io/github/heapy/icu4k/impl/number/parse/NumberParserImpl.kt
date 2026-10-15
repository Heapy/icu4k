// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.impl.number.DecimalFormatProperties.ParseMode
import io.github.heapy.icu4k.number.GroupingStrategy
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.CurrencyAmount
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmStatic

open class NumberParserImpl(private val parseFlags: Int) {
    private val matchers = ArrayList<NumberParseMatcher?>()
    private var frozen = false
    open fun addMatcher(matcher: NumberParseMatcher?) { assertNumberParser { !frozen }; matchers.add(matcher) }
    open fun addMatchers(matchers: Collection<NumberParseMatcher?>?) { assertNumberParser { !frozen }; this.matchers.addAll(matchers!!) }
    open fun freeze() { frozen = true }
    open fun getParseFlags(): Int = parseFlags
    open fun parse(input: String?, greedy: Boolean, result: ParsedNumber?) { parse(input, 0, greedy, result) }
    open fun parse(input: String?, start: Int, greedy: Boolean, result: ParsedNumber?) {
        assertNumberParser { frozen }
        assertNumberParser { start >= 0 && start < input!!.length }
        val segment = StringSegment(input, parseFlags and ParsingUtils.PARSE_FLAG_IGNORE_CASE != 0)
        segment.adjustOffset(start)
        if (greedy) parseGreedy(segment, result)
        else if (parseFlags and ParsingUtils.PARSE_FLAG_ALLOW_INFINITE_RECURSION != 0) parseLongestRecursive(segment, result, 1)
        else parseLongestRecursive(segment, result, -100)
        for (matcher in matchers) matcher!!.postProcess(result)
        result!!.postProcess()
    }
    private fun parseGreedy(segment: StringSegment, result: ParsedNumber?) {
        var i = 0
        while (i < matchers.size) {
            if (segment.length == 0) return
            val matcher = matchers[i]!!
            if (!matcher.smokeTest(segment)) { i++; continue }
            val initialOffset = segment.getOffset()
            matcher.match(segment, result)
            if (segment.getOffset() != initialOffset) { i = 0; continue }
            else { i++; continue }
        }
    }
    private fun parseLongestRecursive(segment: StringSegment, result: ParsedNumber?, recursionLevels: Int) {
        if (segment.length == 0 || recursionLevels == 0) return
        val initial = ParsedNumber(); initial.copyFrom(result)
        val candidate = ParsedNumber()
        val initialOffset = segment.getOffset()
        var i = 0
        while (i < matchers.size) {
            val matcher = matchers[i]!!
            if (!matcher.smokeTest(segment)) { i++; continue }
            var charsToConsume = 0
            while (charsToConsume < segment.length) {
                charsToConsume += if (segment.codePointAt(charsToConsume) >= 0x10000) 2 else 1
                candidate.copyFrom(initial)
                segment.setLength(charsToConsume)
                val maybeMore = matcher.match(segment, candidate)
                segment.resetLength()
                if (segment.getOffset() - initialOffset == charsToConsume) {
                    parseLongestRecursive(segment, candidate, recursionLevels + 1)
                    if (candidate.isBetterThan(result)) result!!.copyFrom(candidate)
                }
                segment.setOffset(initialOffset)
                if (!maybeMore) break
            }
            i++
        }
    }
    override fun toString(): String = "<NumberParserImpl matchers=$matchers>"
    companion object {
        @JvmStatic fun createSimpleParser(locale: ULocale?, pattern: String?, parseFlags: Int): NumberParserImpl {
            val parser = NumberParserImpl(parseFlags)
            val currency = Currency.getInstance("USD")
            val symbols = DecimalFormatSymbols.getInstance(locale)
            val ignorables = IgnorablesMatcher.getInstance(parseFlags)
            val factory = AffixTokenMatcherFactory().also { it.currency = currency; it.symbols = symbols; it.ignorables = ignorables; it.locale = locale; it.parseFlags = parseFlags }
            val patternInfo = PatternStringParser.parseToPatternInfo(pattern)
            AffixMatcher.createMatchers(patternInfo, parser, factory, ignorables, parseFlags)
            val grouper = Grouper.forStrategy(GroupingStrategy.AUTO).withLocaleData(locale, patternInfo)
            parser.addMatcher(ignorables)
            parser.addMatcher(DecimalMatcher.getInstance(symbols, grouper, parseFlags))
            parser.addMatcher(MinusSignMatcher.getInstance(symbols, false))
            parser.addMatcher(PlusSignMatcher.getInstance(symbols, false))
            parser.addMatcher(ApproximatelySignMatcher.getInstance(symbols, false))
            parser.addMatcher(PercentMatcher.getInstance(symbols))
            parser.addMatcher(PermilleMatcher.getInstance(symbols))
            parser.addMatcher(NanMatcher.getInstance(symbols, parseFlags))
            parser.addMatcher(InfinityMatcher.getInstance(symbols))
            parser.addMatcher(PaddingMatcher.getInstance("@"))
            parser.addMatcher(ScientificMatcher.getInstance(symbols, grouper))
            parser.addMatcher(CombinedCurrencyMatcher.getInstance(currency, symbols, parseFlags))
            parser.addMatcher(RequireNumberValidator())
            parser.freeze(); return parser
        }
        @JvmStatic fun parseStatic(input: String?, ppos: ParsePosition?, properties: DecimalFormatProperties?, symbols: DecimalFormatSymbols?): Number? {
            val parser = createParserFromProperties(properties, symbols, false)
            val result = ParsedNumber(); parser.parse(input, true, result)
            return if (result.success()) { ppos!!.index = result.charEnd; result.getNumber() }
            else { ppos!!.errorIndex = result.charEnd; null }
        }
        @JvmStatic fun parseStaticCurrency(input: String?, ppos: ParsePosition?, properties: DecimalFormatProperties?, symbols: DecimalFormatSymbols?): CurrencyAmount? {
            val parser = createParserFromProperties(properties, symbols, true)
            val result = ParsedNumber(); parser.parse(input, true, result)
            return if (result.success()) {
                ppos!!.index = result.charEnd
                assertNumberParser { result.currencyCode != null }
                CurrencyAmount(result.getNumber(), Currency.getInstance(result.currencyCode))
            } else { ppos!!.errorIndex = result.charEnd; null }
        }
        @JvmStatic fun createDefaultParserForLocale(loc: ULocale?): NumberParserImpl {
            val symbols = DecimalFormatSymbols.getInstance(loc)
            val properties = PatternStringParser.parseToProperties("0")
            return createParserFromProperties(properties, symbols, false)
        }
        @JvmStatic fun createParserFromProperties(properties: DecimalFormatProperties?, symbols: DecimalFormatSymbols?, parseCurrency: Boolean): NumberParserImpl {
            val locale = symbols!!.getULocale()
            val affixProvider = PropertiesAffixPatternProvider.forProperties(properties)
            val currency = CustomSymbolCurrency.resolve(properties!!.getCurrency(), locale, symbols)
            val parseMode = properties.getParseMode() ?: ParseMode.LENIENT
            val grouper = Grouper.forProperties(properties)
            var parseFlags = 0
            if (!properties.getParseCaseSensitive()) parseFlags = parseFlags or ParsingUtils.PARSE_FLAG_IGNORE_CASE
            if (properties.getParseIntegerOnly()) parseFlags = parseFlags or ParsingUtils.PARSE_FLAG_INTEGER_ONLY
            if (properties.getParseToBigDecimal()) parseFlags = parseFlags or ParsingUtils.PARSE_FLAG_FORCE_BIG_DECIMAL
            if (properties.getSignAlwaysShown()) parseFlags = parseFlags or ParsingUtils.PARSE_FLAG_PLUS_SIGN_ALLOWED
            if (parseMode == ParseMode.JAVA_COMPATIBILITY) {
                parseFlags = parseFlags or ParsingUtils.PARSE_FLAG_STRICT_SEPARATORS or ParsingUtils.PARSE_FLAG_USE_FULL_AFFIXES or ParsingUtils.PARSE_FLAG_EXACT_AFFIX or ParsingUtils.PARSE_FLAG_JAVA_COMPATIBILITY_IGNORABLES
            } else if (parseMode == ParseMode.STRICT) {
                parseFlags = parseFlags or ParsingUtils.PARSE_FLAG_STRICT_GROUPING_SIZE or ParsingUtils.PARSE_FLAG_STRICT_SEPARATORS or ParsingUtils.PARSE_FLAG_USE_FULL_AFFIXES or ParsingUtils.PARSE_FLAG_EXACT_AFFIX or ParsingUtils.PARSE_FLAG_STRICT_IGNORABLES
            } else parseFlags = parseFlags or ParsingUtils.PARSE_FLAG_INCLUDE_UNPAIRED_AFFIXES
            if (grouper.getPrimary() <= 0) parseFlags = parseFlags or ParsingUtils.PARSE_FLAG_GROUPING_DISABLED
            if (parseCurrency || affixProvider.hasCurrencySign()) parseFlags = parseFlags or ParsingUtils.PARSE_FLAG_MONETARY_SEPARATORS
            if (!parseCurrency) parseFlags = parseFlags or ParsingUtils.PARSE_FLAG_NO_FOREIGN_CURRENCIES
            val parser = NumberParserImpl(parseFlags)
            val ignorables = IgnorablesMatcher.getInstance(parseFlags)
            val factory = AffixTokenMatcherFactory().also { it.currency = currency; it.symbols = symbols; it.ignorables = ignorables; it.locale = locale; it.parseFlags = parseFlags }
            AffixMatcher.createMatchers(affixProvider, parser, factory, ignorables, parseFlags)
            if (parseCurrency || affixProvider.hasCurrencySign()) parser.addMatcher(CombinedCurrencyMatcher.getInstance(currency, symbols, parseFlags))
            if (parseMode == ParseMode.LENIENT && affixProvider.containsSymbolType(AffixUtils.TYPE_PERCENT)) parser.addMatcher(PercentMatcher.getInstance(symbols))
            if (parseMode == ParseMode.LENIENT && affixProvider.containsSymbolType(AffixUtils.TYPE_PERMILLE)) parser.addMatcher(PermilleMatcher.getInstance(symbols))
            if (parseMode == ParseMode.LENIENT) {
                parser.addMatcher(PlusSignMatcher.getInstance(symbols, false))
                parser.addMatcher(MinusSignMatcher.getInstance(symbols, false))
                parser.addMatcher(ApproximatelySignMatcher.getInstance(symbols, false))
            }
            parser.addMatcher(NanMatcher.getInstance(symbols, parseFlags))
            parser.addMatcher(InfinityMatcher.getInstance(symbols))
            val padString = properties.getPadString()
            if (padString != null && !ignorables.getSet()!!.contains(padString)) parser.addMatcher(PaddingMatcher.getInstance(padString))
            parser.addMatcher(ignorables)
            parser.addMatcher(DecimalMatcher.getInstance(symbols, grouper, parseFlags))
            if (!properties.getParseNoExponent() || properties.getMinimumExponentDigits() > 0) parser.addMatcher(ScientificMatcher.getInstance(symbols, grouper))
            parser.addMatcher(RequireNumberValidator())
            if (parseMode != ParseMode.LENIENT) parser.addMatcher(RequireAffixValidator())
            if (parseCurrency) parser.addMatcher(RequireCurrencyValidator())
            if (properties.getDecimalPatternMatchRequired()) {
                val hasDecimal = properties.getDecimalSeparatorAlwaysShown() || properties.getMaximumFractionDigits() != 0
                parser.addMatcher(RequireDecimalSeparatorValidator.getInstance(hasDecimal))
            }
            val multiplier = RoundingUtils.scaleFromProperties(properties)
            if (multiplier != null) parser.addMatcher(MultiplierParseHandler(multiplier))
            parser.freeze(); return parser
        }
    }
}
internal expect fun assertNumberParser(condition: () -> Boolean)
