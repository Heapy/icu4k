@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.text.PluralRules
import io.github.heapy.icu4k.impl.units.UnitDecimal
import java.math.BigDecimal
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class PluralRuleListCoreOracleTest {
    private val samplesParse = PluralRules.DecimalQuantitySamples::class.java.getDeclaredMethod("parse", String::class.java).apply { isAccessible = true }
    private fun sourceSamples(s: String) = samplesParse.invoke(null, s) as PluralRules.DecimalQuantitySamples
    private fun outcome(block: () -> Any?): String = try { block().toString() } catch (caught: Throwable) {
        val e = if (caught is InvocationTargetException) caught.targetException else caught
        when (e) {
            is java.text.ParseException -> "parse:${e.errorOffset}:${e.message}"
            is PluralConditionParseException -> "parse:${e.errorOffset}:${e.message}"
            is NumberFormatException -> "number"
            is IndexOutOfBoundsException -> "bounds"
            is IllegalArgumentException -> "argument:${e.message}"
            else -> e.javaClass.simpleName
        }
    }
    @Test fun sampleParsingAndExpansionMatchSource() {
        for (text in listOf("integer 0~3, 10, …", "decimal 0.0~0.3, 1.00~1.02", "decimal 1.0c3~1.2c3", "decimal 1c3, 2e3", "integer 1~1, 1, 1", "integer ...,...", "integer ,", "integer ~", "integer 1~", "integer ~1", "integer 1~~2", "integer 1.0", "decimal 1", "decimal 1.0~1.00", "decimal 3.0~1.0", "decimal -0.2~0.1", "integer 1, …, 2", "integer", "unknown 1", "integer1", "INTEGER 1")) {
            assertEquals(outcome { sourceSamples(text) }, outcome { PluralSamplesCore.parse(text) }, text)
            val j = try { sourceSamples(text) } catch (_: Exception) { continue }
            val k = PluralSamplesCore.parse(text)
            assertEquals(j.bounded, k.bounded, text)
            assertEquals(j.samples.size, k.samples.size, text)
            assertEquals(j.sampleType.name, k.sampleType.name, text)
            val jd = mutableListOf<Double>(); val kd = mutableListOf<Double>()
            j.addSamples(jd); k.addSamples(kd, null)
            assertEquals(jd, kd, text)
            val jq = mutableListOf<com.ibm.icu.impl.number.DecimalQuantity>(); val kq = mutableListOf<DecimalQuantityCore>()
            j.addDecimalQuantitySamples(jq); k.addSamples(null, kq)
            assertEquals(jq.map { it.toExponentString() }, kq.map { it.toExponentString() }, text)
            val je = linkedSetOf<com.ibm.icu.impl.number.DecimalQuantity>(); val ke = linkedSetOf<DecimalQuantityCore>()
            j.getStartEndSamples(je); k.getStartEndSamples(ke)
            assertEquals(je.map { it.toExponentString() }, ke.map { it.toExponentString() }, text)
        }
    }
    @Test fun ruleParsingSelectionAndBoundingMatchSource() {
        val descriptions = listOf("", " ", ";", ";;", "other:", "ONE: N IS 1; OTHER:", "other: @integer 0, 2, …; one: i=1 and v=0 @integer 1", "one:n=1; few:n=2..4 @integer 2~4 @decimal 2.0~2.2; other:", "one: n=1 @decimal 1.0 @integer 1", "one: n=1 @integer 1 @integer 2", "one: n=1 @integer 1 @decimal 1.0 @decimal 2.0", "one:n=1;one:n=2", "other:n=1", "one:", "one:or", ":n=1", "1:n=1", "ONE N=1", "one:n=1;", "one:n=1;;", "other: @", "one:n=1 @", "one:n=1; other: @decimal 0.0~0.2, …")
        for (text in descriptions) {
            assertEquals(outcome { PluralRules.parseDescription(text) }, outcome { PluralRuleListCore.parse(text) }, text)
            val j = try { PluralRules.parseDescription(text).also { it.toString() } } catch (_: Exception) { continue }
            val k = PluralRuleListCore.parse(text)
            assertEquals(j.keywords.toList(), k.getKeywords().toList(), text)
            for (keyword in listOf("one", "few", "other", "", "unknown")) {
                assertEquals(j.getRules(keyword), k.getRules(keyword), "$text/$keyword")
                for (type in PluralRules.SampleType.entries) {
                    val kt = PluralCondition.SampleType.valueOf(type.name)
                    assertEquals(j.computeLimited(keyword, type), k.computeLimited(keyword, kt), "$text/$keyword/$type")
                    assertEquals(j.isLimited(keyword, type), k.isLimited(keyword, kt), "$text/$keyword/$type")
                    assertEquals(j.getDecimalSamples(keyword, type)?.toString(), k.getDecimalSamples(keyword, kt)?.toString(), "$text/$keyword/$type")
                }
            }
            for (n in listOf(-1.0, 0.0, 1.0, 1.2, 2.0, 3.0, 4.0, 10.0, Double.NaN, Double.POSITIVE_INFINITY)) for (v in listOf(0, 1, 2)) {
                val jf = PluralRules.FixedDecimal(n, v); val kf = FixedDecimalCore(n, v)
                assertEquals(j.select(jf), k.select(kf::getPluralOperand, kf.isInfinite(), kf.isNaN()), "$text/$n/$v")
            }
        }
    }
    @Test fun decimalPointMovementMatchesBigDecimal() {
        for (value in listOf("0", "0E+8", "1E+3", "-1.234", "123456789012345678901234567890.000")) for (places in -12..12) {
            assertEquals(BigDecimal(value).movePointRight(places).toString(), UnitDecimal(value).movePointRight(places).toString(), "$value/$places")
        }
    }
    @Test fun allDistinctLocaleRuleSetsMatchSource() {
        val seen = mutableSetOf<String>()
        for (locale in PluralRules.getAvailableULocales()) for (type in PluralRules.PluralType.entries) {
            val j = PluralRules.forLocale(locale, type)
            val description = j.toString()
            if (!seen.add(description)) continue
            val k = PluralRuleListCore.parse(description)
            assertEquals(description, k.toString(), "$locale/$type")
            assertEquals(j.keywords.toList(), k.getKeywords().toList(), "$locale/$type")
            for (keyword in j.keywords) for (sampleType in PluralRules.SampleType.entries) {
                val kt = PluralCondition.SampleType.valueOf(sampleType.name)
                assertEquals(j.getDecimalSamples(keyword, sampleType)?.toString(), k.getDecimalSamples(keyword, kt)?.toString(), "$locale/$type/$keyword/$sampleType")
                assertEquals(j.isLimited(keyword, sampleType), k.isLimited(keyword, kt), "$locale/$type/$keyword/$sampleType")
                assertEquals(j.computeLimited(keyword, sampleType), k.computeLimited(keyword, kt), "$locale/$type/$keyword/$sampleType")
            }
            for (n in listOf(0.0, 1.0, 2.0, 3.0, 5.0, 11.0, 21.0, 100.0, 101.0, 1000000.0, 1.2, 2.01)) for (v in 0..3) for (e in listOf(0, 3, 6)) {
                val jf = PluralRules.FixedDecimal.createWithExponent(n, v, e)
                val kf = FixedDecimalCore.createWithExponent(n, v, e)
                assertEquals(j.select(jf), k.select(kf::getPluralOperand), "$locale/$type/$n/$v/$e")
            }
        }
        assertTrue(seen.size > 50, "Must exercise the ICU plural data corpus, not only a few locales")
    }
}
