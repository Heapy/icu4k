// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

internal actual fun <K : Any, V : Any> createLocaleCacheStorage(initialCapacity: Int, loadFactor: Float, concurrencyLevel: Int): LocaleCacheStorage<K, V> =
    JvmLocaleCacheStorage(initialCapacity, loadFactor, concurrencyLevel)

internal class JvmLocaleCacheStorage<K : Any, V : Any>(capacity: Int, factor: Float, concurrency: Int) : LocaleCacheStorage<K, V> {
    private class Entry<K, V>(val key: K, value: V, queue: ReferenceQueue<V>) : SoftReference<V>(value, queue)
    private val map = ConcurrentHashMap<K, Entry<K, V>>(capacity, factor, concurrency)
    private val queue = ReferenceQueue<V>()
    override fun get(key: K): V? = map[key]?.get()
    override fun put(key: K, value: V) { map[key] = Entry(key, value, queue) }
    override fun cleanStaleEntries() {
        while (true) {
            @Suppress("UNCHECKED_CAST")
            val entry = queue.poll() as Entry<K, V>? ?: return
            map.remove(entry.key) // Keep original unconditional queued-key removal.
        }
    }
}
