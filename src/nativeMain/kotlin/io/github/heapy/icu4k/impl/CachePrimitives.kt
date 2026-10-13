@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.locale.createLocaleCacheLock
import kotlin.native.ref.WeakReference

internal actual fun <V : Any> createCacheReference(value: V?): CacheReference<V> = NativeCacheReference(value)
private class NativeCacheReference<V : Any>(value: V?) : CacheReference<V> {
    private val reference = value?.let { WeakReference(it) }
    override fun get(): V? = reference?.get()
    override fun clear() { reference?.clear() }
}
internal actual fun <K : Any> createCacheMap(): CacheMap<K> = object : CacheMap<K> {
    private val lock = createLocaleCacheLock()
    private val map = HashMap<K, Any>()
    override fun get(key: K): Any? = lock.withLock { map[key] }
    override fun putIfAbsent(key: K, value: Any): Any? = lock.withLock {
        val previous = map[key]
        if (previous == null) map[key] = value
        previous
    }
}
