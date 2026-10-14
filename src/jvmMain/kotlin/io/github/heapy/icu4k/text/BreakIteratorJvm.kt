package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun BreakIterator.Companion.getWordInstance(where: Locale?): BreakIterator = getWordInstance(ULocale.forLocale(where))
fun BreakIterator.Companion.getLineInstance(where: Locale?): BreakIterator = getLineInstance(ULocale.forLocale(where))
fun BreakIterator.Companion.getCharacterInstance(where: Locale?): BreakIterator = getCharacterInstance(ULocale.forLocale(where))
fun BreakIterator.Companion.getSentenceInstance(where: Locale?): BreakIterator = getSentenceInstance(ULocale.forLocale(where))
@Deprecated("Use word iteration for titlecasing")
fun BreakIterator.Companion.getTitleInstance(where: Locale?): BreakIterator = getTitleInstance(ULocale.forLocale(where))
