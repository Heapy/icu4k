package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.number.parse.*
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencyAffixMatcherOracleTest {
    @Test fun currencyPrefixesSpacingAndForeignNames() {
        for (locale in listOf("en", "fr", "ar", "ru")) for (code in listOf("USD", "EUR", "JPY")) {
            val ks = DecimalFormatSymbols(ULocale(locale)); val js = com.ibm.icu.text.DecimalFormatSymbols(com.ibm.icu.util.ULocale(locale))
            for (spacing in listOf("\u00a0", "ab")) {
                for (before in listOf(false, true)) {
                    ks.setPatternForCurrencySpacing(DecimalFormatSymbols.CURRENCY_SPC_INSERT, before, spacing)
                    js.setPatternForCurrencySpacing(com.ibm.icu.text.DecimalFormatSymbols.CURRENCY_SPC_INSERT, before, spacing)
                }
                for (flags in listOf(0, ParsingUtils.PARSE_FLAG_NO_FOREIGN_CURRENCIES)) {
                    val k = CombinedCurrencyMatcher.getInstance(Currency.getInstance(code), ks, flags)
                    val j = com.ibm.icu.impl.number.parse.CombinedCurrencyMatcher.getInstance(com.ibm.icu.util.Currency.getInstance(code), js, flags)
                    for (input in listOf("USD", "usd", "$", "US$", "€", "EUR", "euro", "euros", "US dollars", "dollar des États-Unis", "dollars des États-Unis", "японская иена", "JPY", "\u00a0USD", "abUSD", "USDab", "USD\u00a0", "ab", "x")) {
                        for (end in 1..input.length) for (number in listOf(false, true)) for (fold in listOf(false, true)) {
                            val text = input.take(end)
                            val seg = StringSegment(text, fold); val jseg = com.ibm.icu.impl.StringSegment(text, fold)
                            val kr = ParsedNumber(); val jr = com.ibm.icu.impl.number.parse.ParsedNumber()
                            if (number) { kr.quantity = DecimalQuantity_DualStorageBCD(1); jr.quantity = com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD(1) }
                            val label = "$locale/$code/$spacing/$flags/$number/$fold '$text'"
                            assertEquals(j.match(jseg, jr), k.match(seg, kr), label)
                            assertEquals(jseg.offset, seg.getOffset(), label)
                            assertEquals(jr.currencyCode, kr.currencyCode, label)
                            assertEquals(jr.charEnd, kr.charEnd, label)
                            assertEquals(j.match(jseg, jr), k.match(seg, kr), "repeat $label")
                        }
                    }
                }
            }
        }
    }
    @Test fun affixTokensAndIgnorables() {
        val kf = AffixTokenMatcherFactory().also { it.symbols = DecimalFormatSymbols(ULocale.ENGLISH); it.currency = Currency.getInstance("USD"); it.ignorables = IgnorablesMatcher.getInstance(0); it.locale = ULocale.ENGLISH }
        val jf = com.ibm.icu.impl.number.parse.AffixTokenMatcherFactory().also { it.symbols = com.ibm.icu.text.DecimalFormatSymbols(com.ibm.icu.util.ULocale.ENGLISH); it.currency = com.ibm.icu.util.Currency.getInstance("USD"); it.ignorables = com.ibm.icu.impl.number.parse.IgnorablesMatcher.getInstance(0); it.locale = com.ibm.icu.util.ULocale.ENGLISH }
        for (pattern in listOf("", "-", "+", "~", "%", "‰", "¤", "¤¤", "¤¤¤", "¤¤¤¤", "¤¤¤¤¤", "¤¤¤¤¤¤", "'abc'", "'a b'", "- 'a'", "'a' -", "'a''b'", "'😀'", " ", "'x'¤'y'")) {
            for (flags in listOf(0, ParsingUtils.PARSE_FLAG_EXACT_AFFIX)) {
                val k = AffixPatternMatcher.fromAffixPattern(pattern, kf, flags)
                val j = com.ibm.icu.impl.number.parse.AffixPatternMatcher.fromAffixPattern(pattern, jf, flags)
                assertEquals(j?.length(), k?.length(), pattern)
                assertEquals(j?.toString(), k?.toString(), pattern)
                assertEquals(j?.hashCode(), k?.hashCode(), pattern)
                if (k == null || j == null) continue
                assertEquals(k, AffixPatternMatcher.fromAffixPattern(pattern, kf, flags))
                for (input in listOf("-", "+", "~", "%", "‰", "$", "USD", "US dollars", "abc", "a b", "a   b", "- a", "a -", "a'b", "😀", "x$" + "y", " x ")) {
                    for (end in 1..input.length) {
                        val text = input.take(end); val seg = StringSegment(text, true); val jseg = com.ibm.icu.impl.StringSegment(text, true)
                        val kr = ParsedNumber(); val jr = com.ibm.icu.impl.number.parse.ParsedNumber()
                        val label = "$pattern/$flags '$text'"
                        assertEquals(j.match(jseg, jr), k.match(seg, kr), label)
                        assertEquals(jseg.offset, seg.getOffset(), label)
                        assertEquals(jr.charEnd, kr.charEnd, label); assertEquals(jr.flags, kr.flags, label)
                        assertEquals(jr.currencyCode, kr.currencyCode, label)
                    }
                }
            }
        }
    }
}
