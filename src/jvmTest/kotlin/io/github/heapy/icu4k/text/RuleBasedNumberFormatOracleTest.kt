// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.RuleBasedNumberFormat as JFormat
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.math.BigDecimal as JDecimal
import com.ibm.icu.text.DisplayContext as JContext
import io.github.heapy.icu4k.math.BigDecimal as IcuDecimal
import io.github.heapy.icu4k.util.ULocale
import java.math.BigInteger
import java.math.BigDecimal
import java.util.Locale
import kotlin.test.*

class RuleBasedNumberFormatOracleTest {
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (e: Throwable) {
        when (e) { is NullPointerException -> "NPE"; is IndexOutOfBoundsException -> "bounds"; else -> "${e.javaClass.simpleName}:${e.message}" }
    }
    @Test fun allNumericOverloadsBuffersFieldsContextsAndFailures() {
        for (id in listOf("en", "fr", "ru", "ar", "zh_Hant")) {
            val s = JFormat(JLocale(id), 1)
            val t = RuleBasedNumberFormat(ULocale(id), 1)
            for (context in listOf(JContext.CAPITALIZATION_NONE, JContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE, JContext.CAPITALIZATION_FOR_STANDALONE)) {
                s.setContext(context); t.setContext(DisplayContext.valueOf(context.name))
                for (n in listOf(-1234L, Long.MIN_VALUE, Long.MAX_VALUE, 0L, 1L, 21L)) assertEquals(outcome { s.format(n) }, outcome { t.format(n) }, "$id $context long=$n")
                for (n in listOf(-1.235, -0.0, 1.235, 21.0, Double.NaN, Double.POSITIVE_INFINITY)) assertEquals(outcome { s.format(n) }, outcome { t.format(n) }, "$id $context double=$n")
                for (value in listOf("-9223372036854775808.1", "-9223372036854775808", "21", "21.0", "21.0001", "9223372036854775807", "9223372036854775807.1", "1E40")) {
                    val sd = JDecimal(value); val td = IcuDecimal(value)
                    for (prefix in listOf<String?>(null, "", "prefix ")) for (hasField in listOf(false, true)) {
                        val sb = prefix?.let(::StringBuffer); val tb = prefix?.let(::StringBuilder)
                        val sp = if (hasField) java.text.FieldPosition(0).also { it.beginIndex = 7; it.endIndex = 9 } else null
                        val tp = if (hasField) FormatFieldPosition(0).also { it.setBeginIndex(7); it.setEndIndex(9) } else null
                        assertEquals(outcome { s.format(sd, sb, sp) }, outcome { t.format(td, tb, tp) }, "$id $context decimal=$value prefix=$prefix field=$hasField")
                        assertEquals(sb?.toString(), tb?.toString())
                        assertEquals(sp?.beginIndex, tp?.getBeginIndex()); assertEquals(sp?.endIndex, tp?.getEndIndex())
                    }
                    assertEquals(outcome { s.format(BigDecimal(value)) }, outcome { t.format(BigDecimal(value)) })
                }
                for (value in listOf("-9223372036854775809", "0", "21", "9223372036854775808", "123456789012345678901234567890")) assertEquals(outcome { s.format(BigInteger(value)) }, outcome { t.format(BigInteger(value)) })
                for (prefix in listOf<String?>(null, "", "prefix ")) {
                    val sb = prefix?.let(::StringBuffer); val tb = prefix?.let(::StringBuilder)
                    assertEquals(outcome { s.format(21L, sb, null) }, outcome { t.format(21L, tb, null) })
                    assertEquals(sb?.toString(), tb?.toString())
                }
            }
        }
    }
    @Test fun constructorsMetadataNamesParsingAndJavaBridges() {
        val s = JFormat("%a: 0: one; %b: 0: two;", arrayOf(arrayOf("%a", "%b"), arrayOf("en", "One", "Two")), JLocale.ENGLISH)
        val t = RuleBasedNumberFormat("%a: 0: one; %b: 0: two;", arrayOf(arrayOf("%a", "%b"), arrayOf("en", "One", "Two")), ULocale.ENGLISH)
        assertEquals(s.toString(), t.toString()); assertContentEquals(s.ruleSetNames, t.getRuleSetNames())
        assertNull(t.getLocale(ULocale.ACTUAL_LOCALE))
        assertContentEquals(s.getRuleSetDisplayNames(JLocale.ENGLISH), t.getRuleSetDisplayNames(ULocale.ENGLISH))
        assertEquals(s.getRuleSetDisplayName("%a", JLocale.ENGLISH), t.getRuleSetDisplayName("%a", ULocale.ENGLISH))
        val jf = RuleBasedNumberFormat(Locale.US, RuleBasedNumberFormat.ORDINAL)
        assertEquals(JFormat(Locale.US, JFormat.ORDINAL).format(21L), jf.format(21L))
        assertEquals(JFormat("0: value", Locale.US).format(1L), RuleBasedNumberFormat("0: value", Locale.US).format(1L))
        val javaFormat: java.text.Format = t
        val sb = StringBuffer("prefix ")
        assertSame(sb, javaFormat.format(21L, sb, java.text.FieldPosition(0)))
        assertEquals("prefix one", sb.toString())
        for (text in listOf<String?>(null, "", "one", "two", "xxone!", "missing")) for (start in listOf(-1, 0, 2, 100)) {
            val sp = java.text.ParsePosition(start).also { it.errorIndex = 7 }
            val tp = java.text.ParsePosition(start).also { it.errorIndex = 7 }
            assertEquals(outcome { s.parseObject(text, sp) }, outcome { javaFormat.parseObject(text, tp) })
            assertEquals(sp.index, tp.index); assertEquals(sp.errorIndex, tp.errorIndex)
        }
    }
    @Test fun subclassNumericDispatchAndVirtualContextAndModeQueries() {
        val se = ArrayList<String>(); val te = ArrayList<String>()
        val s = object : JFormat("0: value", JLocale.ENGLISH) {
            override fun format(n: Long, out: StringBuffer?, p: java.text.FieldPosition?): StringBuffer { se.add("long"); return out!!.append("long") }
            override fun format(n: Double, out: StringBuffer?, p: java.text.FieldPosition?): StringBuffer { se.add("double"); return out!!.append("double") }
            override fun format(n: JDecimal?, out: StringBuffer?, p: java.text.FieldPosition?): StringBuffer { se.add("icu"); return super.format(n, out, p) }
        }
        val t = object : RuleBasedNumberFormat("0: value", ULocale.ENGLISH) {
            override fun format(number: Long, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder { te.add("long"); return output!!.append("long") }
            override fun format(number: Double, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder { te.add("double"); return output!!.append("double") }
            override fun format(number: IcuDecimal?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder { te.add("icu"); return super.format(number, output, position) }
        }
        for (v in listOf("21", "21.0", "9223372036854775808")) {
            se.clear(); te.clear()
            assertEquals(s.format(BigDecimal(v)), t.format(BigDecimal(v)))
            assertEquals(se, te, v)
        }
        val vs = object : JFormat("0: lower", JLocale.ENGLISH) {
            override fun getContext(type: JContext.Type?): JContext = JContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE
            override fun getRoundingMode() = 3
        }
        val vt = object : RuleBasedNumberFormat("0: lower", ULocale.ENGLISH) {
            override fun getContext(type: DisplayContext.Type?): DisplayContext = DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE
            override fun getRoundingMode() = 3
        }
        for (prefix in listOf("", "prefix ")) {
            assertEquals(outcome { vs.format(1.235, StringBuffer(prefix), null) }, outcome { vt.format(1.235, StringBuilder(prefix), null) })
        }
    }
    @Test fun scannerDiscoveryPublishesThroughVirtualSetterOnce() {
        var sourcePublishes = 0
        var targetPublishes = 0
        val source = object : JFormat("0: one", JLocale.ENGLISH) {
            override fun setLenientScannerProvider(provider: com.ibm.icu.text.RbnfLenientScannerProvider?) { ++sourcePublishes }
        }
        val target = object : RuleBasedNumberFormat("0: one", ULocale.ENGLISH) {
            override fun setLenientScannerProvider(provider: RbnfLenientScannerProvider?) { ++targetPublishes }
        }
        // Inject a present optional module into this test only; no ICU4J provider enters production.
        target.runtime = RbnfFormatterCore.fromDescription("0: one", locale = ULocale.ENGLISH, scannerProviderFactory = {
            object : RbnfLenientScannerProvider { override fun get(locale: ULocale?, extras: String?): RbnfLenientScanner? = null }
        })
        RuleBasedNumberFormat::class.java.getDeclaredMethod("bindOwner").also { it.isAccessible = true }.invoke(target)
        source.setLenientParseMode(true); target.setLenientParseMode(true)
        assertNull(source.getLenientScannerProvider()); assertNull(target.getLenientScannerProvider())
        assertEquals(1, sourcePublishes); assertEquals(sourcePublishes, targetPublishes)
        assertNull(source.getLenientScannerProvider()); assertNull(target.getLenientScannerProvider())
        assertEquals(1, targetPublishes)
    }

    @Test fun displayNamesAndLenientFallbackUseVirtualPublicGetters() {
        val sourceNames = arrayOf("%injected")
        val targetNames = arrayOf("%injected")
        val s = object : JFormat("0: one;", JLocale.ENGLISH) { override fun getRuleSetNames(): Array<String> = sourceNames }
        val t = object : RuleBasedNumberFormat("0: one;", ULocale.ENGLISH) { override fun getRuleSetNames(): Array<String> = targetNames }
        repeat(2) {
            assertContentEquals(s.getRuleSetDisplayNames(null as JLocale?), t.getRuleSetDisplayNames(null as ULocale?))
            assertContentEquals(sourceNames, targetNames) // The override's own returned array is modified in place.
        }
        val desc = "%main: 0: zero; 1: one; 100: << hundred >>;"
        val ls = object : JFormat(desc, JLocale.ENGLISH) { override fun lenientParseEnabled() = true }
        val lt = object : RuleBasedNumberFormat(desc, ULocale.ENGLISH) { override fun lenientParseEnabled() = true }
        val sp = java.text.ParsePosition(0); val tp = ParsePosition(0)
        assertEquals(ls.parse("2 hundred one", sp), lt.parse("2 hundred one", tp))
        assertEquals(sp.index, tp.index)
    }

}
