package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun CurrencyDisplayNames.Companion.getInstance(locale: Locale?): CurrencyDisplayNames? = getInstance(ULocale.forLocale(locale), false)
fun CurrencyDisplayNames.Companion.getInstance(locale: Locale?, noSubstitute: Boolean): CurrencyDisplayNames? = getInstance(ULocale.forLocale(locale), noSubstitute)
