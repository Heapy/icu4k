// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.getAvailableLocales
import io.github.heapy.icu4k.impl.coll.CollatorService
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun Collator.Companion.getInstance(locale: Locale?): Collator = getInstance(ULocale.forLocale(locale))
fun Collator.Companion.getAvailableLocales(): Array<Locale> {
    val service = CollatorService.current()
    return if (service == null || service.isDefault()) ICUResourceBundle.getAvailableLocales(ICUData.ICU_COLLATION_BASE_NAME,null)
        else service.getAvailableLocales()
}
fun Collator.Companion.getDisplayName(objectLocale: Locale?, displayLocale: Locale?): String? = getDisplayName(ULocale.forLocale(objectLocale),ULocale.forLocale(displayLocale))
fun Collator.Companion.getDisplayName(objectLocale: Locale?): String? = getDisplayName(ULocale.forLocale(objectLocale))
