package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun NumberRangeFormatter.Companion.withLocale(locale: Locale?): LocalizedNumberRangeFormatter = NumberRangeFormatter.with().locale(locale)
fun UnlocalizedNumberRangeFormatter.locale(locale: Locale?): LocalizedNumberRangeFormatter = locale(ULocale.forLocale(locale))
