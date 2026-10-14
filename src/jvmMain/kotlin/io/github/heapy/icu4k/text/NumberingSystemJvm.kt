package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun NumberingSystem.Companion.getInstance(locale: Locale?): NumberingSystem = getInstance(ULocale.forLocale(locale))
