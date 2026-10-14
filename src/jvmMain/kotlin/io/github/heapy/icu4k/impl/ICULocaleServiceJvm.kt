// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import java.util.Locale

@Suppress("DEPRECATION")
fun ICULocaleService.getAvailableLocales(): Array<Locale> = getVisibleIDs().map { id ->
    val first = id.indexOf('_')
    if (first < 0) Locale(id, "", "") else {
        val second = id.indexOf('_', first + 1)
        if (second < 0) Locale(id.substring(0, first), id.substring(first + 1), "")
        else Locale(id.substring(0, first), id.substring(first + 1, second), id.substring(second + 1))
    }
}.toTypedArray()
