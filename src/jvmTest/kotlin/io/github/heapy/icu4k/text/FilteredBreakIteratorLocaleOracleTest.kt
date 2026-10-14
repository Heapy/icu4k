package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.util.ULocale
import java.util.Locale
import kotlin.test.*

class FilteredBreakIteratorLocaleOracleTest {
    @Test fun everyLoadedSuppressionAndBoundaryMatchesSource() {
        for (id in listOf("en", "en_US", "fr", "fr_CA", "de", "es", "root", "zz_ZZ")) {
            val jlocale = com.ibm.icu.util.ULocale(id); val klocale = ULocale(id)
            val j = com.ibm.icu.text.FilteredBreakIteratorBuilder.getInstance(jlocale)
            val k = FilteredBreakIteratorBuilder.getInstance(klocale)
            val bundle = com.ibm.icu.impl.ICUResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_BRKITR_BASE_NAME, jlocale, com.ibm.icu.impl.ICUResourceBundle.OpenType.LOCALE_ROOT)
            val exceptions = bundle.findWithFallback("exceptions/SentenceBreak")
            val loaded = if (exceptions == null) emptyList() else (0 until exceptions.size).map { exceptions.get(it).string }
            val texts = listOf("Mr. Smith went home. Next.", "Mme. Dupont parle. Bonjour.", "Dr. Prof. Test. Done.", "", "A. B. C.") + loaded.map { "$it Name. Next." }
            val ji = j.wrapIteratorWithFilter(com.ibm.icu.text.BreakIterator.getSentenceInstance(com.ibm.icu.util.ULocale.ROOT))
            val ki = k.wrapIteratorWithFilter(RuleBasedBreakIterator.getInstanceFromCompiledRules(ICUBinary.getRequiredData("brkitr/sent.brk")))
            for (text in texts) {
                ji.setText(text); ki.setText(text)
                val expected = mutableListOf<Int>(); val actual = mutableListOf<Int>()
                var a = ji.first(); while (a != -1) { expected.add(a); a = ji.next() }
                var b = ki.first(); while (b != -1) { actual.add(b); b = ki.next() }
                assertEquals(expected, actual, "$id/$text")
                for (offset in 0..text.length) { assertEquals(ji.following(offset), ki.following(offset)); assertEquals(ji.preceding(offset), ki.preceding(offset)) }
            }
            for (abbreviation in loaded + listOf("missing.", "Mr.")) {
                assertEquals(j.suppressBreakAfter(abbreviation), k.suppressBreakAfter(abbreviation))
                assertEquals(j.unsuppressBreakAfter(abbreviation), k.unsuppressBreakAfter(abbreviation))
                assertEquals(j.unsuppressBreakAfter(abbreviation), k.unsuppressBreakAfter(abbreviation))
            }
        }
    }
    @Test fun javaLocaleAdapterLoadsSameSuppressionData() {
        val j = com.ibm.icu.text.FilteredBreakIteratorBuilder.getInstance(Locale.ENGLISH)
        val k = FilteredBreakIteratorBuilder.getInstance(Locale.ENGLISH)
        assertEquals(j.suppressBreakAfter("Mr."), k.suppressBreakAfter("Mr."))
    }
}
