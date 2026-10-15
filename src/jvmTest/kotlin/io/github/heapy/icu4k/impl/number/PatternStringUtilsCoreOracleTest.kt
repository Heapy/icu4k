@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.PatternStringUtils as JUtils
import com.ibm.icu.impl.number.PatternStringParser as JParser
import com.ibm.icu.impl.number.PropertiesAffixPatternProvider as JProvider
import com.ibm.icu.impl.number.DecimalFormatProperties as JProperties
import com.ibm.icu.impl.number.Padder as JPadder
import com.ibm.icu.impl.number.AffixPatternProvider as JAffix
import com.ibm.icu.number.NumberFormatter.SignDisplay as JSign
import com.ibm.icu.impl.number.Modifier.Signum as JSignum
import com.ibm.icu.impl.StandardPlural as JPlural
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.units.UnitDecimal
import java.math.BigDecimal
import kotlin.random.Random
import kotlin.test.*

class PatternStringUtilsCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { if (e is IllegalArgumentException) "argument:${e.message}" else e.javaClass.simpleName }
    // Supply identical affix-provider inputs: that provider's currency-plural implementation is a separate port.
    private fun adapter(source: JAffix): AffixPatternProvider = object : AffixPatternProvider {
        override fun charAt(flags: Int, index: Int) = source.charAt(flags, index)
        override fun length(flags: Int) = source.length(flags)
        override fun getString(flags: Int) = source.getString(flags)
        override fun hasCurrencySign() = source.hasCurrencySign()
        override fun positiveHasPlusSign() = source.positiveHasPlusSign()
        override fun hasNegativeSubpattern() = source.hasNegativeSubpattern()
        override fun negativeHasMinusSign() = source.negativeHasMinusSign()
        override fun containsSymbolType(type: Int) = source.containsSymbolType(type)
        override fun hasBody() = source.hasBody()
        override fun currencyAsDecimal() = source.currencyAsDecimal()
    }
    private fun sourceProperties(p: PatternStringParserCore.PatternProperties): JProperties {
        val j = JProperties()
        for (field in p.javaClass.declaredFields) {
            field.isAccessible = true
            val value = when (val v = field.get(p)) { is UnitDecimal -> BigDecimal(v.toString()); is Padder.PadPosition -> JPadder.PadPosition.valueOf(v.name); else -> v }
            j.javaClass.methods.single { it.name == "set" + field.name.replaceFirstChar { it.uppercaseChar() } }.invoke(j, value)
        }
        return j
    }
    private fun portProperties(p: PatternStringParserCore.PatternProperties): DecimalFormatProperties {
        val result = DecimalFormatProperties()
        for (field in p.javaClass.declaredFields) {
            field.isAccessible = true
            val value = when (val v = field.get(p)) { is UnitDecimal -> BigDecimal(v.toString()); else -> v }
            result.javaClass.methods.single { it.name == "set" + field.name.replaceFirstChar { it.uppercaseChar() } }.invoke(result, value)
        }
        return result
    }
    private fun portSymbols(values: List<String>) = io.github.heapy.icu4k.text.DecimalFormatSymbols(io.github.heapy.icu4k.util.ULocale("en")).apply {
        setPercentString(values[0]); setPerMillString(values[1]); setDecimalSeparatorString(values[2]); setGroupingSeparatorString(values[3])
        setMinusSignString(values[4]); setPlusSignString(values[5]); setPatternSeparator(values[6][0]); setSignificantDigit(values[7][0])
        setExponentSeparator(values[8]); setPadEscape(values[9][0]); setDigit(values[10][0]); setDigitStrings(values.drop(11).toTypedArray())
    }
    @Test fun propertySerializationMatchesSourceForParsedAndUnconstrainedBags() {
        val properties = mutableListOf<PatternStringParserCore.PatternProperties>()
        for (pattern in listOf("", "0", "#,##0.00", "0.050", "@@##", "##0.0E+00", "¤0.00;(¤0.00)", "P0S;-P0S", "*'a''b'0")) properties.add(PatternStringParserCore.parseToProperties(pattern))
        val random = Random(8534)
        repeat(600) {
            properties.add(PatternStringParserCore.PatternProperties().apply {
                groupingSize = listOf(-1, 0, 2, 3, 110).random(random); secondaryGroupingSize = listOf(-1, 0, 2, 3, 110).random(random)
                groupingUsed = random.nextBoolean(); formatWidth = listOf(-1, 0, 8, 120).random(random)
                minimumIntegerDigits = random.nextInt(-3, 8); maximumIntegerDigits = listOf(-2, -1, 1, 6, 100, 120).random(random)
                minimumFractionDigits = random.nextInt(-3, 6); maximumFractionDigits = listOf(-2, -1, 0, 3, 100, 120).random(random)
                minimumSignificantDigits = random.nextInt(-3, 5); maximumSignificantDigits = listOf(-2, -1, 3, 7, 120).random(random)
                minimumExponentDigits = listOf(-2, -1, 0, 2, 120).random(random); exponentSignAlwaysShown = random.nextBoolean()
                decimalSeparatorAlwaysShown = random.nextBoolean(); currencyAsDecimal = random.nextBoolean()
                roundingIncrement = listOf(null, "0", "0.005", "0.006", "-1.25", "3E+2", "0.0500").random(random)?.let(::UnitDecimal)
                padString = listOf(null, "", "_", "ab", "'", "a'b", "😀").random(random)
                padPosition = (Padder.PadPosition.entries + listOf(null)).random(random)
                positivePrefixPattern = listOf(null, "", "P", "¤", "+", "'x'").random(random)
                positiveSuffixPattern = listOf(null, "", "%", "S").random(random)
                negativePrefixPattern = listOf(null, "-", "(", "-P").random(random)
                negativeSuffixPattern = listOf(null, "", ")", "S").random(random)
            })
        }
        for ((index, p) in properties.withIndex()) {
            val j = sourceProperties(p); val affixes = JProvider.forProperties(j)
            assertEquals(outcome { JUtils.propertiesToPatternString(j) }, outcome { PatternStringUtilsCore.propertiesToPatternString(p, adapter(affixes)) }, "bag $index")
            assertEquals(outcome { JUtils.propertiesToPatternString(j) }, outcome { PatternStringUtils.propertiesToPatternString(portProperties(p)) }, "public bag $index")
        }
        for (text in listOf("0", "0.005", "0.006", "0.0001", "-0.001", "1E-9999", "1E9999", "2.2250738585072014E-308")) for (max in listOf(-2, -1, 0, 1, 2, 3, 10, 100))
            assertEquals(JUtils.ignoreRoundingIncrement(BigDecimal(text), max), PatternStringUtilsCore.ignoreRoundingIncrement(UnitDecimal(text), max), "$text/$max")
    }
    private fun symbols(s: JSymbols): List<String> = listOf(s.percentString, s.perMillString, s.decimalSeparatorString, s.groupingSeparatorString,
        s.minusSignString, s.plusSignString, s.patternSeparator.toString(), s.significantDigit.toString(), s.exponentSeparator,
        s.padEscape.toString(), s.digit.toString()) + s.digitStrings.toList()
    @Test fun localizedNotationMatchesAllLocaleSymbolsAndCustomSymbolStrings() {
        val seen = mutableSetOf<List<String>>()
        val all = JLocale.getAvailableLocales().map { JSymbols(it) }.toMutableList()
        all.add(JSymbols(JLocale.ENGLISH).apply { percentString = "p'c"; minusSignString = "minus"; plusSignString = "plus"; exponentSeparator = "x10^" })
        val patterns = listOf(null, "", "#,##0.00;(#,##0.00)", "0.0E+00%", "'a''b'0", "'", "''", "abc,.-+@*#0123456789", "٪٫٬؜-٠١٢", "'x'%'y'‰", "x10^plus")
        for (s in all) {
            val values = symbols(s)
            if (!seen.add(values)) continue
            val port = portSymbols(values)
            for (pattern in patterns) for (toLocalized in listOf(false, true)) {
                assertEquals(outcome { JUtils.convertLocalized(pattern, s, toLocalized) }, outcome { PatternStringUtilsCore.convertLocalized(pattern, values::get, toLocalized) }, "$values/$pattern/$toLocalized")
                assertEquals(outcome { JUtils.convertLocalized(pattern, s, toLocalized) }, outcome { PatternStringUtils.convertLocalized(pattern, port, toLocalized) }, "public $values/$pattern/$toLocalized")
            }
        }
        assertTrue(seen.size > 10)
    }
    @Test fun signResolutionAndAffixRenderingMatchSource() {
        for (display in JSign.entries + listOf(null)) for (signum in JSignum.entries + listOf(null)) {
            assertEquals(outcome { JUtils.resolveSignDisplay(display, signum).name }, outcome { PatternStringUtils.resolveSignDisplay(display?.let { PatternStringUtilsCore.SignDisplay.valueOf(it.name) }, signum?.let { Modifier.Signum.valueOf(it.name) }).name }, "$display/$signum")
        }
        for (pattern in listOf("0", "+0", "0%;(0%)", "0;-0", "'x-y'0;(-0)", "¤0;¤-0", "0+;0-")) {
            val jp = JProvider.forProperties(JParser.parseToProperties(pattern)); val kp = adapter(jp)
            for (prefix in listOf(false, true)) for (sign in JUtils.PatternSignType.entries + listOf(null)) for (approx in listOf(false, true)) for (plural in JPlural.VALUES + listOf(null)) for (perMille in listOf(false, true)) {
                val jo = StringBuilder("seed"); val ko = StringBuilder("seed")
                assertEquals(outcome { JUtils.patternInfoToStringBuilder(jp, prefix, sign, approx, plural, perMille, jo) }, outcome { PatternStringUtils.patternInfoToStringBuilder(kp, prefix, sign?.let { PatternStringUtilsCore.PatternSignType.valueOf(it.name) }, approx, plural?.let { StandardPlural.valueOf(it.name) }, perMille, ko) })
                assertEquals(jo.toString(), ko.toString(), "$pattern/$prefix/$sign/$approx/$plural/$perMille")
            }
        }
    }
    @Test fun providerCallOrderAndPartialOutputOnFailureMatchSource() {
        val source = JProvider.forProperties(JParser.parseToProperties("P0S;(-0%)"))
        fun traced(trace: MutableList<String>, failure: String?): JAffix = object : JAffix {
            fun <T> call(name: String, body: () -> T): T {
                trace.add(name)
                if (name == failure) throw IllegalArgumentException("provider failure")
                return body()
            }
            override fun charAt(flags: Int, index: Int) = call("char:$index") { source.charAt(flags, index) }
            override fun length(flags: Int) = call("length") { source.length(flags) }
            override fun getString(flags: Int) = call("string") { source.getString(flags) }
            override fun hasCurrencySign() = source.hasCurrencySign()
            override fun positiveHasPlusSign() = call("plus") { source.positiveHasPlusSign() }
            override fun hasNegativeSubpattern() = call("negative") { source.hasNegativeSubpattern() }
            override fun negativeHasMinusSign() = call("minus") { source.negativeHasMinusSign() }
            override fun containsSymbolType(type: Int) = source.containsSymbolType(type)
            override fun hasBody() = source.hasBody()
            override fun currencyAsDecimal() = source.currencyAsDecimal()
        }
        for (failure in listOf(null, "plus", "negative", "minus", "length", "char:0", "char:1")) for (nullOutput in listOf(false, true)) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            val jo = StringBuilder("seed"); val ko = StringBuilder("seed")
            assertEquals(outcome { JUtils.patternInfoToStringBuilder(traced(jt, failure), true, JUtils.PatternSignType.POS_SIGN, true, JPlural.ONE, true, if (nullOutput) null else jo) },
                outcome { PatternStringUtils.patternInfoToStringBuilder(adapter(traced(kt, failure)), true, PatternStringUtilsCore.PatternSignType.POS_SIGN, true, StandardPlural.ONE, true, if (nullOutput) null else ko) }, "$failure/$nullOutput")
            assertEquals(jt, kt, "$failure/$nullOutput/trace")
            assertEquals(jo.toString(), ko.toString(), "$failure/$nullOutput/output")
        }
        assertEquals(outcome { JUtils.convertLocalized(null, null, true) }, outcome { PatternStringUtilsCore.convertLocalized(null, null, true) })
        assertEquals(outcome { JUtils.convertLocalized("", null, true) }, outcome { PatternStringUtilsCore.convertLocalized("", null, true) })
        assertEquals(outcome { JUtils.ignoreRoundingIncrement(null, 0) }, outcome { PatternStringUtilsCore.ignoreRoundingIncrement(null, 0) })
    }
}
