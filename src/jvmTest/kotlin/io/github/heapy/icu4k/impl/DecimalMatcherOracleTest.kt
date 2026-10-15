package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.number.Grouper
import io.github.heapy.icu4k.impl.number.DecimalFormatProperties
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.impl.number.parse.*
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.ULocale
import kotlin.random.Random
import kotlin.test.*

class DecimalMatcherOracleTest {
    @Test fun scientificPrefixesSignsAndSpecialValues() {
        for (custom in listOf(false, true)) {
            val ks = DecimalFormatSymbols(ULocale.ENGLISH)
            val js = com.ibm.icu.text.DecimalFormatSymbols(com.ibm.icu.util.ULocale.ENGLISH)
            if (custom) {
                ks.setExponentSeparator("EXP"); js.setExponentSeparator("EXP")
                ks.setMinusSignString("minus"); js.setMinusSignString("minus")
                ks.setPlusSignString("plus"); js.setPlusSignString("plus")
            }
            val k = ScientificMatcher.getInstance(ks, Grouper.forProperties(DecimalFormatProperties()))
            val j = com.ibm.icu.impl.number.parse.ScientificMatcher.getInstance(js, com.ibm.icu.impl.number.Grouper.forProperties(com.ibm.icu.impl.number.DecimalFormatProperties()))
            for (text in listOf("E", "E+", "E-2", "E12.5", "E2147483648", "E-2147483648", "E9999999999999999999999", "E\u061c-\u061c2", "E 2", "EXPminus20", "EXPmi", "EXPplus3", "EXPx", "EX", "x")) {
                for (end in 1..text.length) for (flag in listOf(0, ParsedNumber.FLAG_NAN, ParsedNumber.FLAG_INFINITY, ParsedNumber.FLAG_HAS_EXPONENT)) {
                    val kr = ParsedNumber().also { it.flags = flag }
                    val jr = com.ibm.icu.impl.number.parse.ParsedNumber().also { it.flags = flag }
                    if (flag == 0 || flag == ParsedNumber.FLAG_HAS_EXPONENT) {
                        kr.quantity = DecimalQuantity_DualStorageBCD(12)
                        jr.quantity = com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD(12)
                    }
                    val seg = StringSegment(text.take(end), true)
                    val jseg = com.ibm.icu.impl.StringSegment(text.take(end), true)
                    val label = "$custom/$flag/${text.take(end)}"
                    assertEquals(j.smokeTest(jseg), k.smokeTest(seg), label)
                    assertEquals(j.match(jseg, jr), k.match(seg, kr), label)
                    assertEquals(jseg.offset, seg.getOffset(), label)
                    assertEquals(jr.flags, kr.flags, label); assertEquals(jr.charEnd, kr.charEnd, label)
                    assertEquals(jr.quantity?.toScientificString(), kr.quantity?.toScientificString(), label)
                }
            }
        }
    }

    @Test fun decimalGroupingAndExponentMatrix() {
        val random = Random(7901)
        val cases = mutableListOf("", "1", "1,1", "1,1,", "1,1,1", ",1", "1,,234", "1,234,567.89", "12,34,567", ".", ".5", "1.", "1..2", "000.0001", "١٢٣٫٤٥", "１２３", "𝟙𝟚.𝟛", "2147483647", "2147483648", "999999999999999999999999999", "ab", "aaab", "a", "1::2", "1__234")
        repeat(240) { cases += buildString { repeat(random.nextInt(1, 20)) { append("0123456789,._: x"[random.nextInt(15)]) } } }
        for (locale in listOf("en", "fr", "ar", "hi")) for (custom in 0..2) {
            val ks = DecimalFormatSymbols(ULocale(locale))
            val js = com.ibm.icu.text.DecimalFormatSymbols(com.ibm.icu.util.ULocale(locale))
            if (custom > 0) {
                ks.setGroupingSeparatorString("__"); js.setGroupingSeparatorString("__")
                ks.setDecimalSeparatorString("::"); js.setDecimalSeparatorString("::")
            }
            if (custom == 2) {
                val digits = Array(10) { "a" + ('a' + it) }
                ks.setDigitStrings(digits); js.setDigitStrings(digits)
            }
            for ((primary, secondary) in listOf(3 to 3, 3 to 2)) {
                val kp = DecimalFormatProperties().setGroupingSize(primary).setSecondaryGroupingSize(secondary)
                val jp = com.ibm.icu.impl.number.DecimalFormatProperties().setGroupingSize(primary).setSecondaryGroupingSize(secondary)
                for (flags in listOf(0, ParsingUtils.PARSE_FLAG_STRICT_GROUPING_SIZE, ParsingUtils.PARSE_FLAG_STRICT_SEPARATORS, ParsingUtils.PARSE_FLAG_INTEGER_ONLY, ParsingUtils.PARSE_FLAG_GROUPING_DISABLED, ParsingUtils.PARSE_FLAG_MONETARY_SEPARATORS, ParsingUtils.PARSE_FLAG_STRICT_GROUPING_SIZE or ParsingUtils.PARSE_FLAG_STRICT_SEPARATORS)) {
                    val k = DecimalMatcher.getInstance(ks, Grouper.forProperties(kp), flags)
                    val j = com.ibm.icu.impl.number.parse.DecimalMatcher.getInstance(js, com.ibm.icu.impl.number.Grouper.forProperties(jp), flags)
                    for (input in cases) for (exponent in listOf(0, 1, -1)) {
                        val kr = ParsedNumber(); val jr = com.ibm.icu.impl.number.parse.ParsedNumber()
                        if (exponent != 0) {
                            kr.quantity = DecimalQuantity_DualStorageBCD(123)
                            jr.quantity = com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD(123)
                        }
                        val seg = StringSegment(input, false); val jseg = com.ibm.icu.impl.StringSegment(input, false)
                        val label = "$locale/$custom/$primary/$secondary/$flags/$exponent '$input'"
                        if (input.isNotEmpty()) assertEquals(j.smokeTest(jseg), k.smokeTest(seg), label)
                        val expected = runCatching { j.match(jseg, jr, exponent) }
                        val actual = runCatching { k.match(seg, kr, exponent) }
                        assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass, label)
                        if (expected.isSuccess) assertEquals(expected.getOrThrow(), actual.getOrThrow(), label)
                        assertEquals(jseg.offset, seg.getOffset(), label)
                        assertEquals(jr.flags, kr.flags, label); assertEquals(jr.charEnd, kr.charEnd, label)
                        assertEquals(jr.quantity?.toScientificString(), kr.quantity?.toScientificString(), label)
                    }
                }
            }
        }
    }
}
