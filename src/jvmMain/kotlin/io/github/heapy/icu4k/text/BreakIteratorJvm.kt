package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale
import io.github.heapy.icu4k.impl.getAvailableLocales

fun BreakIterator.Companion.getWordInstance(where: Locale?): BreakIterator = getWordInstance(ULocale.forLocale(where))
fun BreakIterator.Companion.getLineInstance(where: Locale?): BreakIterator = getLineInstance(ULocale.forLocale(where))
fun BreakIterator.Companion.getCharacterInstance(where: Locale?): BreakIterator = getCharacterInstance(ULocale.forLocale(where))
fun BreakIterator.Companion.getSentenceInstance(where: Locale?): BreakIterator = getSentenceInstance(ULocale.forLocale(where))
@Deprecated("Use word iteration for titlecasing")
fun BreakIterator.Companion.getTitleInstance(where: Locale?): BreakIterator = getTitleInstance(ULocale.forLocale(where))

fun BreakIterator.Companion.registerInstance(iter: BreakIterator?, locale: Locale?, kind: Int): Any = registerInstance(iter, ULocale.forLocale(locale), kind)
fun BreakIterator.Companion.getAvailableLocales(): Array<Locale> = io.github.heapy.icu4k.impl.BreakIteratorService.get().getAvailableLocales()
