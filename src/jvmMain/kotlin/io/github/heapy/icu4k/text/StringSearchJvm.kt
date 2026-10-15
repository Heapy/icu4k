// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun StringSearch(pattern: String?, target: CharacterIterator?, locale: Locale?): StringSearch = StringSearch(pattern,target,ULocale.forLocale(locale))
