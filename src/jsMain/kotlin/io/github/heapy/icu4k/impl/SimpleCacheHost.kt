package io.github.heapy.icu4k.impl

internal actual fun <V : Any> createSimpleCacheReference(value: V, weak: Boolean): CacheReference<V> = createCacheReference(value)
