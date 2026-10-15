package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun NumberFormatter.Companion.withLocale(locale: Locale?): LocalizedNumberFormatter = NumberFormatter.with().locale(locale)
fun UnlocalizedNumberFormatter.locale(locale: Locale?): LocalizedNumberFormatter = locale(ULocale.forLocale(locale))
