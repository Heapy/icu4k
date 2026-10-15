// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.concurrent.atomics.AtomicReference

internal object CurrencyService {
    private val published = AtomicReference<ICULocaleService?>(null)
    private val lock = createTextTrieMapLock(this)
    fun isInitialized(): Boolean = published.load() != null
    fun get(): ICULocaleService = published.load() ?: lock.withLock {
        published.load() ?: object : ICULocaleService("Currency") {
            init {
                registerFactory(object : ICUResourceBundleFactory() {
                    override fun handleCreate(loc: ULocale?, kind: Int, service: ICUService?): Any? = Currency.createCurrency(loc!!)
                })
                markDefault()
            }
        }.also { published.store(it) }
    }
    fun create(locale: ULocale): Currency? {
        val service = get()
        return if (service.isDefault()) Currency.createCurrency(locale) else service.get(locale) as Currency?
    }
    fun unregister(key: Any): Boolean = get().unregisterFactory(key as ICUService.Factory)
}
