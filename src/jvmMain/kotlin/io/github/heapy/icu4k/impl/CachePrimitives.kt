package io.github.heapy.icu4k.impl

import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

internal actual fun <V : Any> createCacheReference(value: V?): CacheReference<V> = JvmCacheReference(value)
private class JvmCacheReference<V : Any>(value: V?) : CacheReference<V> {
    private val reference = SoftReference(value)
    override fun get(): V? = reference.get()
    override fun clear() { reference.clear() }
}
internal actual fun <K : Any> createCacheMap(): CacheMap<K> = object : CacheMap<K> {
    private val map = ConcurrentHashMap<K, Any>()
    override fun get(key: K): Any? = map[key]
    override fun putIfAbsent(key: K, value: Any): Any? = map.putIfAbsent(key, value)
}
