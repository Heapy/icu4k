// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import kotlin.concurrent.atomics.AtomicReference

interface ICUCache<K, V> {
    fun clear()
    fun put(key: K, value: V)
    fun get(key: Any?): V?
    companion object { const val SOFT = 0; const val WEAK = 1; val NULL: Any = Any() }
}

/** Reclaimable reference to the entire synchronized map, as in ICU4J. */
class SimpleCache<K, V>(cacheType: Int = ICUCache.SOFT, initialCapacity: Int = 16) : ICUCache<K, V> {
    private val type = if (cacheType == ICUCache.WEAK) ICUCache.WEAK else ICUCache.SOFT
    private val capacity = if (initialCapacity > 0) initialCapacity else 16
    private val reference = AtomicReference<CacheReference<Store<K, V>>?>(null)
    private class Store<K, V>(capacity: Int) {
        private val lock = createResourceCacheLock()
        private val map = HashMap<K, V>(capacity)
        @Suppress("UNCHECKED_CAST")
        fun get(key: Any?): V? = lock.withLock { map[key as K] }
        fun put(key: K, value: V) = lock.withLock { map[key] = value }
    }
    override fun get(key: Any?): V? = reference.load()?.get()?.get(key)
    override fun put(key: K, value: V) {
        var store = reference.load()?.get()
        if (store == null) {
            store = Store(capacity)
            reference.store(createSimpleCacheReference(store, type == ICUCache.WEAK))
        }
        store.put(key, value)
    }
    override fun clear() { reference.store(null) }
}
internal expect fun <V : Any> createSimpleCacheReference(value: V, weak: Boolean): CacheReference<V>
