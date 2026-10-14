// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.toLocale
import java.util.Locale

/** Converts every locale, including scripts, and deduplicates by Java identity. */
fun ICUResourceBundle.Companion.getLocaleList(locales: Array<out ULocale?>?): Array<Locale> {
    val unique = LinkedHashSet<Locale>()
    for (locale in locales!!) unique.add(locale!!.toLocale())
    return unique.toTypedArray()
}

@JvmOverloads
fun ICUResourceBundle.Companion.getAvailableLocales(type: ULocale.AvailableType? = ULocale.AvailableType.DEFAULT): Array<Locale> =
    getAvailableLocalesWithLoader(ICUData.ICU_BASE_NAME, defaultICUResourceLoader(), type)

@JvmOverloads
fun ICUResourceBundle.Companion.getAvailableLocales(baseName: String?, loader: ClassLoader?,
    type: ULocale.AvailableType? = ULocale.AvailableType.DEFAULT): Array<Locale> =
    getAvailableLocalesWithLoader(baseName!!, loader?.let(::ICUClassLoaderResourceLoader), type)

@Suppress("UNCHECKED_CAST")
private fun ICUResourceBundle.Companion.getAvailableLocalesWithLoader(baseName: String, loader: ICUResourceLoader?,
    type: ULocale.AvailableType?): Array<Locale> =
    getAvailableJavaLocaleList(baseName, loader, type) { getLocaleList(it) } as Array<Locale>
