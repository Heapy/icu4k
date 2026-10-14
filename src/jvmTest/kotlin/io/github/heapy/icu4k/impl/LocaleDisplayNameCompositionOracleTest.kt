package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.DisplayContext
import io.github.heapy.icu4k.util.ULocale
import kotlin.random.Random
import kotlin.test.*

class LocaleDisplayNameCompositionOracleTest {
    @Test fun compoundNamesDialectPatternsAndKeywordsMatchSource() {
        val random = Random(7803)
        val languages = listOf("en", "fr", "zh", "sr", "sh", "iw", "de", "und", "zz")
        val scripts = listOf("", "Latn", "Cyrl", "Hans", "Hant")
        val regions = listOf("", "US", "GB", "CN", "TW", "RS", "FK", "XX")
        val keywords = listOf("", "@calendar=gregorian", "@currency=usd", "@currency=zzz", "@foo=bar", "@calendar=unknown;collation=phonebook;currency=EUR;numbers=latn", "@foo=bar(baz)")
        val ids = listOf("", "root", "en_US", "en_GB", "zh_Hant_TW", "sr_Latn_RS", "en_US_POSIX", "de__POSIX_REVISED", "@currency=USD", "und_US", "en_FK", "root@foo=bar", "sh_RS", "iw_IL") + List(80) {
            buildString {
                append(languages.random(random))
                val script = scripts.random(random); if (script.isNotEmpty()) append('_').append(script)
                val region = regions.random(random); if (region.isNotEmpty()) append('_').append(region)
                append(keywords.random(random))
            }
        }
        for (display in listOf("root", "en", "en_US", "fr", "fr_CA", "ar", "ja")) for (dialect in listOf(DisplayContext.STANDARD_NAMES, DisplayContext.DIALECT_NAMES)) for (length in listOf(DisplayContext.LENGTH_FULL, DisplayContext.LENGTH_SHORT)) for (substitute in listOf(DisplayContext.SUBSTITUTE, DisplayContext.NO_SUBSTITUTE)) for (cap in listOf(DisplayContext.CAPITALIZATION_NONE, DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)) {
            val contexts = arrayOf(dialect, length, substitute, cap)
            val original = contexts.map { com.ibm.icu.text.DisplayContext.valueOf(it.name) }.toTypedArray()
            val j = com.ibm.icu.impl.LocaleDisplayNamesImpl(com.ibm.icu.util.ULocale(display), *original)
            val k = LocaleDisplayNameComponents(ULocale(display), *contexts)
            for (id in ids) {
                assertEquals(j.localeDisplayName(id), k.localeDisplayName(id), "$display/${contexts.toList()}/$id")
                assertEquals(j.localeDisplayName(com.ibm.icu.util.ULocale(id)), k.localeDisplayName(ULocale(id)), "$display/$id/ULocale")
            }
        }
    }
}
