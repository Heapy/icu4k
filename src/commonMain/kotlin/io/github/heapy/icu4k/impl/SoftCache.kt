// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2010-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Keeps key slots permanently; factory races return the published winner. */
abstract class SoftCache<K : Any, V : Any, D> : CacheBase<K, V, D>() {
    private val map = createCacheMap<K>()
    @Suppress("UNCHECKED_CAST")
    final override fun getInstance(key: K, data: D): V? {
        val previous = map.get(key)
        if (previous != null) {
            if (previous !is CacheValue<*>) return previous as V
            val cached = previous as CacheValue<V>
            if (cached.isNull()) return null
            cached.get()?.let { return it }
            return cached.resetIfCleared(createInstance(key, data))
        }
        val value = createInstance(key, data)
        val candidate = if (value != null && CacheValue.futureInstancesWillBeStrong()) value else CacheValue.getInstance(value)
        val winner = map.putIfAbsent(key, candidate) ?: return value
        if (winner !is CacheValue<*>) return winner as V
        return (winner as CacheValue<V>).resetIfCleared(value)
    }
}
internal interface CacheMap<K : Any> {
    fun get(key: K): Any?
    fun putIfAbsent(key: K, value: Any): Any?
}
internal expect fun <K : Any> createCacheMap(): CacheMap<K>
