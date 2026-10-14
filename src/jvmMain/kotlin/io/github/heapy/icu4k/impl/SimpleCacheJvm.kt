package io.github.heapy.icu4k.impl

internal actual fun <V : Any> createSimpleCacheReference(value: V, weak: Boolean): CacheReference<V> {
    val reference: java.lang.ref.Reference<V> = if (weak) java.lang.ref.WeakReference(value) else java.lang.ref.SoftReference(value)
    return object : CacheReference<V> {
        override fun get(): V? = reference.get()
        override fun clear() = reference.clear()
    }
}
