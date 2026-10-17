// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*

fun RelativeDateTimeFormatter.Companion.getInstance(locale: java.util.Locale?): RelativeDateTimeFormatter = getInstance(ULocale.forLocale(locale))
fun RelativeDateTimeFormatter.Companion.getInstance(locale: java.util.Locale?, nf: NumberFormat?): RelativeDateTimeFormatter = getInstance(ULocale.forLocale(locale), nf)
