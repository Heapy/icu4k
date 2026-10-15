// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.NumberFormat as JNumber
import com.ibm.icu.text.DecimalFormat as JDecimal
import com.ibm.icu.text.NumberingSystem as JSystem
import com.ibm.icu.text.RuleBasedNumberFormat as JRuleBased
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class NumberFormatDataFactoryOracleTest {
    private val create = JNumber::class.java.getDeclaredMethod("createInstance", JLocale::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
    private val noAlgorithmic = NumberFormatDataFactory.AlgorithmicFactory { _, _, _ -> error("Unexpected algorithmic dispatch") }
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (e: Throwable) {
        val t = if (e is InvocationTargetException) e.targetException else e
        when (t) {
            is NullPointerException -> "NPE"
            is IndexOutOfBoundsException -> "bounds"
            else -> "${t.javaClass.simpleName}:${t.message}"
        }
    }
    private fun compare(s: JDecimal, t: DecimalFormat, label: String) {
        assertEquals(s.toPattern(), t.toPattern(), label)
        assertEquals(s.currency?.currencyCode, t.getCurrency()?.getCurrencyCode(), label)
        assertEquals(s.currencyUsage.name, t.getCurrencyUsage().name, label)
        assertEquals(s.isParseIntegerOnly, t.isParseIntegerOnly(), label)
        assertEquals(s.isDecimalSeparatorAlwaysShown, t.isDecimalSeparatorAlwaysShown(), label)
        assertEquals(s.maximumFractionDigits, t.getMaximumFractionDigits(), label)
        assertEquals(s.minimumFractionDigits, t.getMinimumFractionDigits(), label)
        assertEquals(s.maximumIntegerDigits, t.getMaximumIntegerDigits(), label)
        assertEquals(s.minimumIntegerDigits, t.getMinimumIntegerDigits(), label)
        assertEquals(s.roundingIncrement, t.getRoundingIncrement(), label)
        assertEquals(s.getLocale(JLocale.VALID_LOCALE)?.name, t.getLocale(ULocale.VALID_LOCALE)?.getName(), label)
        assertEquals(s.getLocale(JLocale.ACTUAL_LOCALE)?.name, t.getLocale(ULocale.ACTUAL_LOCALE)?.getName(), label)
        assertEquals(s.currencyPluralInfo?.locale?.name, t.getCurrencyPluralInfo()?.getLocale()?.getName(), label)
        for (n in listOf<Number>(0L, 1L, -1L, 12345L, 0.12345, 1.005, -1234.5, Double.NaN,
            Double.POSITIVE_INFINITY, -0.0, BigDecimal("123.4500"), BigInteger("123456789012345678901"))) {
            assertEquals(s.format(n), t.format(n), "$label $n")
        }
        for (text in listOf("12.5", "1,234.50", "12%", "USD 1.23", s.format(1234.5))) {
            val sp = java.text.ParsePosition(0)
            val tp = ParsePosition(0)
            assertEquals(outcome { s.parse(text, sp) }, outcome { t.parse(text, tp) }, "$label parse:$text")
            assertEquals(sp.index, tp.index, label); assertEquals(sp.errorIndex, tp.errorIndex, label)
        }
    }
    @Test fun allNumericStylesLocalesAndCurrencyOverrides() {
        val locales = listOf("", "en_US", "en_US@cf=account", "en_US@cf=standard", "en_US@currency=JPY", "fr_FR",
            "de_CH@currency=CHF", "ar", "fa", "hi_IN", "bn_BD", "zh_Hant_TW", "en_US@numbers=arab",
            "en_US@numbers=mathbold", "fa@numbers=latn", "en_US@numbers=not-a-system", "zz_ZZ")
        for (locale in locales) for (style in 0..9) {
            val s = create.invoke(null, JLocale(locale), style) as JDecimal
            val t = NumberFormatDataFactory.create(ULocale(locale), style, noAlgorithmic) as DecimalFormat
            compare(s, t, "$locale/$style")
        }
    }
    @Test fun everyNumericDigitSystem() {
        for (name in JSystem.getAvailableNames()) {
            if (JSystem.getInstanceByName(name).isAlgorithmic) continue
            val id = "en_US@numbers=$name"
            for (style in listOf(0, 1, 3, 4, 5, 6, 8)) {
                val s = create.invoke(null, JLocale(id), style) as JDecimal
                val t = NumberFormatDataFactory.create(ULocale(id), style, noAlgorithmic) as DecimalFormat
                compare(s, t, "$id/$style")
            }
        }
    }
    @Test fun allAlgorithmicSystemsDispatchToTheSameSourceRules() {
        val locale = JRuleBased::class.java.getDeclaredField("locale").apply { isAccessible = true }
        for (name in JSystem.getAvailableNames()) {
            if (!JSystem.getInstanceByName(name).isAlgorithmic) continue
            val id = "en_US@numbers=$name"
            for (style in listOf(0, 1, 4, 6, 8)) {
                var expected: JRuleBased? = null
                var actualRules: JRuleBased? = null
                var target: NumberFormat? = null
                var calls = 0
                val placeholder = DecimalFormat("0", DecimalFormatSymbols(ULocale.FRENCH))
                placeholder.setLocale(ULocale.FRENCH, ULocale.FRENCH)
                val expectedError = outcome { expected = create.invoke(null, JLocale(id), style) as JRuleBased; "created" }
                val actualError = outcome {
                    target = NumberFormatDataFactory.create(ULocale(id), style) { nsLocale, type, ruleSet ->
                        calls++
                        assertEquals(ULocale.FRENCH, placeholder.getLocale(ULocale.ACTUAL_LOCALE))
                        // ICU4J remains a test oracle: the production creator only dispatches to its callback.
                        actualRules = JRuleBased(nsLocale?.let { JLocale(it.getName()) }, type).also { it.setDefaultRuleSet(ruleSet) }
                        placeholder
                    }
                    "created"
                }
                assertEquals(expectedError, actualError, "$id/$style")
                assertEquals(1, calls)
                if (expected != null) {
                    assertSame(placeholder, target)
                    assertEquals(expected!!.toString(), actualRules!!.toString(), "$id/$style rules")
                    assertEquals(expected!!.defaultRuleSetName, actualRules!!.defaultRuleSetName, "$id/$style default")
                    assertEquals((locale.get(expected) as JLocale?)?.name, (locale.get(actualRules) as JLocale?)?.name, "$id/$style locale")
                    assertEquals(expected!!.getLocale(JLocale.ACTUAL_LOCALE)?.name, target!!.getLocale(ULocale.ACTUAL_LOCALE)?.getName())
                    assertEquals(expected!!.getLocale(JLocale.VALID_LOCALE)?.name, target!!.getLocale(ULocale.VALID_LOCALE)?.getName())
                }
            }
        }
    }
    @Test fun invalidInputsAndPublicPatternMethodsMatchSource() {
        for (style in listOf(-1, 0, 1, 10, Int.MAX_VALUE)) {
            assertEquals(outcome { create.invoke(null, null, style) }, outcome { NumberFormatDataFactory.create(null, style, noAlgorithmic) })
            if (style !in 0..9) {
                assertEquals(outcome { create.invoke(null, JLocale.ENGLISH, style) }, outcome { NumberFormatDataFactory.create(ULocale.ENGLISH, style, noAlgorithmic) })
            }
        }
        val target = NumberFormat::class.java.getDeclaredMethod("getPatternForStyle", ULocale::class.java, Int::class.javaPrimitiveType)
        assertEquals(String::class.java, target.returnType)
        val withNS = NumberFormat::class.java.getDeclaredMethod("getPatternForStyleAndNumberingSystem", ULocale::class.java, String::class.java, Int::class.javaPrimitiveType)
        assertEquals(String::class.java, withNS.returnType)
    }
}
