package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun ListFormatter.Companion.getInstance(locale: Locale?): ListFormatter = getInstance(ULocale.forLocale(locale))
fun ListFormatter.Companion.getInstance(locale: Locale?, type: ListFormatter.Type?, width: ListFormatter.Width?): ListFormatter = getInstance(ULocale.forLocale(locale), type, width)
internal actual fun listPatternIndex(index: Int): String = String.format("{%d}", index)
