package io.github.heapy.icu4k.impl.locale

import kotlin.js.jsTypeOf
import kotlin.js.unsafeCast

@JsName("WeakRef")
private external class JsCacheWeakRef(target: dynamic) { fun deref(): dynamic }
@JsName("FinalizationRegistry")
private external class JsCacheFinalizationRegistry(cleanup: (dynamic) -> Unit) {
    fun register(target: dynamic, heldValue: dynamic, unregisterToken: dynamic)
    fun unregister(token: dynamic): Boolean
}
internal actual fun <K : Any, V : Any> createLocaleCacheStorage(initialCapacity: Int, loadFactor: Float, concurrencyLevel: Int): LocaleCacheStorage<K, V> {
    validateLocaleCacheParameters(initialCapacity, loadFactor, concurrencyLevel)
    return JsLocaleCacheStorage(initialCapacity)
}
private class JsLocaleCacheStorage<K : Any, V : Any>(capacity: Int) : LocaleCacheStorage<K, V> {
    private class Entry<K>(val key: K, val reference: JsCacheWeakRef, val boxed: Boolean)
    private val map = HashMap<K, Entry<K>>(capacity)
    private val stale = ArrayDeque<Entry<K>>()
    private val registry = JsCacheFinalizationRegistry { stale.addLast(it.unsafeCast<Entry<K>>()) }
    override fun get(key: K): V? {
        val entry = map[key] ?: return null
        val target = entry.reference.deref()
        if (target == null) return null
        return (if (entry.boxed) target.value else target).unsafeCast<V>()
    }
    override fun put(key: K, value: V) {
        val kind = jsTypeOf(value)
        val boxed = kind != "object" && kind != "function"
        val target: dynamic
        if (boxed) { target = js("({})"); target.value = value } else target = value
        val entry = Entry(key, JsCacheWeakRef(target), boxed)
        map.put(key, entry)?.let { registry.unregister(it) }
        registry.register(target, entry, entry)
    }
    override fun cleanStaleEntries() {
        while (stale.isNotEmpty()) map.remove(stale.removeFirst().key)
    }
}
