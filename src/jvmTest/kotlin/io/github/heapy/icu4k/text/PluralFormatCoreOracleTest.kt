// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.PluralFormat as JPlural
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.text.DecimalFormat as JDecimal
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.MessagePattern as JPattern
import com.ibm.icu.text.NumberFormat as JNumber
import com.ibm.icu.text.RbnfLenientScanner as JScanner
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class PluralFormatCoreOracleTest {
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (e: Throwable) {
        val t = if (e is InvocationTargetException) e.targetException else e
        when (t) {
            is NullPointerException -> "NPE"
            is IndexOutOfBoundsException -> "bounds"
            is ClassCastException -> "cast"
            else -> "${t.javaClass.simpleName}:${t.message}"
        }
    }
    @Test fun localesPatternsRoundedOperandsAndNumberObjects() {
        val patterns = listOf(
            "=0{zero explicit}one{one #}two{two #}few{few #}many{many #}other{other #}",
            "offset:1 =2{explicit two}one{one # #}other{other #}",
            "offset:0.5 =1.5{explicit fraction}one{'#' #}other{# {n,number,'x' 0}}",
            "other{{n,plural,one{'#'}other{#}} '#': #}",
            "one{first one}one{second one}other{first other}other{second other}",
            "other{fallback}one{one}=1{explicit}", "=1{explicit}other{fallback}", "o{prefix accepted}",
        )
        val objects: List<Number> = listOf(0L, 1L, 2L, 3L, 11L, 21L, 101L, Long.MAX_VALUE,
            1.0, 1.04, 1.5, 1.99, 2.05, -1.0, -0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            BigDecimal("1.000"), BigDecimal("1.005"), BigDecimal("2.00000000000000000001"), BigInteger("123456789012345678901234567890"))
        for (locale in listOf("en", "ru", "pl", "fr", "ar", "lv")) for (type in JRules.PluralType.entries) {
            val s = JPlural(JLocale(locale), type)
            val rules = PluralRules.forLocale(ULocale(locale), PluralRules.PluralType.valueOf(type.name))
            for (decimalPattern in listOf("0", "0.0", "0.00", "0.0%", "0.0E0")) {
                s.setNumberFormat(JDecimal(decimalPattern, JSymbols(JLocale(locale))))
                val t = PluralFormatCore(rules, DecimalFormat(decimalPattern, DecimalFormatSymbols(ULocale(locale))))
                for (p in patterns) {
                    assertEquals(outcome { s.applyPattern(p) }, outcome { t.applyPattern(p) })
                    assertEquals(s.toPattern(), t.pattern)
                    for (n in objects) {
                        assertEquals(outcome { s.format(n, StringBuffer(), null) }, outcome { t.formatAny(n) }, "$locale $type $decimalPattern $p $n")
                        assertEquals(outcome { s.format(n.toDouble()) }, outcome { t.format(n.toDouble()) }, "$locale $type $decimalPattern $p double:$n")
                    }
                }
            }
        }
    }
    @Test fun quoteModesFailuresAndUnpatternedFormatting() {
        for (mode in JPattern.ApostropheMode.entries) {
            val s = JPlural(JLocale.ENGLISH)
            val t = PluralFormatCore(PluralRules.forLocale(ULocale.ENGLISH), DecimalFormat("0.0", DecimalFormatSymbols(ULocale.ENGLISH)))
            s.setNumberFormat(JDecimal("0.0", JSymbols(JLocale.ENGLISH)))
            assertEquals(s.format(1.0), t.format(1.0))
            s.applyPattern("other{x}"); t.applyPattern("other{x}")
            val sm = JPlural::class.java.getDeclaredField("msgPattern").apply { isAccessible = true }
            (sm.get(s) as JPattern).clearPatternAndSetApostropheMode(mode)
            t.msgPattern!!.clearPatternAndSetApostropheMode(MessagePattern.ApostropheMode.valueOf(mode.name))
            for (p in listOf("one{'#' #}other{'{name}' # {n,number,'x'}}", "other{don't #}", "other{{n,select,a{'a'}other{''}}}", "one{invalid}", "", "offset:1", "other{x}", null)) {
                assertEquals(outcome { s.applyPattern(p) }, outcome { t.applyPattern(p) })
                for (n in listOf(1.0, 1.5, 2.0)) assertEquals(outcome { s.format(n) }, outcome { t.format(n) }, "$mode $p")
                assertEquals(s.toPattern(), t.pattern)
            }
            s.setNumberFormat(null); t.numberFormat = null
            assertEquals(outcome { s.format(1.0) }, outcome { t.format(1.0) })
            for (x in listOf(null, "x", object { override fun toString() = "callback" })) {
                assertEquals(outcome { s.format(x, StringBuffer(), null) }, outcome { t.formatAny(x) })
            }
        }
    }
    @Test fun selectorEvaluationOrderOffsetAndExplicitPriority() {
        val selectorClass = Class.forName("com.ibm.icu.text.PluralFormat\$PluralSelector")
        val method = JPlural::class.java.getDeclaredMethod("findSubMessage", JPattern::class.java, Int::class.javaPrimitiveType, selectorClass, Any::class.java, Double::class.javaPrimitiveType).apply { isAccessible = true }
        for (p in listOf(
            "other{fallback}", "=1{explicit}one{one}other{fallback}", "one{one}=1{explicit}other{fallback}",
            "offset:1 other{fallback}one{one}=2{explicit}one{duplicate}",
            "offset:0.5 other{fallback}one{one}few{few}other{duplicate}", "o{prefix}",
        )) for (n in listOf(-0.0, 0.0, 1.0, 1.5, 2.0, Double.NaN, Double.POSITIVE_INFINITY)) for (keyword in listOf("one", "other", "few", "missing", null)) {
            val s = JPattern().parsePluralStyle(p)
            val t = MessagePattern().parsePluralStyle(p)
            val se = arrayListOf<Long>()
            val te = arrayListOf<Long>()
            val context = Any()
            val proxy = Proxy.newProxyInstance(selectorClass.classLoader, arrayOf(selectorClass)) { _, m, args ->
                assertEquals("select", m.name)
                assertSame(context, args!![0])
                se.add((args[1] as Double).toRawBits())
                keyword
            }
            val selector = PluralFormatCore.PluralSelector { c, num -> assertSame(context, c); te.add(num.toRawBits()); keyword }
            assertEquals(outcome { method.invoke(null, s, 0, proxy, context, n) }, outcome { PluralFormatCore.findSubMessage(t, 0, selector, context, n) }, "$p $n $keyword")
            assertEquals(se, te)
        }
        for (p in listOf("other{fallback}", "=1{explicit}other{fallback}")) {
            val s = JPattern().parsePluralStyle(p)
            val t = MessagePattern().parsePluralStyle(p)
            assertEquals(method.invoke(null, s, 0, null, null, 1.0), PluralFormatCore.findSubMessage(t, 0, null, null, 1.0))
        }
    }
    @Test fun parseTypePositionPolicyAndLenientScannerCalls() {
        val parse = JPlural::class.java.getDeclaredMethod("parseType", String::class.java, JScanner::class.java, java.text.FieldPosition::class.java).apply { isAccessible = true }
        val s = JPlural(JLocale.ENGLISH)
        val t = PluralFormatCore(null, null)
        fun compare(source: String?, start: Int, scanResult: IntArray?, scannerEnabled: Boolean) {
            val se = arrayListOf<String>()
            val te = arrayListOf<String>()
            val js = object : JScanner {
                override fun allIgnorable(s: String?) = false
                override fun prefixLength(str: String?, prefix: String?) = 0
                override fun findText(str: String?, key: String?, startingAt: Int): IntArray? { se.add("$str:$key:$startingAt"); return scanResult }
            }
            val ks = object : RbnfLenientScanner {
                override fun allIgnorable(s: String?) = false
                override fun prefixLength(str: String?, prefix: String?) = 0
                override fun findText(str: String?, key: String?, startingAt: Int): IntArray? { te.add("$str:$key:$startingAt"); return scanResult }
            }
            val sp = java.text.FieldPosition(0).also { it.beginIndex = start; it.endIndex = 77 }
            val tp = FormatFieldPosition(0).also { it.setBeginIndex(start); it.setEndIndex(77) }
            assertEquals(outcome { parse.invoke(s, source, if (scannerEnabled) js else null, sp) }, outcome { t.parseType(source, if (scannerEnabled) ks else null, tp) })
            assertEquals(sp.beginIndex, tp.getBeginIndex()); assertEquals(sp.endIndex, tp.getEndIndex())
            assertEquals(se, te)
        }
        compare(null, 0, null, false)
        for (p in listOf("one{a}two{long}other{other}", "offset:1 =1{explicit}one{a #}other{fallback}", "one{{x}}other{tail}", "one{}other{fallback}", "one{same}two{same}other{later}")) {
            s.applyPattern(p); t.applyPattern(p)
            for (text in listOf(null, "", "a long other", "other long a", "xx fallback tail later", "same")) for (start in listOf(-3, 0, 2, 99)) {
                compare(text, start, null, false)
                for (result in listOf(null, intArrayOf(), intArrayOf(-1, 0), intArrayOf(3, 100))) compare(text, start, result, true)
            }
        }
    }
    private class JProbe : JNumber() {
        var callback: () -> Unit = {}
        private fun emit(out: StringBuffer?): StringBuffer { callback(); return out!!.append("formatted") }
        override fun format(n: Long, out: StringBuffer?, p: java.text.FieldPosition?) = emit(out)
        override fun format(n: Double, out: StringBuffer?, p: java.text.FieldPosition?) = emit(out)
        override fun format(n: BigInteger?, out: StringBuffer?, p: java.text.FieldPosition?) = emit(out)
        override fun format(n: BigDecimal?, out: StringBuffer?, p: java.text.FieldPosition?) = emit(out)
        override fun format(n: com.ibm.icu.math.BigDecimal?, out: StringBuffer?, p: java.text.FieldPosition?) = emit(out)
        override fun parse(s: String?, p: java.text.ParsePosition?): Number? = throw UnsupportedOperationException()
    }
    private class KProbe : NumberFormat() {
        var callback: () -> Unit = {}
        private fun emit(out: StringBuilder?): StringBuilder { callback(); return out!!.append("formatted") }
        override fun format(number: Long, output: StringBuilder?, position: FormatFieldPosition?) = emit(output)
        override fun format(number: Double, output: StringBuilder?, position: FormatFieldPosition?) = emit(output)
        override fun format(number: BigInteger?, output: StringBuilder?, position: FormatFieldPosition?) = emit(output)
        override fun format(number: BigDecimal?, output: StringBuilder?, position: FormatFieldPosition?) = emit(output)
        override fun format(number: io.github.heapy.icu4k.math.BigDecimal?, output: StringBuilder?, position: FormatFieldPosition?) = emit(output)
        override fun parse(text: String?, position: ParsePosition?): Number? = throw UnsupportedOperationException()
    }
    @Test fun customFormatterCallbacksAndCloneSelectorOwner() {
        val sf = JProbe(); val tf = KProbe()
        val s = JPlural(JLocale.ENGLISH, JRules.parseDescription("one: n = 1"))
        s.setNumberFormat(sf)
        val t = PluralFormatCore(PluralRules.parseDescription("one: n = 1"), tf)
        val rules = JPlural::class.java.getDeclaredField("pluralRules").apply { isAccessible = true }
        s.applyPattern("one{one #}two{two #}other{other #}")
        t.applyPattern(s.toPattern())
        sf.callback = { rules.set(s, JRules.parseDescription("two: n = 1")) }
        tf.callback = { t.rules = PluralRules.parseDescription("two: n = 1") }
        assertEquals(s.format(1.0), t.format(1.0))
        sf.callback = {}; tf.callback = {}
        val sc = s.clone() as JPlural
        val tc = t.copy()
        rules.set(sc, JRules.parseDescription("one: n = 1")); tc.rules = PluralRules.parseDescription("one: n = 1")
        assertEquals(sc.format(1.0), tc.format(1.0))
        rules.set(s, JRules.parseDescription("one: n = 1")); t.rules = PluralRules.parseDescription("one: n = 1")
        assertEquals(sc.format(1.0), tc.format(1.0))
        sf.callback = { s.applyPattern("offset:2 other{changed #}") }
        tf.callback = { t.applyPattern("offset:2 other{changed #}") }
        assertEquals(s.format(3.0), t.format(3.0))
    }
}
