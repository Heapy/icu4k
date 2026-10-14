package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.DisplayContext
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class LocaleDisplayNameComponentsOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) { e.javaClass.simpleName }
    @Test fun componentNamesContextsAndFallbackMatchSource() {
        for (id in listOf("root", "en", "en_US", "fr", "fr_CA", "ar", "ja")) for (length in listOf(DisplayContext.LENGTH_FULL, DisplayContext.LENGTH_SHORT)) for (substitute in listOf(DisplayContext.SUBSTITUTE, DisplayContext.NO_SUBSTITUTE)) for (capital in listOf(DisplayContext.CAPITALIZATION_NONE, DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE, DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU, DisplayContext.CAPITALIZATION_FOR_STANDALONE)) {
            val contexts = arrayOf(length, substitute, capital)
            val original = contexts.map { com.ibm.icu.text.DisplayContext.valueOf(it.name) }.toTypedArray()
            val j = com.ibm.icu.impl.LocaleDisplayNamesImpl(com.ibm.icu.util.ULocale(id), *original)
            val k = LocaleDisplayNameComponents(ULocale(id), *contexts)
            assertEquals(j.locale.name, k.locale.getName(), id)
            for (type in DisplayContext.Type.entries) assertEquals(j.getContext(com.ibm.icu.text.DisplayContext.Type.valueOf(type.name)).name, k.getContext(type).name)
            for (code in listOf("en", "de", "fr", "iw", "mo", "sh", "root", "en_US", "ROOT", "zz", "")) assertEquals(outcome { j.languageDisplayName(code) }, outcome { k.languageDisplayName(code) }, "$id/$code/$contexts")
            for (code in listOf(null, "Latn", "Hans", "Hant", "Zzzz", "missing")) {
                assertEquals(j.scriptDisplayName(code), k.scriptDisplayName(code))
                assertEquals(j.scriptDisplayNameInContext(code), k.scriptDisplayNameInContext(code))
            }
            for (code in listOf(null, "US", "GB", "CN", "MM", "BU", "ZZ", "missing")) assertEquals(j.regionDisplayName(code), k.regionDisplayName(code), "$id/$code")
            for (code in listOf(null, "POSIX", "REVISED", "missing")) assertEquals(j.variantDisplayName(code), k.variantDisplayName(code))
            for (key in listOf("calendar", "currency", "collation", "numbers", "missing")) {
                assertEquals(j.keyDisplayName(key), k.keyDisplayName(key))
                for (value in listOf("gregorian", "phonebook", "latn", "usd", "TWD", "unknown")) assertEquals(j.keyValueDisplayName(key, value), k.keyValueDisplayName(key, value), "$id/$key/$value")
            }
            for (code in listOf(-1, 0, 1, 25, 300)) assertEquals(outcome { j.scriptDisplayName(code) }, outcome { k.scriptDisplayName(code) })
        }
    }
}
