// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.defaultICUResourceLoader
import io.github.heapy.icu4k.impl.getAvailableLocales
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.toLocale
import java.util.Locale

@Suppress("UNCHECKED_CAST")
internal fun NumberFormatServiceCore.getAvailableLocales(): Array<Locale> = if (service.isDefault()) {
    ICUResourceBundle.getAvailableJavaLocaleList(ICUData.ICU_BASE_NAME, defaultICUResourceLoader(), ULocale.AvailableType.DEFAULT) { locales ->
        locales!!.map { it.toLocale() }.distinct().toTypedArray()
    } as Array<Locale>
} else service.getAvailableLocales()
