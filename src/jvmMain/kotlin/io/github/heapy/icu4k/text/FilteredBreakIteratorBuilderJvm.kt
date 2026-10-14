package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun FilteredBreakIteratorBuilder.Companion.getInstance(locale: Locale?): FilteredBreakIteratorBuilder = getInstance(ULocale.forLocale(locale))
