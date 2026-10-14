@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import java.util.Locale
import kotlin.test.*

class BreakIteratorPublicFactoryOracleTest {
    @Test fun publicFactoriesAndCachedSnapshotsMatch() {
        for (id in listOf("", "en-US", "en-US-posix", "fr", "el", "ja", "ko", "zh-Hant")) {
            val locale = Locale.forLanguageTag(id)
            for (kind in 0..4) repeat(2) {
                val j = when (kind) {
                    0 -> com.ibm.icu.text.BreakIterator.getCharacterInstance(locale)
                    1 -> com.ibm.icu.text.BreakIterator.getWordInstance(locale)
                    2 -> com.ibm.icu.text.BreakIterator.getLineInstance(locale)
                    3 -> com.ibm.icu.text.BreakIterator.getSentenceInstance(locale)
                    else -> com.ibm.icu.text.BreakIterator.getTitleInstance(locale)
                }
                val k = when (kind) {
                    0 -> BreakIterator.getCharacterInstance(locale)
                    1 -> BreakIterator.getWordInstance(locale)
                    2 -> BreakIterator.getLineInstance(locale)
                    3 -> BreakIterator.getSentenceInstance(locale)
                    else -> BreakIterator.getTitleInstance(locale)
                }
                assertEquals(j.getText().endIndex, k.getText().getEndIndex())
                assertEquals(j.getLocale(com.ibm.icu.util.ULocale.ACTUAL_LOCALE)?.name, k.getLocale(io.github.heapy.icu4k.util.ULocale.ACTUAL_LOCALE)?.getName())
                val text = "Hello. Next! 日本語 😀 a\u0301"
                j.setText(text); k.setText(text)
                var a = j.first(); var b = k.first()
                while (a != -1) { assertEquals(a, b, "$id/$kind"); a = j.next(); b = k.next() }
                assertEquals(a, b)
            }
        }
    }
}
