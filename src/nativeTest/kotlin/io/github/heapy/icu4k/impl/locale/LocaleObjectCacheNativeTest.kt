@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlin.native.concurrent.ObsoleteWorkersApi::class, kotlin.native.runtime.NativeRuntimeApi::class)
package io.github.heapy.icu4k.impl.locale

import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.test.*

class LocaleObjectCacheNativeTest {
    private class Box(val key: String)
    private class Key
    private fun transientEntry(storage: LocaleCacheStorage<Key, Box>): Pair<WeakReference<Key>, WeakReference<Box>> {
        val key = Key()
        val value = Box("transient")
        storage.put(key, value)
        return WeakReference(key) to WeakReference(value)
    }
    @Test fun reclaimedValuesAndStaleKeysAreReleasedByNativeStorage() {
        val storage = NativeLocaleCacheStorage<Key, Box>(16)
        val (key, value) = transientEntry(storage)
        GC.collect()
        assertNull(value.get())
        storage.cleanStaleEntries()
        GC.collect()
        assertNull(key.get())
    }
    private class Cache : LocaleObjectCache<String, Box>() { override fun createObject(key: String) = Box(key) }
    @Test fun sharedNativeStorageSurvivesConcurrentReadsAndWrites() {
        val cache = Cache()
        val workers = List(4) { Worker.start() }
        try {
            val tasks = workers.mapIndexed { index, worker ->
                worker.execute(TransferMode.SAFE, { cache to index }) { (shared, index) ->
                    List(300) { assertNotNull(shared.get("$index/$it")) }
                }
            }
            val results = tasks.map { it.result }
            for ((index, values) in results.withIndex()) for ((offset, box) in values.withIndex()) {
                assertEquals("$index/$offset", box.key)
                assertSame(box, cache.get(box.key))
            }
        } finally { workers.forEach { it.requestTermination().result } }
    }
}
