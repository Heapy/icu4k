package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun CurrencyPluralInfo(locale: Locale?): CurrencyPluralInfo = CurrencyPluralInfo(ULocale.forLocale(locale))
fun CurrencyPluralInfo.Companion.getInstance(locale: Locale?): CurrencyPluralInfo = getInstance(ULocale.forLocale(locale))
