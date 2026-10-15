// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.text.BreakIterator
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun UCharacter.Companion.toLowerCase(locale: Locale?, str: String?): String = toLowerCase(ULocale.forLocale(locale), str)
fun UCharacter.Companion.toUpperCase(locale: Locale?, str: String?): String = toUpperCase(ULocale.forLocale(locale), str)
fun UCharacter.Companion.toTitleCase(locale: Locale?, str: String?, titleIter: BreakIterator?): String = toTitleCase(locale, str, titleIter, 0)
fun UCharacter.Companion.toTitleCase(locale: Locale?, str: String?, titleIter: BreakIterator?, options: Int): String =
    toTitleCase(ULocale.forLocale(locale), str, titleIter, options)
