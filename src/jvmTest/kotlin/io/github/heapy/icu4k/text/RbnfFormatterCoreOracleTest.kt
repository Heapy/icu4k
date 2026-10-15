// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.RuleBasedNumberFormat as JFormatter
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.RbnfLenientScanner as JScanner
import com.ibm.icu.text.RbnfLenientScannerProvider as JProvider
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class RbnfFormatterCoreOracleTest {
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (e: Throwable) {
        val t = if (e is InvocationTargetException) e.targetException else e
        when (t) { is NullPointerException -> "NPE"; is IndexOutOfBoundsException -> "bounds"; is ClassCastException -> "cast"; else -> "${t.javaClass.simpleName}:${t.message}" }
    }
    private fun core(description: String?, localizations: Array<Array<String>>? = null, locale: ULocale? = ULocale.ENGLISH) =
        RbnfFormatterCore.fromDescription(description, localizations, locale, scannerProviderFactory = { error("unexpected discovery") })
    private fun compareState(s: JFormatter, t: RbnfFormatterCore, label: String) {
        assertEquals(s.toString(), t.toString(), label)
        assertContentEquals(s.ruleSetNames, t.getRuleSetNames(), label)
        assertEquals(s.defaultRuleSetName, t.getDefaultRuleSetName(), label)
        assertContentEquals(s.ruleSetDisplayNameLocales?.map { it.name }, t.getRuleSetDisplayNameLocales()?.map { it.getName() }, label)
        for (id in listOf<String?>(null, "", "en", "en_US", "fr_CA", "zz_ZZ")) {
            val sloc = id?.let(::JLocale)
            val tloc = id?.let(::ULocale)
            assertContentEquals(s.getRuleSetDisplayNames(sloc), t.getRuleSetDisplayNames(tloc), "$label display=$id")
            for (name in s.ruleSetNames.toList() + listOf(null, "%missing", "%%private")) {
                assertEquals(outcome { s.getRuleSetDisplayName(name, sloc) }, outcome { t.getRuleSetDisplayName(name, tloc) }, "$label name=$name loc=$id")
            }
        }
    }
    private fun compareParse(s: JFormatter, t: RbnfFormatterCore, text: String?, start: Int, label: String) {
        val sp = java.text.ParsePosition(start).also { it.errorIndex = 71 }
        val tp = ParsePosition(start).also { it.errorIndex = 71 }
        assertEquals(outcome { val n = s.parse(text, sp); "${n?.javaClass?.name}:$n" }, outcome {
            val n = t.parse(text, tp); "${n?.javaClass?.name}:$n"
        }, "$label text=$text index=$start")
        assertEquals(sp.index, tp.index, "$label text=$text index=$start")
        assertEquals(sp.errorIndex, tp.errorIndex, "$label text=$text index=$start")
    }
    @Test fun descriptionInitializationLocalizationAndFailureOrder() {
        val descriptions = listOf<String?>(null, "", "; ; ", "0: zero", "%x:", "%x: ", "%x: 0: x; %y: 0: y;", "%%private: 0: private;",
            "%duration: 0: duration; %digits-ordinal: 0: ordinal; %spellout-numbering: 0: spellout; %last: 0: last;",
            "%x: 0: first; %x@noparse: 0: second;", "%%lenient-parse: extras; more; %x: 0: x;", "%%post-process: missing; args; %x: 0: x;",
            "%x: 0: embedded %%post-process: marker;", "%%post-process:", "%%lenient-parse:", "%x: 0: x; %y: 0: y; %%private: 0: hidden;")
        val localizations = listOf<Array<Array<String>>?>(null, emptyArray(), arrayOf(emptyArray()), arrayOf(arrayOf("%x")),
            arrayOf(arrayOf("%x", "%y"), arrayOf("en", "X", "Y"), arrayOf("fr", "Un", "Deux")),
            arrayOf(arrayOf("%x"), arrayOf("en")), arrayOf(arrayOf("%missing")), arrayOf(arrayOf("%x"), emptyArray()),
            arrayOf(arrayOf("%x"), arrayOf("", "Root"), arrayOf("en", "English"), arrayOf("en", "last duplicate")))
        for (description in descriptions) for (names in localizations) {
            var source: JFormatter? = null
            var target: RbnfFormatterCore? = null
            val label = "$description / ${names?.contentDeepToString()}"
            assertEquals(outcome { source = JFormatter(description, names, JLocale.ENGLISH); null }, outcome { target = core(description, names); null }, label)
            if (source == null || target == null) continue
            val s = source!!; val t = target!!
            compareState(s, t, label)
            for (name in listOf<String?>("%x", "%%private", "%missing", null)) {
                assertEquals(outcome { s.setDefaultRuleSet(name); null }, outcome { t.setDefaultRuleSet(name); null }, "$label default=$name")
                assertEquals(s.defaultRuleSetName, t.getDefaultRuleSetName(), label)
                for (n in listOf(Long.MIN_VALUE, -1L, 0L, 21L)) assertEquals(outcome { s.format(n) }, outcome { t.format(n) }, "$label default=$name n=$n")
            }
            for (text in listOf<String?>(null, "", "x", "y", "hidden", "private", "mismatch")) for (start in listOf(-1, 0, 1, 100)) compareParse(s, t, text, start, label)
        }
    }
    @Test fun everyEmbeddedLocaleAndStyleBuildsTheSourceGraph() {
        val ids = ICUResourceBundle.getFullLocaleNameSet(ICUData.ICU_RBNF_BASE_NAME, ICUResourceBundleReader.embeddedLoader)
        assertEquals(111, ids.size)
        for (id in ids.sorted()) for (style in 1..4) {
            var source: JFormatter? = null
            var target: RbnfFormatterCore? = null
            val metadata = ArrayList<ULocale>()
            val label = "$id/$style"
            assertEquals(outcome { source = JFormatter(JLocale(id), style); null }, outcome {
                target = RbnfFormatterCore.fromLocale(ULocale(id), style, { error("unexpected discovery") }, ICUResourceBundleReader.embeddedLoader, { metadata.add(it) }); null
            }, label)
            if (source == null || target == null) continue
            val s = source!!; val t = target!!
            assertEquals(s.toString(), t.toString(), label)
            assertContentEquals(s.ruleSetNames, t.getRuleSetNames(), label)
            assertEquals(s.defaultRuleSetName, t.getDefaultRuleSetName(), label)
            assertEquals(s.getLocale(JLocale.ACTUAL_LOCALE).name, metadata.single().getName(), label)
            for (n in listOf(Long.MIN_VALUE, -1L, 0L, 1L, 21L, 101L, 1234L)) {
                assertEquals(outcome { s.format(n) }, outcome { t.format(n) }, "$label long=$n")
            }
            for (n in listOf(-0.0, 0.5, 1.25, Double.NaN, Double.POSITIVE_INFINITY)) {
                assertEquals(outcome { s.format(n) }, outcome { t.format(n) { s.maximumFractionDigits } }, "$label double=$n")
            }
        }
    }
    @Test fun namedRuleSetsWholeParsingRoundingAndShallowClone() {
        val description = RbnfTestEngine.ENGLISH + "%numeric: 0: =#,##0.00=; %%private: 0: hidden;"
        val s = JFormatter(description, JLocale.ENGLISH)
        val t = core(description)
        for (mode in 0..7) for (digits in listOf(0, 1, 2, 5)) {
            s.setRoundingMode(mode); t.setRoundingMode(mode); s.maximumFractionDigits = digits
            for (n in listOf(-123.456, -0.0, 0.05, 1.235, 123.456, Double.NaN, Double.POSITIVE_INFINITY)) for (name in s.ruleSetNames + arrayOf("%%private", "%missing")) {
                assertEquals(outcome { s.format(n, name) }, outcome { t.format(n, name) { digits } }, "mode=$mode digits=$digits n=$n set=$name")
            }
        }
        for (text in listOf<String?>(null, "", "one", "twenty-one", "xxone hundred two!", "123.45", "1,234.00", "hidden", "mismatch")) for (start in listOf(-1, 0, 2, 100)) compareParse(s, t, text, start, "whole parse")
        val sc = s.clone(); val tc = t.copy()
        assertTrue(s == sc); assertTrue(t.sameRulesAs(tc))
        sc.setDefaultRuleSet("%main"); tc.setDefaultRuleSet("%main")
        sc.setRoundingMode(3); tc.setRoundingMode(3)
        assertTrue(s == sc); assertTrue(t.sameRulesAs(tc))
        assertEquals(outcome { sc.format(123.456) }, outcome { tc.format(123.456) { sc.maximumFractionDigits } })
        val sd = JFormatter("%main: x.x: dot; x,x: comma; 0: zero;", JLocale.ENGLISH)
        val td = core("%main: x.x: dot; x,x: comma; 0: zero;")
        val sdc = sd.clone(); val tdc = td.copy()
        val ss = JSymbols(JLocale.ENGLISH).also { it.decimalSeparator = ','; it.infinity = "infinite" }
        val ts = DecimalFormatSymbols(ULocale.ENGLISH).also { it.setDecimalSeparator(','); it.setInfinity("infinite") }
        sdc.setDecimalFormatSymbols(ss); tdc.setDecimalFormatSymbols(ts)
        for (n in listOf(0.5, Double.POSITIVE_INFINITY)) assertEquals(sd.format(n), td.format(n) { 3 })
        for (n in listOf(0.5, Double.POSITIVE_INFINITY)) assertEquals(sdc.format(n), tdc.format(n) { 3 })
        sd.setDecimalFormatSymbols(ss); td.setDecimalFormatSymbols(ts)
        assertEquals(sdc.format(0.5), tdc.format(0.5) { 3 })
    }
    @Test fun providerCallbacksReceiveRulesAndRetainSourceDiscoveryState() {
        val description = "%%lenient-parse: extras; more; %main: 0: zero; 1: one;"
        val source = JFormatter(description, JLocale.ENGLISH)
        val target = core(description)
        val se = ArrayList<String>(); val te = ArrayList<String>()
        source.setLenientScannerProvider(object : JProvider {
            override fun get(locale: JLocale?, extras: String?): JScanner? { se.add("$locale:$extras"); return null }
        })
        target.setLenientScannerProvider(object : RbnfLenientScannerProvider {
            override fun get(locale: ULocale?, extras: String?): RbnfLenientScanner? { te.add("$locale:$extras"); return null }
        })
        source.setLenientParseMode(true); target.setLenientParseMode(true)
        compareParse(source, target, "one", 0, "lenient callbacks")
        assertEquals(se, te)
        source.setLenientParseMode(false); target.setLenientParseMode(false)
        assertEquals(source.lenientParseEnabled(), target.lenientParseEnabled())
        val broken = core("0: raw", locale = null)
        val sb = JFormatter("0: raw", null as JLocale?)
        assertEquals(outcome { sb == sb.clone() }, outcome { broken.sameRulesAs(broken.copy()) })
        val virtual = object : JFormatter("0: =0.00=;", JLocale.ENGLISH) { override fun getRoundingMode() = 3 }
        val tv = core("0: =0.00=;")
        tv.roundingModeReader = { 3 }
        assertEquals(outcome { virtual.format(1.235) }, outcome { tv.format(1.235) { 3 } })
    }
    @Test fun allPublicNamedSetsForRepresentativeLocalesAndStyles() {
        for (id in listOf("", "en", "fr", "ru", "ar", "zh_Hans", "zh_Hant")) for (style in 1..4) {
            val s = JFormatter(JLocale(id), style)
            val t = RbnfFormatterCore.fromLocale(ULocale(id), style, { error("unused discovery") }, ICUResourceBundleReader.embeddedLoader)
            for (name in s.ruleSetNames) {
                for (n in listOf(-1234L, 0L, 1L, 21L, 999L, 123456L)) {
                    assertEquals(outcome { s.format(n, name) }, outcome { t.format(n, name) }, "$id/$style/$name long=$n")
                }
                for (n in listOf(0.5, 1.25, Double.NaN, Double.POSITIVE_INFINITY)) {
                    assertEquals(outcome { s.format(n, name) }, outcome { t.format(n, name) { s.maximumFractionDigits } }, "$id/$style/$name double=$n")
                }
                // Parsing searches public sets in source order rather than restricting to the formatting set.
                val text = s.format(21L, name)
                compareParse(s, t, text + " trailing", 0, "$id/$style/$name")
            }
        }
    }

}
