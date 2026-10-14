package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import java.util.Locale

fun CaseMap.Lower.apply(locale: Locale?, src: CharSequence?): String = apply(ULocale.forLocale(locale), src)
fun <A : Appendable?> CaseMap.Lower.apply(locale: Locale?, src: CharSequence?, dest: A, edits: Edits?): A = apply(ULocale.forLocale(locale), src, dest, edits)
fun CaseMap.Upper.apply(locale: Locale?, src: CharSequence?): String = apply(ULocale.forLocale(locale), src)
fun <A : Appendable?> CaseMap.Upper.apply(locale: Locale?, src: CharSequence?, dest: A, edits: Edits?): A = apply(ULocale.forLocale(locale), src, dest, edits)
internal actual fun rethrowCaseMapException(failure: Exception): Nothing = throw if (failure is java.io.IOException) ICUUncheckedIOException(failure) else failure

fun CaseMap.Title.apply(locale: Locale?, iterator: BreakIterator?, src: CharSequence?): String = apply(ULocale.forLocale(locale), iterator, src)
fun <A : Appendable?> CaseMap.Title.apply(locale: Locale?, iterator: BreakIterator?, src: CharSequence?, dest: A, edits: Edits?): A = apply(ULocale.forLocale(locale), iterator, src, dest, edits)
