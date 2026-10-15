// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.RuleBasedNumberFormat as JFormatter
import com.ibm.icu.text.DisplayContext as JContext
import com.ibm.icu.text.BreakIterator as JIterator
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class RbnfCapitalizationCoreOracleTest {
    private val adjust = JFormatter::class.java.getDeclaredMethod("adjustForContext", String::class.java).also { it.isAccessible = true }
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(owner)
    private fun sourceField(owner: JFormatter, name: String): Any? = JFormatter::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(owner)
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (e: Throwable) {
        val t = if (e is InvocationTargetException) e.targetException else e
        when (t) { is NullPointerException -> "NPE"; is IndexOutOfBoundsException -> "bounds"; else -> "${t.javaClass.simpleName}:${t.message}" }
    }
    private fun compareState(s: JFormatter, t: RbnfCapitalizationCore, label: String) {
        for ((source, target) in listOf("capitalizationInfoIsSet" to "infoIsSet", "capitalizationForListOrMenu" to "forListOrMenu", "capitalizationForStandAlone" to "forStandAlone")) {
            assertEquals(sourceField(s, source), field(t, target), "$label $source")
        }
        val si = sourceField(s, "capitalizationBrkIter") as JIterator?
        val ti = field(t, "iterator") as BreakIterator?
        assertEquals(si == null, ti == null, "$label iterator presence")
        if (si != null && ti != null) {
            assertEquals(si.current(), ti.current(), "$label iterator position")
            val a = si.text; val b = ti.getText()
            assertEquals(a.beginIndex, b.getBeginIndex(), "$label text begin")
            assertEquals(a.endIndex, b.getEndIndex(), "$label text end")
            assertEquals(a.index, b.getIndex(), "$label text position")
        }
    }
    @Test fun allRbnfLocalesLoadSourceContextDataAndAdjustExactText() {
        val ids = ICUResourceBundle.getFullLocaleNameSet(ICUData.ICU_RBNF_BASE_NAME, ICUResourceBundleReader.embeddedLoader)
        val texts = listOf<String?>(null, "", "one! two", "one. two", "One! two", " one", "1 one", "ǅungla", "ʰello", "ßeta", "istanbul Iİ", "ijsselmeer", "άδικος! άλλα", "\uD801\uDC28abc", "\u018d unchanged", "\uD800bad")
        for (id in ids.sorted()) {
            val source = JFormatter("%main: 0: one! two;", JLocale(id))
            val target = RbnfCapitalizationCore(ULocale(id))
            var parent = DisplayContext.CAPITALIZATION_NONE
            fun setParent(value: DisplayContext?) { if (value!!.type() == DisplayContext.Type.CAPITALIZATION) parent = value }
            for (context in DisplayContext.entries.toList() + listOf(null, DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE, DisplayContext.CAPITALIZATION_FOR_STANDALONE)) {
                val jc = context?.let { JContext.valueOf(it.name) }
                assertEquals(outcome { source.setContext(jc); null }, outcome { target.setContext(context, ::setParent); null }, "$id set=$context")
                compareState(source, target, "$id context=$context")
                for (text in texts) {
                    var sr: Any? = null
                    var tr: String? = null
                    assertEquals(outcome { sr = adjust.invoke(source, text); sr }, outcome { tr = target.adjustForContext(text) { parent }; tr }, "$id $context '$text'")
                    assertEquals(sr === text, tr === text, "$id $context '$text' identity")
                    compareState(source, target, "$id $context '$text'")
                }
            }
        }
    }
    @Test fun lazyVirtualContextAndShallowCloneShareTheCreatedIterator() {
        val source = object : JFormatter("0: lower;", JLocale.ENGLISH) {
            override fun getContext(type: JContext.Type?): JContext = JContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE
        }
        val target = RbnfCapitalizationCore(ULocale.ENGLISH)
        for (text in listOf<String?>(null, "", "Upper! lower", " lower", "lower! next")) {
            assertEquals(outcome { adjust.invoke(source, text) }, outcome { target.adjustForContext(text) { DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE } })
            compareState(source, target, "virtual '$text'")
        }
        val sc = source.clone()
        val tc = target.copy()
        assertSame(sourceField(source, "capitalizationBrkIter"), sourceField(sc, "capitalizationBrkIter"))
        assertSame(field(target, "iterator"), field(tc, "iterator"))
        assertEquals(outcome { adjust.invoke(sc, "clone! next") }, outcome { tc.adjustForContext("clone! next") { DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE } })
        compareState(sc, tc, "clone")
        compareState(source, target, "original observes shared iterator")
    }
    @Test fun realFormatterGraphRoutesAndLocaleSensitiveCasing() {
        for (id in listOf("en", "fr", "de", "tr", "nl", "el", "ru", "ar", "zh_Hant")) {
            val source = JFormatter(JLocale(id), JFormatter.SPELLOUT)
            val target = RbnfFormatterCore.fromLocale(ULocale(id), 1, { error("unused scanner") }, ICUResourceBundleReader.embeddedLoader)
            var parent = DisplayContext.CAPITALIZATION_NONE
            for (context in DisplayContext.entries.filter { it.type() == DisplayContext.Type.CAPITALIZATION }) {
                source.setContext(JContext.valueOf(context.name))
                target.setContext(context) { parent = it!! }
                for (number in listOf(-123L, 0L, 1L, 21L, 101L, 1234L)) {
                    assertEquals(source.format(number), target.adjustForContext(target.format(number)) { parent }, "$id $context $number")
                }
                val sc = source.clone(); val tc = target.copy()
                assertEquals(sc.format(21L), tc.adjustForContext(tc.format(21L)) { parent }, "$id clone $context")
            }
        }
    }
}
