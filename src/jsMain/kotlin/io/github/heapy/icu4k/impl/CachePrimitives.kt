package io.github.heapy.icu4k.impl

import kotlin.js.jsTypeOf
import kotlin.js.unsafeCast

@JsName("WeakRef")
private external class JsValueWeakRef(value: dynamic) { fun deref(): dynamic }
internal actual fun <V : Any> createCacheReference(value: V?): CacheReference<V> = JsCacheReference(value)
private class JsCacheReference<V : Any>(value: V?) : CacheReference<V> {
    private val boxed = jsTypeOf(value) != "object" && jsTypeOf(value) != "function"
    private var reference: JsValueWeakRef? = if (value == null) null else {
        val target: dynamic
        if (boxed) { target = js("({})"); target.value = value } else target = value
        JsValueWeakRef(target)
    }
    override fun get(): V? {
        val target = reference?.deref()
        if (target == null) return null
        return (if (boxed) target.value else target).unsafeCast<V>()
    }
    override fun clear() { reference = null }
}
internal actual fun <K : Any> createCacheMap(): CacheMap<K> = object : CacheMap<K> {
    private val map = HashMap<K, Any>()
    override fun get(key: K): Any? = map[key]
    override fun putIfAbsent(key: K, value: Any): Any? {
        val previous = map[key]
        if (previous == null) map[key] = value
        return previous
    }
}
