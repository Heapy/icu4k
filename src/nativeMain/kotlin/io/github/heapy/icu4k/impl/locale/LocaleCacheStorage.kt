@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)
package io.github.heapy.icu4k.impl.locale

import kotlin.native.ref.WeakReference

internal interface LocaleCacheLock { fun <T> withLock(block: () -> T): T }
internal expect fun createLocaleCacheLock(): LocaleCacheLock
internal actual fun <K : Any, V : Any> createLocaleCacheStorage(initialCapacity: Int, loadFactor: Float, concurrencyLevel: Int): LocaleCacheStorage<K, V> {
    validateLocaleCacheParameters(initialCapacity, loadFactor, concurrencyLevel)
    return NativeLocaleCacheStorage(initialCapacity)
}
internal class NativeLocaleCacheStorage<K : Any, V : Any>(capacity: Int) : LocaleCacheStorage<K, V> {
    private class Entry<K : Any, V : Any>(val key: K, val reference: WeakReference<V>)
    private val lock = createLocaleCacheLock()
    private val map = HashMap<K, Entry<K, V>>(capacity)
    private val pending = ArrayDeque<Entry<K, V>>()
    override fun get(key: K): V? = lock.withLock { map[key]?.reference?.get() }
    override fun put(key: K, value: V) = lock.withLock {
        val entry = Entry(key, WeakReference(value))
        map[key] = entry
        pending.addLast(entry)
    }
    override fun cleanStaleEntries() = lock.withLock {
        // Native has no ReferenceQueue. Rotate a bounded batch to keep hits O(1)
        // and eventually remove dead keys without copying the map on publication.
        repeat(minOf(16, pending.size)) {
            val entry = pending.removeFirst()
            if (map[entry.key] === entry) {
                if (entry.reference.get() == null) map.remove(entry.key) else pending.addLast(entry)
            }
        }
    }
}
