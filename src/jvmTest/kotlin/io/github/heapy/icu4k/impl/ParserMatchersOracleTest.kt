package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.number.parse.*
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*
import com.ibm.icu.impl.number.parse.ParsedNumber as JResult
import com.ibm.icu.impl.number.parse.NumberParseMatcher as JMatcher
import com.ibm.icu.impl.StringSegment as JSegment

class ParserMatchersOracleTest {
    @Test fun symbolsAndPartialInput() {
        for (locale in listOf("en", "ar", "fr", "sv")) {
            val symbols = DecimalFormatSymbols(ULocale(locale))
            val js = com.ibm.icu.text.DecimalFormatSymbols(com.ibm.icu.util.ULocale(locale))
            val pairs = listOf(
                MinusSignMatcher.getInstance(symbols, false) to com.ibm.icu.impl.number.parse.MinusSignMatcher.getInstance(js, false),
                PlusSignMatcher.getInstance(symbols, true) to com.ibm.icu.impl.number.parse.PlusSignMatcher.getInstance(js, true),
                PercentMatcher.getInstance(symbols) to com.ibm.icu.impl.number.parse.PercentMatcher.getInstance(js),
                PermilleMatcher.getInstance(symbols) to com.ibm.icu.impl.number.parse.PermilleMatcher.getInstance(js),
                InfinityMatcher.getInstance(symbols) to com.ibm.icu.impl.number.parse.InfinityMatcher.getInstance(js),
                NanMatcher.getInstance(symbols, 0) to com.ibm.icu.impl.number.parse.NanMatcher.getInstance(js, 0),
                IgnorablesMatcher.getInstance(0) to com.ibm.icu.impl.number.parse.IgnorablesMatcher.getInstance(0),
                PaddingMatcher.getInstance("ab") to com.ibm.icu.impl.number.parse.PaddingMatcher.getInstance("ab"),
                CodePointMatcher.getInstance(0x1f600) to com.ibm.icu.impl.number.parse.CodePointMatcher.getInstance(0x1f600)
            )
            for ((k, j) in pairs) for (input in listOf("-", "+", "%", "‰", "∞", "NaN", "na", "a", "ab", "abc", " ", "\u061c-", "😀", "x")) {
                for (flags in listOf(0, 1, 2, 4, 64, 128, 256)) {
                    val kr = ParsedNumber().also { it.flags = flags }
                    val jr = JResult().also { it.flags = flags }
                    val ks = StringSegment(input, true); val seg = JSegment(input, true)
                    val context = "$locale $k '$input' $flags"
                    assertEquals(j.smokeTest(seg), k.smokeTest(ks), context)
                    assertEquals(j.match(seg, jr), k.match(ks, kr), context)
                    assertEquals(seg.offset, ks.getOffset(), context)
                    assertEquals(jr.charEnd, kr.charEnd, context)
                    assertEquals(jr.flags, kr.flags, context)
                }
            }
        }
    }

    @Test fun resultNumbersAndValidators() {
        for (value in listOf(0.0, -0.0, 1.0, -42.0, 1.25, 1e25)) for (flags in listOf(0, 1, 64, 128, 129, 192, 256)) {
            val k = ParsedNumber().also { it.quantity = DecimalQuantity_DualStorageBCD(value); it.flags = flags; it.charEnd = 3 }
            val j = JResult().also { it.quantity = com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD(value); it.flags = flags; it.charEnd = 3 }
            repeat(2) {
                k.postProcess(); j.postProcess()
                assertEquals(j.success(), k.success()); assertEquals(j.seenNumber(), k.seenNumber())
                for (parseFlags in listOf(0, ParsingUtils.PARSE_FLAG_INTEGER_ONLY, ParsingUtils.PARSE_FLAG_FORCE_BIG_DECIMAL)) {
                    val expected = j.getNumber(parseFlags); val actual = k.getNumber(parseFlags)
                    assertEquals(expected.javaClass, actual.javaClass)
                    assertEquals(expected.toString(), actual.toString())
                }
            }
            val copy = ParsedNumber(); copy.copyFrom(k)
            assertNotSame(k.quantity, copy.quantity)
            assertEquals(k.getNumber().toString(), copy.getNumber().toString())
        }
        val validators = listOf(
            RequireNumberValidator() to com.ibm.icu.impl.number.parse.RequireNumberValidator(),
            RequireAffixValidator() to com.ibm.icu.impl.number.parse.RequireAffixValidator(),
            RequireCurrencyValidator() to com.ibm.icu.impl.number.parse.RequireCurrencyValidator(),
            RequireDecimalSeparatorValidator.getInstance(true) to com.ibm.icu.impl.number.parse.RequireDecimalSeparatorValidator.getInstance(true),
            RequireDecimalSeparatorValidator.getInstance(false) to com.ibm.icu.impl.number.parse.RequireDecimalSeparatorValidator.getInstance(false)
        )
        for ((k, j) in validators) for (flags in 0..511) {
            val kr = ParsedNumber().also { it.flags = flags }; val jr = JResult().also { it.flags = flags }
            j.postProcess(jr); k.postProcess(kr); assertEquals(jr.flags, kr.flags)
        }
    }

    @Test fun seriesRollbackAndWeakConsumption() {
        for (input in listOf("a b", "a   b!", "a x", "a ", "ab", "x")) {
            val k = SeriesMatcher(); val j = com.ibm.icu.impl.number.parse.SeriesMatcher()
            k.addMatcher(CodePointMatcher.getInstance(97)); j.addMatcher(com.ibm.icu.impl.number.parse.CodePointMatcher.getInstance(97))
            k.addMatcher(PaddingMatcher.getInstance(" ")); j.addMatcher(com.ibm.icu.impl.number.parse.PaddingMatcher.getInstance(" "))
            k.addMatcher(CodePointMatcher.getInstance(98)); j.addMatcher(com.ibm.icu.impl.number.parse.CodePointMatcher.getInstance(98))
            k.freeze(); j.freeze()
            val ks = StringSegment(input, false); val js = JSegment(input, false)
            val kr = ParsedNumber(); val jr = JResult()
            assertEquals(j.match(js, jr), k.match(ks, kr), input)
            assertEquals(js.offset, ks.getOffset(), input); assertEquals(jr.charEnd, kr.charEnd, input)
        }
    }
}
