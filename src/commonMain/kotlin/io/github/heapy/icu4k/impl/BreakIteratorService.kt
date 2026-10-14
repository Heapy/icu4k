// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.BreakIterator
import io.github.heapy.icu4k.text.StringCharacterIterator
import io.github.heapy.icu4k.util.ULocale
import kotlin.concurrent.atomics.AtomicReference

/** Shared service state behind BreakIterator's lazily loaded factory. */
internal object BreakIteratorService {
    private val published = AtomicReference<ICULocaleService?>(null)
    private val lock = createTextTrieMapLock(this)
    fun isInitialized(): Boolean = published.load() != null
    fun get(): ICULocaleService = published.load() ?: lock.withLock {
        published.load() ?: object : ICULocaleService("BreakIterator") {
            init {
                registerFactory(object : ICUResourceBundleFactory() {
                    override fun handleCreate(loc: ULocale?, kind: Int, service: ICUService?): Any = BreakIteratorDataFactory.create(loc!!, kind)
                })
                markDefault()
            }
            override fun validateFallbackLocale(): String = ""
        }.also { published.store(it) }
    }
    fun registerInstance(iterator: BreakIterator?, locale: ULocale?, kind: Int): Any {
        val service = get()
        iterator!!.setText(StringCharacterIterator(""))
        return service.registerObject(iterator, locale, kind)
    }
    fun unregister(key: Any): Boolean {
        val service = get()
        return if (service.isDefault()) false else service.unregisterFactory(key as ICUService.Factory)
    }
    fun create(locale: ULocale, kind: Int): BreakIterator {
        val service = get()
        if (service.isDefault()) return BreakIteratorDataFactory.create(locale, kind)
        val actual = arrayOfNulls<ULocale>(1)
        val result = service.get(locale, kind, actual) as BreakIterator
        result.setLocale(actual[0], actual[0])
        return result
    }
}
