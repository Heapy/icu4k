package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.number.PatternStringParser
import io.github.heapy.icu4k.impl.number.DecimalFormatProperties.ParseMode
import io.github.heapy.icu4k.impl.number.parse.*
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.ULocale
import kotlin.random.Random
import kotlin.test.*

class NumberParserOracleTest {
    private fun compare(k: NumberParserImpl, j: com.ibm.icu.impl.number.parse.NumberParserImpl, input: String, greedy: Boolean, start: Int = 0) {
        val kr = ParsedNumber(); val jr = com.ibm.icu.impl.number.parse.ParsedNumber()
        val label = "$input/$greedy/$start/${k.getParseFlags()}"
        val expected = runCatching { j.parse(input, start, greedy, jr) }
        val actual = runCatching { k.parse(input, start, greedy, kr) }
        assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass, label)
        assertEquals(jr.charEnd, kr.charEnd, label); assertEquals(jr.flags, kr.flags, label)
        assertEquals(jr.prefix, kr.prefix, label); assertEquals(jr.suffix, kr.suffix, label)
        assertEquals(jr.currencyCode, kr.currencyCode, label)
        assertEquals(jr.success(), kr.success(), label)
        assertEquals(jr.quantity?.toScientificString(), kr.quantity?.toScientificString(), label)
        if (jr.seenNumber()) {
            val jn = jr.getNumber(j.parseFlags); val kn = kr.getNumber(k.getParseFlags())
            assertEquals(jn.javaClass, kn.javaClass, label); assertEquals(jn.toString(), kn.toString(), label)
        }
    }
    @Test fun propertyParserModesAndPatterns() {
        val inputs = listOf("0", "-0", "-12.50", "+12", "~12", "1,234.50x", "12,34,567", "1,1", "1,,234", "12%", "%12", "12‰", "12E-2", "12E+", "NaN", "-∞", "NaNE0", "$" + "12", "USD 12", "12 EUR", "(12)", "abc12xyz", "abc12", "12xyz", " 12 ", "@@12", "١٢٣٫٤", "12.34", "x", "0.00000001")
        for (locale in listOf("en", "fr", "ar", "hi")) for (pattern in listOf("#,##0.###", "#,##,##0.00", "0%;-0%", "0‰", "¤#,##0.00;(¤#,##0.00)", "'abc'0'xyz';('abc'0'xyz')", "0.00E0", "0;-0-", "*x0")) {
            val ks = DecimalFormatSymbols(ULocale(locale)); val js = com.ibm.icu.text.DecimalFormatSymbols(com.ibm.icu.util.ULocale(locale))
            for (mode in ParseMode.entries) for (currency in listOf(false, true)) {
                val kp = PatternStringParser.parseToProperties(pattern).setParseMode(mode)
                val jp = com.ibm.icu.impl.number.PatternStringParser.parseToProperties(pattern).setParseMode(com.ibm.icu.impl.number.DecimalFormatProperties.ParseMode.valueOf(mode.name))
                val k = NumberParserImpl.createParserFromProperties(kp, ks, currency)
                val j = com.ibm.icu.impl.number.parse.NumberParserImpl.createParserFromProperties(jp, js, currency)
                assertEquals(j.parseFlags, k.getParseFlags())
                assertEquals(j.toString().replace(Regex("[a-zA-Z0-9.]+Scale@[0-9a-f]+"), "Scale@identity"), k.toString().replace(Regex("[a-zA-Z0-9.]+Scale@[0-9a-f]+"), "Scale@identity"), "$locale/$pattern/$mode/$currency")
                for (input in inputs) for (greedy in listOf(false, true)) compare(k, j, input, greedy)
            }
        }
    }
    @Test fun propertyOptionsAndStaticEntries() {
        for (option in 0..6) {
            val kp = PatternStringParser.parseToProperties("0.00%")
            val jp = com.ibm.icu.impl.number.PatternStringParser.parseToProperties("0.00%")
            when (option) {
                0 -> { kp.setParseIntegerOnly(true); jp.setParseIntegerOnly(true) }
                1 -> { kp.setParseToBigDecimal(true); jp.setParseToBigDecimal(true) }
                2 -> { kp.setParseNoExponent(true); jp.setParseNoExponent(true) }
                3 -> { kp.setDecimalPatternMatchRequired(true); jp.setDecimalPatternMatchRequired(true) }
                4 -> { kp.setSignAlwaysShown(true); jp.setSignAlwaysShown(true) }
                5 -> { kp.setParseCaseSensitive(true); jp.setParseCaseSensitive(true) }
                6 -> { kp.setMagnitudeMultiplier(3); jp.setMagnitudeMultiplier(3) }
            }
            val ks = DecimalFormatSymbols(ULocale.ENGLISH); val js = com.ibm.icu.text.DecimalFormatSymbols(com.ibm.icu.util.ULocale.ENGLISH)
            val k = NumberParserImpl.createParserFromProperties(kp, ks, false)
            val j = com.ibm.icu.impl.number.parse.NumberParserImpl.createParserFromProperties(jp, js, false)
            for (input in listOf("12.50%", "12", "-0", "NaN", "nan", "12E2", "+12", "x", "12%")) {
                compare(k, j, input, true); compare(k, j, "xx$input", false, 2)
                val pp = ParsePosition(7).also { it.errorIndex = 8 }; val jpp = java.text.ParsePosition(7).also { it.errorIndex = 8 }
                val expected = com.ibm.icu.impl.number.parse.NumberParserImpl.parseStatic(input, jpp, jp, js)
                val actual = NumberParserImpl.parseStatic(input, pp, kp, ks)
                assertEquals(expected?.toString(), actual?.toString()); assertEquals(jpp.index, pp.index); assertEquals(jpp.errorIndex, pp.errorIndex)
                val cp = ParsePosition(7); val jcp = java.text.ParsePosition(7)
                val jc = com.ibm.icu.impl.number.parse.NumberParserImpl.parseStaticCurrency(input, jcp, jp, js)
                val kc = NumberParserImpl.parseStaticCurrency(input, cp, kp, ks)
                assertEquals(jc?.number?.toString(), kc?.getNumber()?.toString()); assertEquals(jc?.currency?.currencyCode, kc?.getCurrency()?.getCurrencyCode())
                assertEquals(jcp.index, cp.index); assertEquals(jcp.errorIndex, cp.errorIndex)
            }
        }
    }
    @Test fun simpleParserRandomInputsAndStarts() {
        val random = Random(8193)
        val inputs = mutableListOf("1", "-0", "USD12", "12EUR", "-12E3", "(12)", "12%", "@12", "1,1,", "NaN", "∞", "𝟙𝟚.𝟛")
        repeat(120) { inputs += buildString { repeat(random.nextInt(1, 12)) { append("0123,E-+% x"[random.nextInt(10)]) } } }
        for (flags in listOf(0, ParsingUtils.PARSE_FLAG_IGNORE_CASE, ParsingUtils.PARSE_FLAG_USE_FULL_AFFIXES or ParsingUtils.PARSE_FLAG_INCLUDE_UNPAIRED_AFFIXES, ParsingUtils.PARSE_FLAG_ALLOW_INFINITE_RECURSION)) {
            val k = NumberParserImpl.createSimpleParser(ULocale.ENGLISH, "0;(0)", flags)
            val j = com.ibm.icu.impl.number.parse.NumberParserImpl.createSimpleParser(com.ibm.icu.util.ULocale.ENGLISH, "0;(0)", flags)
            assertEquals(j.toString(), k.toString())
            for (input in inputs) for (greedy in listOf(false, true)) compare(k, j, input, greedy)
        }
        val k = NumberParserImpl.createDefaultParserForLocale(ULocale.ENGLISH)
        val j = com.ibm.icu.impl.number.parse.NumberParserImpl.createDefaultParserForLocale(com.ibm.icu.util.ULocale.ENGLISH)
        for (input in inputs) compare(k, j, input, true)
    }
}
