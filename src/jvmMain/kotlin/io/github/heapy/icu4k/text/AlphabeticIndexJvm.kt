// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun <V> AlphabeticIndex(locale: Locale?): AlphabeticIndex<V> = AlphabeticIndex(ULocale.forLocale(locale))
fun <V> AlphabeticIndex<V>.addLabels(vararg additions: Locale?): AlphabeticIndex<V> {
    return addLabels(*additions.map { ULocale.forLocale(it) }.toTypedArray())
}
