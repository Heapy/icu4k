// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.locale.localeReadOnlyMap
import io.github.heapy.icu4k.util.ULocale
import kotlin.concurrent.atomics.AtomicReference
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/** Service registry and lookup; sorted display-name enumeration is not yet ported. */
open class ICUService(@JvmField protected val name: String? = "") : ICUNotifier() {
    open class Key(private val identifier: String?) {
        fun id(): String? = identifier
        open fun canonicalID(): String? = identifier
        open fun currentID(): String? = canonicalID()
        open fun currentDescriptor(): String? = "/" + currentID()
        open fun fallback(): Boolean = false
        open fun isFallbackOf(idToCheck: String?): Boolean = canonicalID()!!.equals(idToCheck)
    }
    interface Factory {
        fun create(key: Key?, service: ICUService?): Any?
        fun updateVisibleIDs(result: MutableMap<String, Factory>)
        fun getDisplayName(id: String?, locale: ULocale?): String?
    }
    open class SimpleFactory @JvmOverloads constructor(instance: Any?, id: String?, @JvmField protected var visible: Boolean = true) : Factory {
        @JvmField protected var instance: Any?
        @JvmField protected var id: String?
        init {
            if (instance == null || id == null) throw IllegalArgumentException("Instance or id is null")
            this.instance = instance; this.id = id
        }
        override fun create(key: Key?, service: ICUService?): Any? = if (id!!.equals(key!!.currentID())) instance else null
        override fun updateVisibleIDs(result: MutableMap<String, Factory>) {
            if (visible) result[id!!] = this else result.remove(id)
        }
        override fun getDisplayName(id: String?, locale: ULocale?): String? = if (visible && this.id!!.equals(id)) id else null
        override fun toString(): String = "${super.toString()}, id: $id, visible: $visible"
    }
    private val factoryLock = ICURWLock()
    private val factories = ArrayList<Factory>()
    private var defaultSize = 0
    private val monitor = createTextTrieMapLock(this)
    private class CacheEntry(val actualDescriptor: String, val service: Any)
    private class ServiceCache {
        private val lock = createResourceCacheLock()
        private val entries = HashMap<String, CacheEntry>()
        fun get(key: String): CacheEntry? = lock.withLock { entries[key] }
        fun put(key: String, value: CacheEntry) = lock.withLock { entries[key] = value }
    }
    private val cache = AtomicReference<ServiceCache?>(null)
    private var idcache: Map<String, Factory>? = null
    open fun get(descriptor: String?): Any? = getKey(createKey(descriptor), null)
    open fun get(descriptor: String?, actualReturn: Array<String?>?): Any? {
        if (descriptor == null) throw NullPointerException("descriptor must not be null")
        return getKey(createKey(descriptor), actualReturn)
    }
    open fun getKey(key: Key?): Any? = getKey(key, null)
    open fun getKey(key: Key?, actualReturn: Array<String?>?): Any? = getKey(key, actualReturn, null)
    open fun getKey(key: Key?, actualReturn: Array<String?>?, factory: Factory?): Any? {
        if (factories.isEmpty()) return handleDefault(key, actualReturn)
        if (key != null) {
            factoryLock.acquireRead()
            try {
                val local = cache.load() ?: ServiceCache()
                val limit = factories.size
                var start = 0
                var cacheResult = true
                if (factory != null) {
                    val index = factories.indexOfFirst { it === factory }
                    if (index < 0) throw IllegalStateException("Factory ${factory}not registered with service: $this")
                    start = index + 1; cacheResult = false
                }
                val failed = ArrayList<String>()
                var putInCache = false
                var result: CacheEntry? = null
                do {
                    val descriptor = key.currentDescriptor()!!
                    result = local.get(descriptor)
                    if (result != null) break
                    putInCache = cacheResult
                    var index = start
                    while (index < limit) {
                        val service = factories[index++].create(key, this)
                        if (service != null) { result = CacheEntry(descriptor, service); break }
                    }
                    if (result != null) break
                    failed.add(descriptor)
                } while (key.fallback())
                if (result != null) {
                    if (putInCache) {
                        local.put(result.actualDescriptor, result)
                        for (descriptor in failed) local.put(descriptor, result)
                        cache.store(local)
                    }
                    if (actualReturn != null) checkedSetArrayElement(actualReturn, 0, result.actualDescriptor.removePrefix("/"))
                    return result.service
                }
            } finally { factoryLock.releaseRead() }
        }
        return handleDefault(key, actualReturn)
    }
    protected open fun handleDefault(key: Key?, actualIDReturn: Array<String?>?): Any? = null
    open fun getVisibleIDs(): Set<String> = getVisibleIDs(null)
    open fun getVisibleIDs(matchID: String?): Set<String> {
        val result = getVisibleIDMap().keys
        val key = createKey(matchID) ?: return result
        return result.filterTo(HashSet()) { key.isFallbackOf(it) }
    }
    private fun getVisibleIDMap(): Map<String, Factory> = monitor.withLock {
        idcache ?: run {
            factoryLock.acquireRead()
            try {
                val result = HashMap<String, Factory>()
                for (index in factories.lastIndex downTo 0) factories[index].updateVisibleIDs(result)
                localeReadOnlyMap(result).also { idcache = it }
            } finally { factoryLock.releaseRead() }
        }
    }
    open fun getDisplayName(id: String?): String? = getDisplayName(id, ULocale.getDefault(ULocale.Category.DISPLAY))
    open fun getDisplayName(id: String?, locale: ULocale?): String? {
        val map = getVisibleIDMap()
        map[id]?.let { return it.getDisplayName(id, locale) }
        val key = createKey(id)!!
        while (key.fallback()) map[key.currentID()]?.let { return it.getDisplayName(id, locale) }
        return null
    }
    fun factories(): MutableList<Factory> {
        factoryLock.acquireRead()
        try { return ArrayList(factories) } finally { factoryLock.releaseRead() }
    }
    open fun registerObject(obj: Any?, id: String?): Factory = registerObject(obj, id, true)
    open fun registerObject(obj: Any?, id: String?, visible: Boolean): Factory = registerFactory(SimpleFactory(obj, createKey(id)!!.canonicalID(), visible))
    fun registerFactory(factory: Factory?): Factory {
        val value = factory ?: throw NullPointerException()
        factoryLock.acquireWrite()
        try { factories.add(0, value); clearCaches() } finally { factoryLock.releaseWrite() }
        notifyChanged(); return value
    }
    fun unregisterFactory(factory: Factory?): Boolean {
        val value = factory ?: throw NullPointerException()
        factoryLock.acquireWrite()
        val removed: Boolean
        try { removed = factories.remove(value); if (removed) clearCaches() } finally { factoryLock.releaseWrite() }
        if (removed) notifyChanged()
        return removed
    }
    fun reset() {
        factoryLock.acquireWrite()
        try { reInitializeFactories(); clearCaches() } finally { factoryLock.releaseWrite() }
        notifyChanged()
    }
    protected open fun reInitializeFactories() { factories.clear() }
    open fun isDefault(): Boolean = factories.size == defaultSize
    protected open fun markDefault() { defaultSize = factories.size }
    open fun createKey(id: String?): Key? = id?.let(::Key)
    protected open fun clearCaches() { cache.store(null); idcache = null }
    protected open fun clearServiceCache() { cache.store(null) }
    interface ServiceListener : EventListener { fun serviceChanged(service: ICUService?) }
    override fun acceptsListener(listener: EventListener): Boolean = listener is ServiceListener
    override fun notifyListener(listener: EventListener) { (listener as ServiceListener).serviceChanged(this) }
    open fun stats(): String = factoryLock.resetStats()?.toString() ?: "no stats"
    open fun getName(): String? = name
    override fun toString(): String = "${super.toString()}{$name}"
}
