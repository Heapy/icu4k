// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

/** GC-reclaimable locale cache. Concurrent misses may create distinct values, as in ICU4J. */
abstract class LocaleObjectCache<K : Any, V : Any> internal constructor(private val storage: LocaleCacheStorage<K, V>) {
    constructor() : this(16, 0.75f, 16)
    constructor(initialCapacity: Int, loadFactor: Float, concurrencyLevel: Int) :
        this(createLocaleCacheStorage(initialCapacity, loadFactor, concurrencyLevel))

    fun get(key: K): V? {
        storage.cleanStaleEntries()
        storage.get(key)?.let { return it }
        val normalized = normalizeKey(key) ?: return null
        storage.get(normalized)?.let { return it }
        val value = createObject(normalized) ?: return null
        storage.put(normalized, value)
        storage.cleanStaleEntries()
        return value
    }
    protected abstract fun createObject(key: K): V?
    protected open fun normalizeKey(key: K): K? = key
}

internal interface LocaleCacheStorage<K : Any, V : Any> {
    fun get(key: K): V?
    fun put(key: K, value: V)
    fun cleanStaleEntries()
}
internal expect fun <K : Any, V : Any> createLocaleCacheStorage(initialCapacity: Int, loadFactor: Float, concurrencyLevel: Int): LocaleCacheStorage<K, V>
internal fun validateLocaleCacheParameters(initialCapacity: Int, loadFactor: Float, concurrencyLevel: Int) {
    if (initialCapacity < 0 || !(loadFactor > 0) || concurrencyLevel <= 0) throw IllegalArgumentException()
}
