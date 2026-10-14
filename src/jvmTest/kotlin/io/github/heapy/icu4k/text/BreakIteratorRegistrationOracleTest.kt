@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import java.util.Locale
import kotlin.test.*
import com.ibm.icu.text.BreakIterator as JBreak
import com.ibm.icu.text.RuleBasedBreakIterator as JRules
import com.ibm.icu.util.ULocale as JLocale

class BreakIteratorRegistrationOracleTest {
    @Test fun registrationFallbackMetadataAndCacheMatchSource() {
        val id = "qaa_QQ@calendar=gregorian"
        val j = JRules("!!forward; .;"); val k = RuleBasedBreakIterator("!!forward; .;")
        j.setText("before"); k.setText("before")
        val jt = JBreak.registerInstance(j, JLocale(id), JBreak.KIND_WORD)
        val kt = BreakIterator.registerInstance(k, ULocale(id), BreakIterator.KIND_WORD)
        try {
            assertEquals(j.text.endIndex, k.getText().getEndIndex())
            assertEquals(JBreak.getAvailableULocales().map { it.name }.toSet(), BreakIterator.getAvailableULocales().map { it.getName() }.toSet())
            assertEquals(JBreak.getAvailableLocales().toSet(), BreakIterator.getAvailableLocales().toSet())
            for (request in listOf(id, id, "qaa_QQ", "qaa_QQ_VARIANT", "qaa", "qaa_QQ")) {
                val ji = JBreak.getWordInstance(JLocale(request)); val ki = BreakIterator.getWordInstance(ULocale(request))
                assertEquals(ji === j, ki === k, request)
                assertEquals(ji.getLocale(JLocale.ACTUAL_LOCALE)?.name, ki.getLocale(ULocale.ACTUAL_LOCALE)?.getName(), request)
                val text = "one two. 三四"
                ji.setText(text); ki.setText(text)
                val jb = ArrayList<Int>(); val kb = ArrayList<Int>()
                var p = ji.first(); while (p != -1) { jb.add(p); p = ji.next() }
                p = ki.first(); while (p != -1) { kb.add(p); p = ki.next() }
                assertEquals(jb, kb, request)
            }
        } finally { assertEquals(JBreak.unregister(jt), BreakIterator.unregister(kt)) }
        assertEquals(JBreak.unregister(jt), BreakIterator.unregister(kt))
    }

    private fun failure(block: () -> Any?): String = try { block(); "ok" } catch (e: Throwable) { e.javaClass.simpleName }
    @Test fun validationOrderAndForeignRegistryKeysMatchSource() {
        for (kind in listOf(-1, 0, 5)) {
            assertEquals(failure { JBreak.registerInstance(null, null as JLocale?, kind) }, failure { BreakIterator.registerInstance(null, null as ULocale?, kind) })
        }
        assertEquals(failure { JBreak.unregister(null) }, failure { BreakIterator.unregister(null) })
        val jt = JBreak.registerInstance(JRules("!!forward; .;"), Locale("qab", "QQ"), 2)
        val kt = BreakIterator.registerInstance(RuleBasedBreakIterator("!!forward; .;"), Locale("qab", "QQ"), 2)
        try { assertEquals(failure { JBreak.unregister("foreign") }, failure { BreakIterator.unregister("foreign") }) }
        finally { JBreak.unregister(jt); BreakIterator.unregister(kt) }
    }
}
