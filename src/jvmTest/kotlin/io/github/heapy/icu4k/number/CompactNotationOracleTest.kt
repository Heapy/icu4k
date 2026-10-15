@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.number.CompactNotation as JNotation
import com.ibm.icu.number.Notation as JFactory
import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.number.NumberFormatter.SignDisplay as JSign
import com.ibm.icu.number.NumberFormatter.UnitWidth as JWidth
import com.ibm.icu.impl.number.CompactData.CompactType as JType
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.impl.number.MicroProps as JMicro
import com.ibm.icu.impl.number.MicroPropsGenerator as JGenerator
import com.ibm.icu.impl.number.MutablePatternModifier as JMutable
import com.ibm.icu.impl.number.PatternStringParser as JParser
import com.ibm.icu.impl.number.AffixPatternProvider as JProvider
import com.ibm.icu.impl.FormattedStringBuilder as JBuffer
import com.ibm.icu.text.NumberFormat.Field as JField
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.util.Currency as JCurrency
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CompactNotationOracleTest {
    private val method = JNotation::class.java.getDeclaredMethod("withLocaleData", JLocale::class.java,
        String::class.java, JType::class.java, JRules::class.java, JMutable::class.java,
        Boolean::class.javaPrimitiveType, JGenerator::class.java).apply { isAccessible = true }
    private fun handler(notation: JNotation, locale: String, type: JType, build: JMutable?, safe: Boolean,
        parent: JGenerator?): JGenerator = method.invoke(notation, JLocale(locale), "latn", type,
            JRules.forLocale(JLocale(locale)), build, safe, parent) as JGenerator

    private fun builders(locale: String, traceJ: MutableList<String>, traceK: MutableList<String>): Pair<JMutable, MutablePatternModifier> {
        val j = object : JMutable(true) {
            override fun setPatternInfo(info: JProvider?, field: JField?) {
                traceJ.add((info as JParser.ParsedPatternInfo).pattern)
                super.setPatternInfo(info, field)
            }
        }
        val k = object : MutablePatternModifier(true) {
            override fun setPatternInfo(patternInfo: AffixPatternProvider?, field: NumberFormatField?) {
                traceK.add((patternInfo as PatternStringParser.ParsedPatternInfo).pattern!!)
                super.setPatternInfo(patternInfo, field)
            }
        }
        j.setPatternInfo(JParser.parseToPatternInfo("0"), null)
        k.setPatternInfo(PatternStringParser.parseToPatternInfo("0"), null)
        j.setPatternAttributes(JSign.AUTO, false, false); k.setPatternAttributes(SignDisplay.AUTO, false, false)
        j.setSymbols(JSymbols(JLocale(locale)), JCurrency.getInstance("USD"), JWidth.SHORT, null)
        k.setSymbols(DecimalFormatSymbols(ULocale(locale)), Currency.getInstance("USD"), UnitWidth.SHORT, null)
        traceJ.clear(); traceK.clear()
        return j to k
    }

    private fun exercise(jn: JNotation, kn: CompactNotation, locale: String, type: JType, safe: Boolean) {
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        val (jb, kb) = builders(locale, jt, kt)
        var jm = JMicro(false); var km = MicroProps(false)
        val j = handler(jn, locale, type, jb, safe, JGenerator { jm })
        val k = kn.withLocaleData(ULocale(locale), "latn", CompactData.CompactType.valueOf(type.name),
            PluralRules.forLocale(ULocale(locale)), kb, safe, MicroPropsGenerator { km })
        assertEquals(jt, kt, "precompute order")
        for (number in listOf("0", "-0", "1", "12", "999", "999.5", "1000", "1200", "9999", "999500", "1000000", "-2100000", "1E30")) {
            for (mode in 0..2) {
                jm = JMicro(false); km = MicroProps(false)
                jm.modMiddle = com.ibm.icu.impl.number.ConstantAffixModifier("[", "]", JField.SIGN, false)
                km.modMiddle = ConstantAffixModifier("[", "]", NumberFormatField.SIGN, false)
                jm.rounder = when (mode) { 0 -> JPrecision.maxSignificantDigits(2); 1 -> JPrecision.fixedFraction(0); else -> JPrecision.unlimited() }
                km.rounder = when (mode) { 0 -> Precision.maxSignificantDigits(2); 1 -> Precision.fixedFraction(0); else -> Precision.unlimited() }
                val jq = JBCD(java.math.BigDecimal(number)); val kq = DecimalQuantity_DualStorageBCD(java.math.BigDecimal(number))
                val jr = j.processQuantity(jq); val kr = k.processQuantity(kq)!!
                assertSame(jm, jr); assertSame(km, kr)
                assertEquals(jq.toPlainString(), kq.toPlainString(), "$locale/$safe/$number/$mode quantity")
                assertEquals(jq.exponent, kq.getExponent())
                assertEquals(jr.rounder == null, kr.rounder == null)
                assertEquals(jr.modMiddle == null, kr.modMiddle == null)
                if (jr.modMiddle != null) {
                    val jo = JBuffer().apply { append("12", JField.INTEGER) }
                    val ko = FormattedStringBuilder().apply { append("12", NumberFormatField.INTEGER) }
                    assertEquals(jr.modMiddle.apply(jo, 0, 2), kr.modMiddle!!.apply(ko, 0, 2))
                    assertEquals(jo.toString(), ko.toString(), "$locale/$safe/$number/$mode modifier")
                    assertEquals(jo.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, ko.toFieldArray().map { it?.toString()?.substringAfterLast('(') })
                }
            }
        }
        assertEquals(jt, kt, "all pattern changes")
    }

    @Test fun resourceStylesCurrenciesAndRounding() {
        assertSame(Notation.compactShort(), Notation.compactShort())
        assertSame(Notation.compactLong(), Notation.compactLong())
        for (locale in listOf("en", "fr", "ru", "ar", "it", "ja", "so")) for (safe in listOf(false, true)) for (type in JType.entries) {
            exercise(JFactory.compactShort(), Notation.compactShort(), locale, type, safe)
            exercise(JFactory.compactLong(), Notation.compactLong(), locale, type, safe)
        }
    }

    @Test fun customDataIsLiveUntilHandlerCreationAndSupportsExactForms() {
        val data: MutableMap<String, Map<String, String>> = linkedMapOf("1000" to mapOf("other" to "0 old"))
        val j = JNotation.forCustomData(data); val k = CompactNotation.forCustomData(data)
        assertSame(data, k.compactCustomData)
        data["1000"] = mapOf("other" to "0K", "=1" to "'one thousand'", "one" to "0 k-one")
        data["1000000"] = mapOf("other" to "0M")
        for (safe in listOf(false, true)) exercise(j, k, "en", JType.DECIMAL, safe)
    }
    @Test fun specialValuesAndNullBuilderShortCircuits() {
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        for (safe in listOf(false, true)) {
            val (jb, kb) = builders("en", jt, kt)
            var jm = JMicro(false); var km = MicroProps(false)
            val j = handler(JFactory.compactShort(), "en", JType.DECIMAL, jb, safe, JGenerator { jm })
            val k = Notation.compactShort().withLocaleData(ULocale("en"), "latn", CompactData.CompactType.DECIMAL,
                PluralRules.forLocale(ULocale("en")), kb, safe, MicroPropsGenerator { km })
            for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.0, 0.0)) {
                jm = JMicro(false).apply { rounder = JPrecision.maxSignificantDigits(2) }
                km = MicroProps(false).apply { rounder = Precision.maxSignificantDigits(2) }
                val jq = JBCD(value); val kq = DecimalQuantity_DualStorageBCD(value)
                j.processQuantity(jq); k.processQuantity(kq)
                assertEquals(jq.toPlainString(), kq.toPlainString())
                assertEquals(jq.exponent, kq.getExponent())
                assertEquals(jq.signum().name, kq.signum().name)
                assertEquals(jm.modMiddle == null, km.modMiddle == null)
                assertNull(km.rounder)
            }
            // Empty custom data never dereferences the builder, even in the safe path.
            val jEmpty = JNotation.forCustomData(emptyMap())
            val kEmpty = CompactNotation.forCustomData(emptyMap())
            jm = JMicro(false).apply { rounder = JPrecision.unlimited() }
            km = MicroProps(false).apply { rounder = Precision.unlimited() }
            val je = handler(jEmpty, "en", JType.DECIMAL, null, safe, JGenerator { jm })
            val ke = kEmpty.withLocaleData(null, null, null, null, null, safe, MicroPropsGenerator { km })
            val jq = JBCD(1234); val kq = DecimalQuantity_DualStorageBCD(1234)
            je.processQuantity(jq); ke.processQuantity(kq)
            assertEquals(jq.toPlainString(), kq.toPlainString())
            assertNull(km.modMiddle)
        }
    }

    @Test fun failureAndParentEvaluationOrder() {
        fun outcome(action: () -> Any?): String = try { action(); "ok" } catch (e: Throwable) {
            var cause = e
            while (cause is java.lang.reflect.InvocationTargetException) cause = cause.targetException
            when (cause) {
                is NullPointerException -> "null"
                is AssertionError -> "assert"
                else -> cause.javaClass.simpleName + ":" + cause.message
            }
        }
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        val (jb, kb) = builders("en", jt, kt)
        for (safe in listOf(false, true)) for (nullParent in listOf(false, true)) for (nullResult in listOf(false, true)) {
            jt.clear(); kt.clear()
            val jp = if (nullParent) null else JGenerator { jt.add("parent"); if (nullResult) null else JMicro(false) }
            val kp = if (nullParent) null else MicroPropsGenerator { kt.add("parent"); if (nullResult) null else MicroProps(false) }
            val j = handler(JFactory.compactShort(), "en", JType.DECIMAL, jb, safe, jp)
            val k = Notation.compactShort().withLocaleData(ULocale("en"), "latn", CompactData.CompactType.DECIMAL,
                PluralRules.forLocale(ULocale("en")), kb, safe, kp)
            jt.clear(); kt.clear()
            assertEquals(outcome { j.processQuantity(JBCD(1234)) }, outcome { k.processQuantity(DecimalQuantity_DualStorageBCD(1234)) })
            assertEquals(jt, kt)
        }
        for (safe in listOf(false, true)) {
            assertEquals(outcome { handler(JNotation.forCustomData(null), "en", JType.DECIMAL, null, safe, null) },
                outcome { CompactNotation.forCustomData(null).withLocaleData(null, null, null, null, null, safe, null) })
            assertEquals(outcome { handler(JFactory.compactShort(), "en", JType.DECIMAL, null, safe, null) },
                outcome { Notation.compactShort().withLocaleData(ULocale("en"), "latn", CompactData.CompactType.DECIMAL, null, null, safe, null) })
        }
    }

}
