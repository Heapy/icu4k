// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun PluralFormat(locale: Locale?): PluralFormat = PluralFormat(ULocale.forLocale(locale))
fun PluralFormat(locale: Locale?, rules: PluralRules?): PluralFormat = PluralFormat(ULocale.forLocale(locale), rules)
fun PluralFormat(locale: Locale?, type: PluralRules.PluralType?): PluralFormat = PluralFormat(ULocale.forLocale(locale), type)
internal actual fun pluralFormatReadFields(input: PluralObjectInputStream) = input.defaultReadObject()
