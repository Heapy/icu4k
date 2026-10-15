@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.MutablePatternModifier as JMutable
import com.ibm.icu.impl.number.Modifier as JModifier
import com.ibm.icu.impl.number.MicroProps as JMicro
import com.ibm.icu.impl.number.MicroPropsGenerator as JGenerator
import com.ibm.icu.impl.number.DecimalQuantity as JQuantity
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.impl.number.PatternStringParser as JParser
import com.ibm.icu.impl.number.PropertiesAffixPatternProvider as JProvider
import com.ibm.icu.impl.number.ConstantAffixModifier as JAffix
import com.ibm.icu.impl.FormattedStringBuilder as JBuffer
import com.ibm.icu.impl.StandardPlural as JPlural
import com.ibm.icu.number.NumberFormatter.SignDisplay as JSign
import com.ibm.icu.number.NumberFormatter.UnitWidth as JWidth
import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.NumberFormat.Field as JField
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.number.SignDisplay
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import java.math.BigDecimal
import java.lang.reflect.Proxy
import kotlin.test.*

class MutablePatternModifierOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            is AssertionError -> "assert:${e.message}"
            else -> "${e.javaClass.simpleName}:${e.message}"
        }
    }
    private val js = JSymbols(JLocale.ENGLISH)
    private val ks = DecimalFormatSymbols(ULocale("en"))
    private fun pair(pattern: String, display: JSign? = JSign.AUTO, approx: Boolean = false, perMille: Boolean = false,
                     locale: String = "en", width: JWidth? = JWidth.SHORT, code: String = "USD"): Pair<JMutable, MutablePatternModifier> {
        val j = JMutable(true); val k = MutablePatternModifier(true)
        j.setPatternInfo(JProvider.forProperties(JParser.parseToProperties(pattern)), JField.SIGN)
        k.setPatternInfo(PropertiesAffixPatternProvider.forProperties(PatternStringParser.parseToProperties(pattern)), NumberFormatField.SIGN)
        j.setPatternAttributes(display, perMille, approx)
        k.setPatternAttributes(display?.let { SignDisplay.valueOf(it.name) }, perMille, approx)
        val sj = if (locale == "en") js else JSymbols(JLocale(locale))
        val sk = if (locale == "en") ks else DecimalFormatSymbols(ULocale(locale))
        j.setSymbols(sj, JCurrency.getInstance(code), width, if (j.needsPlurals()) JRules.forLocale(JLocale(locale)) else null)
        k.setSymbols(sk, Currency.getInstance(code), width?.let { UnitWidth.valueOf(it.name) }, if (k.needsPlurals()) PluralRules.forLocale(ULocale(locale)) else null)
        return j to k
    }
    private fun compare(j: JBuffer, k: FormattedStringBuilder, label: String) {
        assertEquals(j.toString(), k.toString(), label)
        assertEquals(j.toFieldArray().map { it?.toString()?.substringAfterLast('(') },
            k.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, label)
    }
    private fun apply(j: JModifier, k: Modifier, label: String) {
        val jb = JBuffer().apply { append("[12]", JField.INTEGER) }
        val kb = FormattedStringBuilder().apply { append("[12]", NumberFormatField.INTEGER) }
        assertEquals(outcome { j.apply(jb, 1, 3) }, outcome { k.apply(kb, 1, 3) }, label)
        compare(jb, kb, label)
        assertEquals(outcome { j.prefixLength }, outcome { k.getPrefixLength() }, "$label/length")
        assertEquals(outcome { j.codePointCount }, outcome { k.getCodePointCount() }, "$label/count")
        assertEquals(j.isStrong, k.isStrong())
    }
    @Test fun mutableAffixesAllSignsAndSymbolWidths() {
        val patterns = listOf("0", "+0", "0%;(0%)", "¤0;¤-0", "0¤¤", "¤¤¤0", "0¤¤¤", "¤¤¤¤0", "¤¤¤¤¤0",
            "'x-y'0;(-0)", "'😀'0", "'literal'", "0+;0-")
        for ((index, pattern) in patterns.withIndex()) for (display in JSign.entries) for (sign in JModifier.Signum.entries) for (approx in listOf(false, true)) {
            val (j, k) = pair(pattern, display, approx, index % 2 == 0)
            assertEquals(j.needsPlurals(), k.needsPlurals())
            val jp = if (j.needsPlurals()) JPlural.ONE else null
            val kp = if (k.needsPlurals()) StandardPlural.ONE else null
            j.setNumberProperties(sign, jp); k.setNumberProperties(Modifier.Signum.valueOf(sign.name), kp)
            apply(j, k, "$pattern/$display/$sign/$approx")
        }
        for (width in JWidth.entries + listOf(null)) for (type in -12..0) {
            val (j, k) = pair("¤¤¤0", width = width)
            j.setNumberProperties(JModifier.Signum.POS, JPlural.FEW)
            k.setNumberProperties(Modifier.Signum.POS, StandardPlural.FEW)
            assertEquals(outcome { j.getSymbol(type)?.toString() }, outcome { k.getSymbol(type)?.toString() }, "$width/$type")
            assertEquals(outcome { j.currencySymbolForUnitWidth }, outcome { k.getCurrencySymbolForUnitWidth() })
        }
    }
    @Test fun immutableAndMutablePipelineStagesMatchSource() {
        for (locale in listOf("en", "ru", "ar")) for (pattern in listOf("0%;(0%)", "¤0", "0 ¤¤¤", "'literal'")) {
            for (display in listOf(JSign.AUTO, JSign.ALWAYS, JSign.ACCOUNTING_EXCEPT_ZERO)) {
                val (j, k) = pair(pattern, display, locale = locale)
                val ji = j.createImmutable(); val ki = k.createImmutable()
                for (immutable in listOf(false, true)) for (existing in listOf(false, true)) for (number in listOf("-1.04", "0", "1.04", "2.56", "3", "11")) {
                    val jm = JMicro(true).apply { rounder = JPrecision.fixedFraction(1); if (existing) modMiddle = JAffix("existing", "", null, false) }
                    val km = MicroProps(true).apply { rounder = Precision.fixedFraction(1); if (existing) modMiddle = ConstantAffixModifier("existing", "", null, false) }
                    val jq = JBCD(BigDecimal(number)); val kq = DecimalQuantity_DualStorageBCD(BigDecimal(number))
                    val jg = if (immutable) ji.addToChain(jm) else j.addToChain(jm)
                    val kg = if (immutable) ki.addToChain(km) else k.addToChain(km)
                    val ja = jg.processQuantity(jq); val ka = kg.processQuantity(kq)!!
                    assertEquals(jq.toPlainString(), kq.toPlainString())
                    assertEquals(jm === ja, km === ka)
                    assertEquals(ja.modMiddle === j, ka.modMiddle === k)
                    if (existing) { assertSame(jm.modMiddle, ja.modMiddle); assertSame(km.modMiddle, ka.modMiddle) }
                    apply(ja.modMiddle, ka.modMiddle!!, "$locale/$pattern/$display/$immutable/$existing/$number")
                }
            }
        }
    }
    @Test fun immutableSnapshotsAndPreparedCurrencySpacingDifference() {
        val (j, k) = pair("¤0¤", width = JWidth.ISO_CODE)
        val ji = j.createImmutable(); val ki = k.createImmutable()
        val jq = JBCD(1); val kq = DecimalQuantity_DualStorageBCD(1)
        val jm = JMicro(true); val km = MicroProps(true)
        ji.applyToMicros(jm, jq); ki.applyToMicros(km, kq)
        apply(jm.modMiddle, km.modMiddle!!, "prepared")
        js.setPatternForCurrencySpacing(2, false, "changed"); ks.setPatternForCurrencySpacing(2, false, "changed")
        try {
            j.setNumberProperties(JModifier.Signum.POS, null); k.setNumberProperties(Modifier.Signum.POS, null)
            apply(j, k, "live-symbols")
            apply(jm.modMiddle, km.modMiddle!!, "snapshot")
            val jb = JBuffer().apply { append("1", JField.INTEGER) }; val kb = FormattedStringBuilder().apply { append("1", NumberFormatField.INTEGER) }
            assertEquals(j.apply(jb, 0, 1), k.apply(kb, 0, 1)); compare(jb, kb, "spacing length omitted")
            assertTrue(kb.length - 1 > k.getPrefixLength() + 3)
        } finally {
            js.setPatternForCurrencySpacing(2, false, "\u00a0"); ks.setPatternForCurrencySpacing(2, false, "\u00a0")
        }
    }
    @Test fun nullCurrencyStillEvaluatesSymbolLocaleAndHiddenWidthShortCircuits() {
        for (width in listOf(JWidth.SHORT, JWidth.ISO_CODE, JWidth.HIDDEN, null)) for (type in listOf(AffixUtils.TYPE_PERMILLE, AffixUtils.TYPE_CURRENCY_SINGLE, AffixUtils.TYPE_CURRENCY_DOUBLE, AffixUtils.TYPE_CURRENCY_TRIPLE, AffixUtils.TYPE_CURRENCY_QUAD, AffixUtils.TYPE_CURRENCY_QUINT)) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            val sj = object : JSymbols(JLocale.ENGLISH) { override fun getULocale(): JLocale { jt.add("locale"); return JLocale.ENGLISH } }
            val sk = object : DecimalFormatSymbols(ULocale("en")) { override fun getULocale(): ULocale { kt.add("locale"); return ULocale("en") } }
            val (j, k) = pair("¤¤¤0", width = width)
            j.setSymbols(sj, null, width, JRules.DEFAULT)
            k.setSymbols(sk, null, width?.let { UnitWidth.valueOf(it.name) }, PluralRules.DEFAULT)
            j.setNumberProperties(JModifier.Signum.POS, JPlural.ONE); k.setNumberProperties(Modifier.Signum.POS, StandardPlural.ONE)
            jt.clear(); kt.clear()
            assertEquals(outcome { j.getSymbol(type) }, outcome { k.getSymbol(type) }, "$width/$type")
            assertEquals(jt, kt, "$width/$type/trace")
        }
    }
    @Test fun assertionsNullsAndUnconfiguredState() {
        val j = JMutable(false); val k = MutablePatternModifier(false)
        assertEquals(outcome { j.needsPlurals() }, outcome { k.needsPlurals() })
        assertEquals(outcome { j.createImmutable() }, outcome { k.createImmutable() })
        assertEquals(outcome { j.setSymbols(null, null, null, null) }, outcome { k.setSymbols(null, null, null, null) })
        assertEquals(outcome { j.setNumberProperties(null, null) }, outcome { k.setNumberProperties(null, null) })
        assertEquals(outcome { j.containsField(null) }, outcome { k.containsField(null) })
        assertEquals(outcome { j.parameters }, outcome { k.getParameters() })
        assertEquals(outcome { j.strictEquals(null) }, outcome { k.strictEquals(null) })
        val (a, b) = pair("0")
        a.setNumberProperties(JModifier.Signum.POS, null); b.setNumberProperties(Modifier.Signum.POS, null)
        assertEquals(outcome { a.apply(null, 0, 1) }, outcome { b.apply(null, 0, 1) })
        assertEquals(outcome { a.processQuantity(null) }, outcome { b.processQuantity(null) })
        assertEquals(outcome { a.createImmutable().processQuantity(null) }, outcome { b.createImmutable().processQuantity(null) })
    }
    @Test fun rawPatternsCanOverwriteTheNumberBody() {
        for (pattern in listOf("'literal'", "'😀'", "¤", "%", "¤¤¤")) {
            val (j, k) = pair(pattern)
            j.setPatternInfo(JParser.parseToPatternInfo(pattern), null)
            k.setPatternInfo(PatternStringParser.parseToPatternInfo(pattern), null)
            val ji = j.createImmutable(); val ki = k.createImmutable()
            for (sign in listOf(JModifier.Signum.POS, JModifier.Signum.NEG)) {
                j.setNumberProperties(sign, if (j.needsPlurals()) JPlural.ONE else null)
                k.setNumberProperties(Modifier.Signum.valueOf(sign.name), if (k.needsPlurals()) StandardPlural.ONE else null)
                apply(j, k, "$pattern/raw/$sign")
                val jq = JBCD(if (sign == JModifier.Signum.NEG) -1 else 1)
                val kq = DecimalQuantity_DualStorageBCD(if (sign == JModifier.Signum.NEG) -1 else 1)
                val jm = JMicro(true); val km = MicroProps(true)
                ji.applyToMicros(jm, jq); ki.applyToMicros(km, kq)
                apply(jm.modMiddle, km.modMiddle!!, "$pattern/prepared-raw/$sign")
            }
        }
    }
    @Test fun nullMicrosFieldAssignmentEvaluatesQuantityFirstWithoutPluralRules() {
        for (pattern in listOf("0", "¤¤¤0")) {
            val (j, k) = pair(pattern)
            val ji = j.createImmutable(); val ki = k.createImmutable()
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            val jq = Proxy.newProxyInstance(JQuantity::class.java.classLoader, arrayOf(JQuantity::class.java)) { _, method, _ ->
                jt.add(method.name); if (method.name == "signum") JModifier.Signum.POS else error("Unexpected callback")
            } as JQuantity
            val kq = Proxy.newProxyInstance(DecimalQuantity::class.java.classLoader, arrayOf(DecimalQuantity::class.java)) { _, method, _ ->
                kt.add(method.name); if (method.name == "signum") Modifier.Signum.POS else error("Unexpected callback")
            } as DecimalQuantity
            assertEquals(outcome { ji.applyToMicros(null, jq) }, outcome { ki.applyToMicros(null, kq) })
            assertEquals(jt, kt)
            assertEquals(if (pattern == "0") listOf("signum") else emptyList(), kt)
        }
    }
    @Test fun symbolFailureLeavesInsertedPrefixInPlace() {
        val j = object : JMutable(false) { override fun getSymbol(type: Int): CharSequence? = throw IllegalStateException("symbol") }
        val k = object : MutablePatternModifier(false) { override fun getSymbol(type: Int): CharSequence? = throw IllegalStateException("symbol") }
        j.setPatternInfo(JParser.parseToPatternInfo("'P'0%"), null)
        k.setPatternInfo(PatternStringParser.parseToPatternInfo("'P'0%"), null)
        j.setPatternAttributes(JSign.AUTO, false, false); k.setPatternAttributes(SignDisplay.AUTO, false, false)
        j.setNumberProperties(JModifier.Signum.POS, null); k.setNumberProperties(Modifier.Signum.POS, null)
        val jb = JBuffer().apply { append("12", JField.INTEGER) }
        val kb = FormattedStringBuilder().apply { append("12", NumberFormatField.INTEGER) }
        assertEquals(outcome { j.apply(jb, 0, 2) }, outcome { k.apply(kb, 0, 2) })
        compare(jb, kb, "partial-prefix")
        assertEquals("P12", kb.toString())
    }
    @Test fun parentRoundingOrderAndExistingMiddleShortCircuit() {
        for (immutable in listOf(false, true)) for (existing in listOf(false, true)) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            val (j, k) = pair("¤¤¤0")
            val jr = object : JPrecision.BogusRounder() { override fun apply(value: JQuantity?) { jt.add("round"); value!!.roundToInfinity() } }
            val kr = object : Precision.BogusRounder() { override fun apply(value: DecimalQuantity?) { kt.add("round"); value!!.roundToInfinity() } }
            val jp = JGenerator { jt.add("parent"); JMicro(false).apply { rounder = jr; if (existing) modMiddle = JAffix() } }
            val kp = MicroPropsGenerator { kt.add("parent"); MicroProps(false).apply { rounder = kr; if (existing) modMiddle = ConstantAffixModifier() } }
            val jq = JBCD(BigDecimal("1.04")); val kq = DecimalQuantity_DualStorageBCD(BigDecimal("1.04"))
            val jg = if (immutable) j.createImmutable().addToChain(jp) else j.addToChain(jp)
            val kg = if (immutable) k.createImmutable().addToChain(kp) else k.addToChain(kp)
            jg.processQuantity(jq); kg.processQuantity(kq)
            assertEquals(jt, kt)
            assertEquals(if (existing) listOf("parent", "round") else listOf("parent", "round", "round"), kt)
        }
    }
}
