// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.concurrent.atomics.AtomicReference

/** ICULocaleService lookup/default-locale core, pending its locale factory facade. */
internal open class LocaleServiceCore(name: String? = "") : ICUService(name) {
    private val fallbackLocale = AtomicReference<ULocale?>(null)
    private var fallbackLocaleName: String? = null
    private val fallbackMonitor = createTextTrieMapLock(this)
    open fun validateFallbackLocale(): String? {
        val locale = ULocale.getDefault()
        if (locale !== fallbackLocale.load()) fallbackMonitor.withLock {
            if (locale !== fallbackLocale.load()) {
                fallbackLocaleName = locale.getBaseName()
                clearServiceCache()
                fallbackLocale.store(locale)
            }
        }
        return fallbackLocaleName
    }
    override fun createKey(id: String?): ICUService.Key? = LocaleServiceKey.createWithCanonicalFallback(id, validateFallbackLocale())
    open fun createKey(id: String?, kind: Int): ICUService.Key? = LocaleServiceKey.createWithCanonicalFallback(id, validateFallbackLocale(), kind)
    open fun createKey(locale: ULocale?, kind: Int): ICUService.Key? = LocaleServiceKey.createWithCanonical(locale, validateFallbackLocale(), kind)
    open fun get(locale: ULocale?): Any? = get(locale, LocaleServiceKey.KIND_ANY, null)
    open fun get(locale: ULocale?, kind: Int): Any? = get(locale, kind, null)
    open fun get(locale: ULocale?, actualReturn: Array<ULocale?>?): Any? = get(locale, LocaleServiceKey.KIND_ANY, actualReturn)
    open fun get(locale: ULocale?, kind: Int, actualReturn: Array<ULocale?>?): Any? {
        val key = createKey(locale, kind)
        if (actualReturn == null) return getKey(key)
        val actual = arrayOfNulls<String>(1)
        val result = getKey(key, actual)
        if (result != null) {
            var descriptor = actual[0]!!
            val separator = descriptor.indexOf('/')
            if (separator >= 0) descriptor = descriptor.substring(separator + 1)
            checkedSetLocaleArrayElement(actualReturn, 0, ULocale(descriptor))
        }
        return result
    }
}
internal expect fun checkedSetLocaleArrayElement(values: Array<ULocale?>, index: Int, value: ULocale)
